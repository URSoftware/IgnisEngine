package com.ignis.collab;

import com.ignis.core.IgnisLogger;

import com.ignis.core.AssetResolver;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * CollabProjectSync - Sincronizacao de arquivos do projeto numa sessao
 * colaborativa (canal {@link CollabSession#CH_PROJECT}).
 *
 * <p><b>Sincronizacao inicial:</b> ao conectar, o convidado pede o manifesto do
 * projeto ao host ({@code req:"manifest"}). O host salva o projeto (hook do
 * editor), varre a pasta do projeto e responde com a lista de arquivos
 * (caminho relativo + tamanho + SHA-256), direcionada so a quem pediu. O
 * convidado compara com seu cache local, pede apenas os arquivos que faltam ou
 * mudaram ({@code req:"files"}), recebe-os em chunks base64, valida o hash de
 * cada um e, ao completar, abre o projeto sincronizado no editor.</p>
 *
 * <p><b>Diretorio temporario:</b> os arquivos recebidos vivem em
 * {@code ~/.ignis/collab-cache/<host>_<porta>/<Projeto>/} — nunca sobrescrevem
 * projetos locais, ficam isolados por sessao (host+porta+projeto) e servem de
 * cache: ao reentrar na mesma sessao, so o delta e transferido. Caches sem uso
 * ha mais de {@link #CACHE_MAX_AGE_DAYS} dias sao removidos ao iniciar uma nova
 * sessao.</p>
 *
 * <p><b>Sincronizacao continua:</b> enquanto hospeda, um {@link WatchService}
 * observa a pasta {@code project/} do host (assets, scripts, prefabs, data...)
 * com debounce; arquivos criados/alterados sao retransmitidos a todos os
 * convidados e exclusoes propagadas ({@code del}). A cena em si e sincronizada
 * ao vivo pelo snapshot do {@link CollabBridge} — o watcher cobre o resto.</p>
 */
public final class CollabProjectSync implements CollabSession.Listener {

    // ------------------------------------------------------------------
    // Constantes de protocolo/limites
    // ------------------------------------------------------------------

    /** Tamanho de cada chunk de arquivo (bytes brutos; ~256 KB em base64). */
    private static final int CHUNK_BYTES = 192 * 1024;
    /** Limite por arquivo transferido (protege a sessao de arquivos gigantes). */
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;
    /** Idade maxima de um cache de sessao sem uso antes da limpeza automatica. */
    private static final int CACHE_MAX_AGE_DAYS = 7;
    /** Debounce do watcher: espera o arquivo "assentar" antes de transmitir. */
    private static final long WATCH_DEBOUNCE_MS = 400;

    private static final CollabProjectSync INSTANCE = new CollabProjectSync();

    // ------------------------------------------------------------------
    // Hooks do editor (registrados por IgnisEditorApp)
    // ------------------------------------------------------------------

    /** Abre no editor o .ignis sincronizado (chamado na FX thread). */
    private static volatile Consumer<File> projectOpener;
    /** Host: salva o projeto antes de montar o manifesto (chamado na FX thread). */
    private static volatile Runnable preSyncHook;
    /** Convidado: avisa o editor que arquivos do projeto mudaram (FX thread). */
    private static volatile Consumer<List<String>> filesChangedCallback;

    public static void setProjectOpener(Consumer<File> opener) { projectOpener = opener; }
    public static void setPreSyncHook(Runnable hook) { preSyncHook = hook; }
    public static void setFilesChangedCallback(Consumer<List<String>> cb) { filesChangedCallback = cb; }

    /** Instala o listener na sessao (idempotente; chamado pelo editor no boot). */
    public static void install() {
        CollabSession.get().removeListener(INSTANCE);
        CollabSession.get().addListener(INSTANCE);
    }

    public static CollabProjectSync get() { return INSTANCE; }

    /**
     * True quando o convidado ja recebeu e abriu a copia do projeto da sessao.
     * Enquanto false, o {@link CollabBridge} nao aplica snapshots nem streaming
     * de assets — evita criar objetos (e gravar arquivos do host) no projeto
     * LOCAL que o convidado tinha aberto antes de entrar. No host e sempre true.
     */
    public boolean isReady() {
        return CollabSession.get().getRole() != CollabSession.Role.GUEST || initialSyncDone;
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    // Transferencias e hashing fora das threads de rede/FX, em ordem (1 thread).
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Collab-ProjectSync");
        t.setDaemon(true);
        return t;
    });

    // --- Convidado: sincronizacao inicial ---
    private volatile JSONObject manifest;          // manifesto recebido do host
    private volatile File sessionDir;              // raiz temporaria da sessao
    private final Set<String> pendingFiles = ConcurrentHashMap.newKeySet();
    private volatile int totalToFetch = 0;
    private volatile boolean initialSyncDone = false;
    // Chunks em montagem: caminho -> buffer parcial
    private final Map<String, ChunkAssembly> assemblies = new ConcurrentHashMap<>();

    // --- Host: watcher de mudancas continuas ---
    private volatile WatchService watcher;
    private volatile Thread watcherThread;
    private final Map<String, Long> watchDebounce = new ConcurrentHashMap<>();

    // Guarda de sessao: onStatus dispara para QUALQUER mensagem de status (inclusive
    // as de progresso desta classe); a sincronizacao so deve iniciar uma vez por
    // sessao, senao cada status reiniciaria o processo (loop).
    private volatile boolean sessionStarted = false;

    private static final class ChunkAssembly {
        final byte[][] parts;
        int received = 0;
        ChunkAssembly(int n) { parts = new byte[n][]; }
    }

    private CollabProjectSync() {}

    // ------------------------------------------------------------------
    // Ciclo de vida da sessao
    // ------------------------------------------------------------------

    @Override
    public void onStatus(String message, boolean connected) {
        if (!connected) {
            sessionStarted = false;
            stopWatcher();
            assemblies.clear();
            pendingFiles.clear();
            return;
        }
        if (sessionStarted) return; // status subsequente da MESMA sessao (progresso etc.)
        CollabSession.Role role = CollabSession.get().getRole();
        if (role == CollabSession.Role.GUEST) {
            // Nova sessao como convidado: zera estado e pede o manifesto.
            sessionStarted = true;
            manifest = null;
            sessionDir = null;
            pendingFiles.clear();
            assemblies.clear();
            initialSyncDone = false;
            io.submit(CollabProjectSync::cleanupStaleCaches);
            CollabSession.get().sendEvent(CollabSession.CH_PROJECT,
                    new JSONObject().put("req", "manifest").put("uid", CollabSession.get().getLocalUid()));
            fireGuestStatus("Solicitando projeto ao host...");
        } else if (role == CollabSession.Role.HOST) {
            sessionStarted = true;
            io.submit(CollabProjectSync::cleanupStaleCaches);
            startWatcher();
        }
    }

    @Override
    public void onEvent(String channel, String from, JSONObject payload) {
        if (!CollabSession.CH_PROJECT.equals(channel) || payload == null) return;
        CollabSession.Role role = CollabSession.get().getRole();

        if (role == CollabSession.Role.HOST) {
            String req = payload.optString("req", "");
            if ("manifest".equals(req)) {
                String toUid = payload.optString("uid", "");
                hostSendManifest(toUid);
            } else if ("files".equals(req)) {
                String toUid = payload.optString("uid", "");
                JSONArray paths = payload.optJSONArray("paths");
                if (paths != null) hostSendFiles(paths, toUid);
            }
            return;
        }

        if (role != CollabSession.Role.GUEST) return;

        if (payload.has("manifest")) {
            guestReceiveManifest(payload.getJSONObject("manifest"));
        } else if (payload.has("f")) {
            guestReceiveChunk(payload);
        } else if (payload.has("del")) {
            guestReceiveDelete(payload.optString("del", ""));
        }
    }

    // ------------------------------------------------------------------
    // HOST: manifesto e envio de arquivos
    // ------------------------------------------------------------------

    // Salva o projeto no editor (FX thread) e monta/envia o manifesto (thread IO).
    private void hostSendManifest(String toUid) {
        Runnable buildAndSend = () -> io.submit(() -> {
            try {
                File projectFolder = AssetResolver.getProjectFolder();
                if (projectFolder == null || !projectFolder.isDirectory()) {
                    CollabSession.get().sendEventTo(CollabSession.CH_PROJECT,
                            new JSONObject().put("manifest", new JSONObject().put("err",
                                    "O host nao tem um projeto aberto.")), toUid);
                    return;
                }
                File mainFolder = projectFolder.getParentFile();
                JSONObject man = buildManifest(mainFolder);
                CollabSession.get().sendEventTo(CollabSession.CH_PROJECT,
                        new JSONObject().put("manifest", man), toUid);
                IgnisLogger.info("[Collab] manifesto enviado (" +
                        man.getJSONArray("files").length() + " arquivos) para " + toUid);
            } catch (Exception e) {
                IgnisLogger.error("[Collab] falha ao montar manifesto: " + e.getMessage());
            }
        });
        // O manifesto deve refletir o estado atual da cena: salvar antes.
        Runnable hook = preSyncHook;
        if (hook != null) {
            javafx.application.Platform.runLater(() -> {
                try { hook.run(); } catch (Exception ignore) { /* salvar e melhor-esforco */ }
                buildAndSend.run();
            });
        } else {
            buildAndSend.run();
        }
    }

    /**
     * Manifesto do projeto: nome, versao do motor, caminho do .ignis e a lista
     * de arquivos {p (relativo a pasta principal), s (bytes), h (sha-256)}.
     */
    private static JSONObject buildManifest(File mainFolder) throws Exception {
        JSONObject man = new JSONObject();
        man.put("name", mainFolder.getName());
        man.put("engine", com.ignis.core.Project.ENGINE_VERSION);

        JSONArray files = new JSONArray();
        String ignisRel = null;
        List<File> all = new ArrayList<>();
        collectFiles(mainFolder, mainFolder, all);
        for (File f : all) {
            String rel = relativize(mainFolder, f);
            if (rel == null || skipInSync(rel)) continue;
            if (f.length() > MAX_FILE_BYTES) {
                IgnisLogger.error("[Collab] arquivo acima do limite, fora da sincronizacao: " + rel);
                continue;
            }
            if (rel.endsWith(".ignis") && !rel.contains("/")) ignisRel = rel;
            JSONObject e = new JSONObject();
            e.put("p", rel);
            e.put("s", f.length());
            e.put("h", sha256(f));
            files.put(e);
        }
        man.put("files", files);
        if (ignisRel != null) man.put("ignis", ignisRel);
        return man;
    }

    // Envia os arquivos pedidos, em chunks, apenas ao convidado que pediu.
    private void hostSendFiles(JSONArray paths, String toUid) {
        io.submit(() -> {
            File projectFolder = AssetResolver.getProjectFolder();
            if (projectFolder == null) return;
            File mainFolder = projectFolder.getParentFile();
            for (int i = 0; i < paths.length(); i++) {
                String rel = paths.optString(i, "");
                if (!isSafeRelPath(rel) || skipInSync(rel)) continue;
                sendFileTo(mainFolder, rel, toUid);
            }
        });
    }

    // Le um arquivo e o transmite em chunks {f, i, n, b64} (+h no primeiro chunk).
    private static void sendFileTo(File mainFolder, String rel, String toUid) {
        try {
            File f = new File(mainFolder, rel);
            if (!f.isFile() || f.length() > MAX_FILE_BYTES) return;
            byte[] bytes = Files.readAllBytes(f.toPath());
            String hash = sha256Bytes(bytes);
            int n = Math.max(1, (bytes.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
            for (int i = 0; i < n; i++) {
                int off = i * CHUNK_BYTES;
                int len = Math.min(CHUNK_BYTES, bytes.length - off);
                byte[] part = new byte[len];
                System.arraycopy(bytes, off, part, 0, len);
                JSONObject msg = new JSONObject()
                        .put("f", rel)
                        .put("i", i)
                        .put("n", n)
                        .put("b64", Base64.getEncoder().encodeToString(part));
                if (i == 0) msg.put("h", hash);
                CollabSession.get().sendEventTo(CollabSession.CH_PROJECT, msg, toUid);
            }
        } catch (Exception e) {
            IgnisLogger.error("[Collab] falha ao enviar arquivo '" + rel + "': " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // CONVIDADO: manifesto, chunks e aplicacao
    // ------------------------------------------------------------------

    private void guestReceiveManifest(JSONObject man) {
        io.submit(() -> {
            try {
                if (man.has("err")) {
                    fireGuestStatus("Projeto indisponivel: " + man.optString("err"));
                    return;
                }
                String engine = man.optString("engine", "?");
                if (!engine.equals(com.ignis.core.Project.ENGINE_VERSION)) {
                    fireGuestStatus("Aviso: versao do motor do host (" + engine
                            + ") difere da local (" + com.ignis.core.Project.ENGINE_VERSION
                            + "). A sessao segue, mas podem haver incompatibilidades.");
                }
                this.manifest = man;
                this.sessionDir = resolveSessionDir(man.optString("name", "Projeto"));
                sessionDir.mkdirs();

                // Diff manifesto x cache local: pede so o que falta ou mudou.
                JSONArray files = man.getJSONArray("files");
                List<String> missing = new ArrayList<>();
                for (int i = 0; i < files.length(); i++) {
                    JSONObject e = files.getJSONObject(i);
                    String rel = e.optString("p", "");
                    if (!isSafeRelPath(rel)) continue;
                    File local = new File(sessionDir, rel);
                    if (!local.isFile() || local.length() != e.optLong("s", -1)
                            || !sha256(local).equals(e.optString("h", ""))) {
                        missing.add(rel);
                    }
                }
                // Isolamento: remove do cache o que nao existe mais no host.
                removeExtraneousFiles(sessionDir, files);

                if (missing.isEmpty()) {
                    finishInitialSync();
                    return;
                }
                pendingFiles.clear();
                pendingFiles.addAll(missing);
                totalToFetch = missing.size();
                fireGuestStatus("Sincronizando projeto: 0/" + totalToFetch + " arquivos...");
                JSONArray req = new JSONArray();
                for (String p : missing) req.put(p);
                CollabSession.get().sendEvent(CollabSession.CH_PROJECT,
                        new JSONObject().put("req", "files").put("paths", req)
                                .put("uid", CollabSession.get().getLocalUid()));
            } catch (Exception e) {
                fireGuestStatus("Falha na sincronizacao do projeto: " + e.getMessage());
            }
        });
    }

    private void guestReceiveChunk(JSONObject payload) {
        io.submit(() -> {
            try {
                String rel = payload.optString("f", "");
                if (!isSafeRelPath(rel)) return;
                int i = payload.optInt("i", 0);
                int n = Math.max(1, payload.optInt("n", 1));
                byte[] part = Base64.getDecoder().decode(payload.optString("b64", ""));

                ChunkAssembly asm = assemblies.computeIfAbsent(rel, k -> new ChunkAssembly(n));
                if (i < 0 || i >= asm.parts.length) return;
                if (asm.parts[i] == null) asm.received++;
                asm.parts[i] = part;
                if (asm.received < asm.parts.length) return;

                // Arquivo completo: monta, valida contra o manifesto e grava.
                assemblies.remove(rel);
                int total = 0;
                for (byte[] p : asm.parts) total += p.length;
                byte[] bytes = new byte[total];
                int off = 0;
                for (byte[] p : asm.parts) {
                    System.arraycopy(p, 0, bytes, off, p.length);
                    off += p.length;
                }
                String expected = expectedHash(rel);
                if (expected != null && !expected.isEmpty()
                        && !sha256Bytes(bytes).equals(expected) && !initialSyncDone) {
                    IgnisLogger.error("[Collab] hash divergente em '" + rel + "', arquivo descartado.");
                    pendingFiles.remove(rel);
                    return;
                }
                File base = sessionBase();
                if (base == null) return;
                File dest = new File(base, rel);
                File parent = dest.getParentFile();
                if (parent != null) parent.mkdirs();
                Files.write(dest.toPath(), bytes);

                if (!initialSyncDone) {
                    pendingFiles.remove(rel);
                    int done = totalToFetch - pendingFiles.size();
                    fireGuestStatus("Sincronizando projeto: " + done + "/" + totalToFetch + " arquivos...");
                    if (pendingFiles.isEmpty()) finishInitialSync();
                } else {
                    // Mudanca continua vinda do watcher do host.
                    AssetResolver.clearImageCache();
                    notifyFilesChanged(List.of(rel));
                    IgnisLogger.info("[Collab] arquivo atualizado pelo host: " + rel);
                }
            } catch (Exception e) {
                IgnisLogger.error("[Collab] falha ao receber chunk: " + e.getMessage());
            }
        });
    }

    private void guestReceiveDelete(String rel) {
        if (!isSafeRelPath(rel)) return;
        io.submit(() -> {
            File base = sessionBase();
            if (base == null) return;
            File f = new File(base, rel);
            if (f.isFile() && f.delete()) {
                AssetResolver.clearImageCache();
                notifyFilesChanged(List.of(rel));
                IgnisLogger.info("[Collab] arquivo removido pelo host: " + rel);
            }
        });
    }

    // Sincronizacao inicial completa: abre o projeto temporario no editor.
    private void finishInitialSync() {
        initialSyncDone = true;
        JSONObject man = manifest;
        File dir = sessionDir;
        if (man == null || dir == null) return;
        String ignisRel = man.optString("ignis", "");
        if (ignisRel.isEmpty()) {
            fireGuestStatus("Projeto sincronizado, mas sem arquivo .ignis no manifesto.");
            return;
        }
        File ignisFile = new File(dir, ignisRel);
        fireGuestStatus("Projeto sincronizado: " + man.optString("name", "?")
                + " (" + man.getJSONArray("files").length() + " arquivos). Abrindo...");
        Consumer<File> opener = projectOpener;
        if (opener != null && ignisFile.isFile()) {
            javafx.application.Platform.runLater(() -> {
                try { opener.accept(ignisFile); } catch (Exception e) {
                    fireGuestStatus("Falha ao abrir projeto sincronizado: " + e.getMessage());
                }
            });
        }
    }

    // Base de escrita do convidado: o diretorio da sessao (temp/cache). Depois que
    // o projeto abre, AssetResolver aponta para <sessionDir>/project — os caminhos
    // do manifesto sao relativos a pasta principal, entao gravamos sempre nela.
    private File sessionBase() {
        return sessionDir;
    }

    private String expectedHash(String rel) {
        JSONObject man = manifest;
        if (man == null) return null;
        JSONArray files = man.optJSONArray("files");
        if (files == null) return null;
        for (int i = 0; i < files.length(); i++) {
            JSONObject e = files.optJSONObject(i);
            if (e != null && rel.equals(e.optString("p"))) return e.optString("h", "");
        }
        return null;
    }

    // ------------------------------------------------------------------
    // HOST: watcher de mudancas continuas na pasta do projeto
    // ------------------------------------------------------------------

    private void startWatcher() {
        stopWatcher();
        File projectFolder = AssetResolver.getProjectFolder();
        if (projectFolder == null || !projectFolder.isDirectory()) return;
        try {
            WatchService ws = projectFolder.toPath().getFileSystem().newWatchService();
            registerRecursive(projectFolder.toPath(), ws);
            this.watcher = ws;
            Thread t = new Thread(() -> watchLoop(ws, projectFolder), "Collab-Watcher");
            t.setDaemon(true);
            t.start();
            this.watcherThread = t;
            IgnisLogger.info("[Collab] observando alteracoes em " + projectFolder);
        } catch (Exception e) {
            IgnisLogger.error("[Collab] watcher indisponivel: " + e.getMessage());
        }
    }

    private void stopWatcher() {
        WatchService ws = watcher;
        watcher = null;
        if (ws != null) {
            try { ws.close(); } catch (Exception ignore) {}
        }
        Thread t = watcherThread;
        watcherThread = null;
        if (t != null) t.interrupt();
        watchDebounce.clear();
    }

    private static void registerRecursive(Path root, WatchService ws) throws Exception {
        Files.walk(root)
                .filter(Files::isDirectory)
                .filter(p -> !p.toString().replace('\\', '/').contains("/scripts/compiled"))
                .forEach(p -> {
                    try {
                        p.register(ws, StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE);
                    } catch (Exception ignore) { /* subpasta opcional */ }
                });
    }

    private void watchLoop(WatchService ws, File projectFolder) {
        File mainFolder = projectFolder.getParentFile();
        while (watcher == ws) {
            try {
                WatchKey key = ws.poll(WATCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();
                if (key != null) {
                    Path dir = (Path) key.watchable();
                    for (WatchEvent<?> ev : key.pollEvents()) {
                        if (!(ev.context() instanceof Path)) continue;
                        Path child = dir.resolve((Path) ev.context());
                        String rel = relativize(mainFolder, child.toFile());
                        if (rel == null || skipInSync(rel)) continue;
                        if (Files.isDirectory(child)) {
                            // Nova subpasta: passa a observa-la tambem.
                            if (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                                try { registerRecursive(child, ws); } catch (Exception ignore) {}
                            }
                            continue;
                        }
                        if (ev.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            watchDebounce.remove(rel);
                            CollabSession.get().sendEvent(CollabSession.CH_PROJECT,
                                    new JSONObject().put("del", rel));
                        } else {
                            watchDebounce.put(rel, now); // debounce: envia no proximo ciclo quieto
                        }
                    }
                    key.reset();
                }
                // Envia arquivos "assentados" (sem novos eventos ha WATCH_DEBOUNCE_MS).
                List<String> ready = new ArrayList<>();
                for (Map.Entry<String, Long> e : watchDebounce.entrySet()) {
                    if (now - e.getValue() >= WATCH_DEBOUNCE_MS) ready.add(e.getKey());
                }
                for (String rel : ready) {
                    watchDebounce.remove(rel);
                    File f = new File(mainFolder, rel);
                    if (f.isFile()) {
                        io.submit(() -> sendFileTo(mainFolder, rel, null)); // broadcast a todos
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            } catch (Exception e) {
                IgnisLogger.error("[Collab] watcher: " + e.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------
    // Cache de sessao (diretorio temporario do convidado)
    // ------------------------------------------------------------------

    /** Raiz de todos os caches de sessao: {@code ~/.ignis/collab-cache}. */
    public static File cacheRoot() {
        return new File(new File(System.getProperty("user.home", "."), ".ignis"), "collab-cache");
    }

    // Pasta desta sessao: isolada por host_porta e nome do projeto.
    private static File resolveSessionDir(String projectName) {
        String remote = CollabSession.get().getRemoteAddress();
        String key = sanitize(remote == null ? "sessao" : remote);
        return new File(new File(cacheRoot(), key), sanitize(projectName));
    }

    /** Remove caches de sessao sem uso ha mais de {@link #CACHE_MAX_AGE_DAYS} dias. */
    static void cleanupStaleCaches() {
        try {
            File root = cacheRoot();
            File[] hosts = root.listFiles();
            if (hosts == null) return;
            long cutoff = System.currentTimeMillis() - CACHE_MAX_AGE_DAYS * 24L * 60 * 60 * 1000;
            for (File hostDir : hosts) {
                if (lastModifiedRecursive(hostDir) < cutoff) {
                    deleteRecursive(hostDir);
                    IgnisLogger.info("[Collab] cache de sessao antigo removido: " + hostDir.getName());
                }
            }
        } catch (Exception ignore) { /* limpeza e melhor-esforco */ }
    }

    /** Apaga todo o cache de colaboracao (acao manual nas Configuracoes). */
    public static boolean clearAllCaches() {
        return deleteRecursive(cacheRoot());
    }

    private static long lastModifiedRecursive(File f) {
        long last = f.lastModified();
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) last = Math.max(last, lastModifiedRecursive(k));
        }
        return last;
    }

    private static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) deleteRecursive(k);
        }
        return f.delete();
    }

    // Remove do cache local arquivos que nao constam no manifesto (isolamento:
    // o convidado ve exatamente o projeto do host, sem sobras de sessoes antigas).
    private static void removeExtraneousFiles(File sessionDir, JSONArray files) {
        Set<String> keep = new HashSet<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject e = files.optJSONObject(i);
            if (e != null) keep.add(e.optString("p", ""));
        }
        List<File> all = new ArrayList<>();
        collectFiles(sessionDir, sessionDir, all);
        for (File f : all) {
            String rel = relativize(sessionDir, f);
            // Compilados de scripts sao gerados localmente; nao remover.
            if (rel != null && !keep.contains(rel) && !skipInSync(rel)) {
                f.delete();
            }
        }
    }

    // ------------------------------------------------------------------
    // Utilitarios
    // ------------------------------------------------------------------

    private static void collectFiles(File root, File dir, List<File> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) collectFiles(root, f, out);
            else out.add(f);
        }
    }

    // Arquivos fora da sincronizacao: compilados (regenerados no convidado),
    // temporarios e metadados de SO.
    private static boolean skipInSync(String rel) {
        String r = rel.replace('\\', '/');
        return r.contains("scripts/compiled/")
                || r.endsWith(".class")
                || r.endsWith(".tmp")
                || r.endsWith("~")
                || r.endsWith("Thumbs.db")
                || r.endsWith(".DS_Store");
    }

    // Caminho relativo seguro: sem absolutos e sem escapar da pasta base.
    private static boolean isSafeRelPath(String rel) {
        if (rel == null || rel.isEmpty()) return false;
        String r = rel.replace('\\', '/');
        if (r.startsWith("/") || r.contains("..") || r.contains(":")) return false;
        return true;
    }

    private static String relativize(File base, File f) {
        try {
            Path b = base.getCanonicalFile().toPath();
            Path t = f.getCanonicalFile().toPath();
            if (!t.startsWith(b)) return null;
            return b.relativize(t).toString().replace(File.separatorChar, '/');
        } catch (Exception e) {
            return null;
        }
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sha256(File f) throws Exception {
        return sha256Bytes(Files.readAllBytes(f.toPath()));
    }

    private static String sha256Bytes(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] d = md.digest(bytes);
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void fireGuestStatus(String msg) {
        IgnisLogger.info("[Collab] " + msg);
        CollabSession.get().fireStatus(msg, CollabSession.get().isActive());
    }

    private void notifyFilesChanged(List<String> rels) {
        Consumer<List<String>> cb = filesChangedCallback;
        if (cb != null) {
            javafx.application.Platform.runLater(() -> {
                try { cb.accept(rels); } catch (Exception ignore) { /* UI e melhor-esforco */ }
            });
        }
    }
}
