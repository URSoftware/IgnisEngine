package com.ignis.collab;

import com.ignis.core.AssetResolver;
import com.ignis.core.Camera;
import com.ignis.core.EntityFactory;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CollabBridge - Ligacao entre a {@link CollabSession} (transporte) e o {@link Game}
 * do editor, para a colaboracao em tempo real (Parte 2 do plano).
 *
 * <p>Modelo <b>host-autoritativo</b>: o host e a fonte da verdade. A cada frame do
 * editor (com limite de ~12 Hz) o host serializa um <i>snapshot</i> da cena
 * (transforms + propriedades visuais + camera + estado de Play) e o transmite pelo
 * canal {@code scene}. Os convidados aplicam o snapshot ao seu proprio Game — veem
 * os objetos se mexendo, sendo criados/removidos e o host jogando, em tempo real,
 * sem rodar a simulacao localmente (puro espelho).</p>
 *
 * <p>Edicoes vindas do convidado (comandos para o host) serao a Parte 2.2. Aqui o
 * foco e o espelhamento host&rarr;convidado, que e a parte mais visual.</p>
 */
public final class CollabBridge implements CollabSession.Listener {

    /**
     * Executor de comandos vindos de convidados, aplicado no host. Recebe o nome
     * da ferramenta e seus argumentos e devolve o resultado (texto). Setado pelo
     * editor com base no registry do MCP — assim o convidado edita a cena do host
     * reusando TODAS as ferramentas de cena, mantendo o host autoritativo.
     */
    public interface CommandExecutor {
        String execute(String tool, JSONObject args);
    }

    private static CollabBridge instance;
    private static CommandExecutor commandExecutor;

    // Editores de codigo abertos, por nome de script -> funcao que aplica o texto
    // recebido (chamada na FX thread). Registrados pelo FxCodeEditor.
    private static final Map<String, java.util.function.Consumer<String>> scriptEditors =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final Game game;
    private long lastBroadcastNanos = 0L;
    // ~12 Hz e suficiente para movimento fluido e leve na rede/VPN.
    private static final long BROADCAST_INTERVAL_NS = 1_000_000_000L / 12;
    private static final double SNAP_INTERVAL_NS = 1_000_000_000.0 / 12;

    // Estado de interpolacao no convidado: nome -> {prevX, prevY, targetX, targetY}
    // e a camera {prevX, prevY, targetX, targetY, prevZoom, targetZoom}.
    private final Map<String, double[]> objInterp = new HashMap<>();
    private double[] camInterp = null;
    private long lastSnapshotNanos = 0L;

    // Streaming de assets: caminhos ja pedidos ao host (evita pedir em duplicidade).
    private final Set<String> requestedAssets = java.util.Collections.synchronizedSet(new HashSet<>());
    // Limite de tamanho de asset transmitido (2 MB) para nao travar o canal.
    private static final long MAX_ASSET_BYTES = 2L * 1024 * 1024;

    private CollabBridge(Game game) {
        this.game = game;
    }

    /** Liga o executor de comandos do host (chamado pelo editor). */
    public static void setCommandExecutor(CommandExecutor executor) {
        commandExecutor = executor;
    }

    // ------------------------------------------------------------------
    // Sincronizacao de codigo (canal script) — broadcast com debounce
    // ------------------------------------------------------------------

    /** Um editor de codigo se registra para receber edicoes remotas do seu script. */
    public static void registerScriptEditor(String scriptName, java.util.function.Consumer<String> applyRemote) {
        if (scriptName != null && applyRemote != null) scriptEditors.put(scriptName, applyRemote);
    }

    public static void unregisterScriptEditor(String scriptName) {
        if (scriptName != null) scriptEditors.remove(scriptName);
    }

    /** Transmite o conteudo de um script editado localmente (se houver sessao). */
    public static void broadcastScript(String scriptName, String content) {
        if (!CollabSession.get().isActive() || scriptName == null) return;
        CollabSession.get().sendEvent(CollabSession.CH_SCRIPT,
                new JSONObject().put("n", scriptName).put("c", content == null ? "" : content));
    }

    /** Instala (ou reinstala) o bridge para o Game informado. */
    public static void init(Game game) {
        if (instance != null) CollabSession.get().removeListener(instance);
        instance = new CollabBridge(game);
        CollabSession.get().addListener(instance);
    }

    public static CollabBridge get() { return instance; }

    // ------------------------------------------------------------------
    // Host: transmite o snapshot da cena
    // ------------------------------------------------------------------

    /**
     * Chamado a cada frame pelo AnimationTimer do editor. No host, transmite o
     * snapshot (throttle); no convidado, interpola as posicoes em direcao ao ultimo
     * snapshot (movimento fluido mesmo recebendo a ~12 Hz).
     */
    public void onEditorFrame() {
        CollabSession.Role role = CollabSession.get().getRole();
        if (role == CollabSession.Role.HOST) {
            long now = System.nanoTime();
            if (now - lastBroadcastNanos < BROADCAST_INTERVAL_NS) return;
            lastBroadcastNanos = now;
            try {
                CollabSession.get().sendEvent(CollabSession.CH_SCENE, buildSnapshot());
            } catch (Exception ignore) { /* nao derruba o render */ }
        } else if (role == CollabSession.Role.GUEST) {
            interpolateGuest();
        }
    }

    // Interpolacao no convidado: move cada objeto (e a camera) de prev->target
    // ao longo do intervalo entre snapshots, deixando o mirror fluido.
    private void interpolateGuest() {
        if (lastSnapshotNanos == 0L) return;
        double alpha = (System.nanoTime() - lastSnapshotNanos) / SNAP_INTERVAL_NS;
        if (alpha < 0) alpha = 0;
        if (alpha > 1) alpha = 1;
        // Indexa por nome uma vez por frame.
        Map<String, GameObject> byName = new HashMap<>();
        for (GameObject go : game.getEntities()) {
            if (!(go instanceof Camera)) byName.put(go.getName(), go);
        }
        for (Map.Entry<String, double[]> e : objInterp.entrySet()) {
            GameObject go = byName.get(e.getKey());
            if (go == null) continue;
            double[] t = e.getValue(); // {prevX, prevY, targetX, targetY}
            go.setX(t[0] + (t[2] - t[0]) * alpha);
            go.setY(t[1] + (t[3] - t[1]) * alpha);
        }
        if (camInterp != null) {
            Camera cam = game.getActiveCamera();
            if (cam != null) {
                cam.setPosition(camInterp[0] + (camInterp[2] - camInterp[0]) * alpha,
                                camInterp[1] + (camInterp[3] - camInterp[1]) * alpha);
                cam.setZoom(camInterp[4] + (camInterp[5] - camInterp[4]) * alpha);
            }
        }
    }

    private JSONObject buildSnapshot() {
        JSONObject snap = new JSONObject();
        JSONArray arr = new JSONArray();
        for (GameObject go : game.getEntities()) {
            if (go instanceof Camera) continue;
            JSONObject o = new JSONObject();
            o.put("n", go.getName());
            o.put("t", go.getType());
            o.put("x", go.getX());
            o.put("y", go.getY());
            o.put("w", go.getWidth());
            o.put("h", go.getHeight());
            o.put("r", go.getRotation());
            o.put("z", go.getZIndex());
            o.put("v", go.isVisible());
            o.put("op", go.getOpacity());
            o.put("fx", go.isFlipX());
            o.put("fy", go.isFlipY());
            o.put("sx", go.getScaleX());
            o.put("sy", go.getScaleY());
            if (go.getSpritePath() != null) o.put("sp", go.getSpritePath());
            arr.put(o);
        }
        snap.put("objs", arr);
        Camera cam = game.getActiveCamera();
        if (cam != null) {
            snap.put("camx", cam.getX());
            snap.put("camy", cam.getY());
            snap.put("camz", cam.getZoom());
        }
        snap.put("playing", game.getGameState() == Game.GameState.PLAYING);
        return snap;
    }

    // ------------------------------------------------------------------
    // Convidado: aplica o snapshot recebido
    // ------------------------------------------------------------------

    /**
     * Encaminha um comando de edicao do convidado para o host (uma chamada de
     * ferramenta MCP a ser executada no host). Chamado por {@code IgnisToolRegistry}
     * quando o editor esta como convidado numa sessao.
     */
    public static void sendCommand(String tool, JSONObject args) {
        JSONObject cmd = new JSONObject()
                .put("cmd", tool)
                .put("args", args == null ? new JSONObject() : args);
        CollabSession.get().sendEvent(CollabSession.CH_SCENE, cmd);
    }

    @Override
    public void onEvent(String channel, String from, JSONObject payload) {
        if (payload == null) return;

        // Canal de codigo: salva o script no disco local e atualiza o editor aberto.
        if (CollabSession.CH_SCRIPT.equals(channel)) {
            final String scriptName = payload.optString("n", "");
            final String content = payload.optString("c", "");
            if (scriptName.isEmpty()) return;
            javafx.application.Platform.runLater(() -> {
                try {
                    if (game.getScriptManager() != null) {
                        game.getScriptManager().saveScriptContent(scriptName, content);
                    }
                    java.util.function.Consumer<String> apply = scriptEditors.get(scriptName);
                    if (apply != null) apply.accept(content);
                } catch (Exception ignore) { /* edicao remota nao derruba o editor */ }
            });
            return;
        }

        if (!CollabSession.CH_SCENE.equals(channel)) return;

        // Streaming de assets: convidado pede um arquivo que nao tem; host responde.
        if (payload.has("assetReq")) {
            if (CollabSession.get().getRole() == CollabSession.Role.HOST) {
                sendAssetData(payload.optString("assetReq", ""));
            }
            return;
        }
        if (payload.has("assetData")) {
            if (CollabSession.get().getRole() == CollabSession.Role.GUEST) {
                receiveAssetData(payload.optString("assetData", ""), payload.optString("b64", ""));
            }
            return;
        }

        // Comando de edicao vindo de um convidado -> executado NO HOST (autoritativo).
        if (payload.has("cmd")) {
            if (CollabSession.get().getRole() != CollabSession.Role.HOST) return;
            final String tool = payload.optString("cmd", "");
            final JSONObject args = payload.optJSONObject("args");
            javafx.application.Platform.runLater(() -> {
                try {
                    if (commandExecutor != null && !tool.isEmpty()) {
                        String res = commandExecutor.execute(tool, args == null ? new JSONObject() : args);
                        System.out.println("[Collab] comando de " + from + ": " + tool + " -> " + res);
                    }
                } catch (Exception ignore) { /* comando invalido nao derruba o host */ }
            });
            return;
        }

        // Snapshot da cena vindo do host -> aplicado no convidado (espelho).
        if (CollabSession.get().getRole() != CollabSession.Role.GUEST) return;
        javafx.application.Platform.runLater(() -> {
            try {
                applySnapshot(payload);
            } catch (Exception ignore) { /* snapshot malformado nao derruba o editor */ }
        });
    }

    private void applySnapshot(JSONObject snap) {
        JSONArray arr = snap.optJSONArray("objs");
        if (arr == null) return;

        // Indexa os objetos locais por nome (ignora cameras).
        Map<String, GameObject> existing = new HashMap<>();
        for (GameObject go : game.getEntities()) {
            if (!(go instanceof Camera)) existing.put(go.getName(), go);
        }
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("n", "");
            if (name.isEmpty()) continue;
            seen.add(name);
            GameObject go = existing.get(name);
            boolean isNew = false;
            if (go == null) {
                go = EntityFactory.create(o.optString("t", "Square"));
                if (go == null) continue;
                go.setName(name);
                go.setGame(game);
                game.addEntity(go);
                isNew = true;
            }
            // Propriedades nao-posicionais: aplicadas imediatamente.
            go.setWidth(o.optInt("w", go.getWidth()));
            go.setHeight(o.optInt("h", go.getHeight()));
            go.setRotation(o.optDouble("r", 0));
            go.setZIndex(o.optInt("z", 0));
            go.setVisible(o.optBoolean("v", true));
            go.setOpacity(o.optDouble("op", 1.0));
            go.setFlipX(o.optBoolean("fx", false));
            go.setFlipY(o.optBoolean("fy", false));
            go.setScaleX(o.optDouble("sx", 1.0));
            go.setScaleY(o.optDouble("sy", 1.0));
            String sp = o.has("sp") ? o.optString("sp", "") : null;
            if (sp != null && !sp.equals(go.getSpritePath())) {
                go.setSpritePath(sp); // so troca se mudou (evita recarregar a imagem)
            }
            // Se o convidado nao tem o sprite localmente, pede ao host (uma vez).
            if (sp != null && !sp.isEmpty()) maybeRequestAsset(sp);
            // Posicao: guarda alvo (prev = pos atual visivel) para interpolar por frame.
            double targetX = o.optDouble("x", go.getX());
            double targetY = o.optDouble("y", go.getY());
            double prevX = isNew ? targetX : go.getX();
            double prevY = isNew ? targetY : go.getY();
            objInterp.put(name, new double[] { prevX, prevY, targetX, targetY });
            if (isNew) { go.setX(targetX); go.setY(targetY); } // aparece ja no lugar
        }

        // Remove localmente os objetos que sumiram no host.
        for (Map.Entry<String, GameObject> e : existing.entrySet()) {
            if (!seen.contains(e.getKey())) {
                game.removeEntity(e.getValue());
                objInterp.remove(e.getKey());
            }
        }

        // Camera: guarda alvo para interpolar (mesmo enquadramento do host, suave).
        Camera cam = game.getActiveCamera();
        if (cam != null && snap.has("camx")) {
            camInterp = new double[] {
                    cam.getX(), cam.getY(),
                    snap.optDouble("camx", cam.getX()), snap.optDouble("camy", cam.getY()),
                    cam.getZoom(), snap.optDouble("camz", cam.getZoom())
            };
        }

        lastSnapshotNanos = System.nanoTime();
    }

    // ------------------------------------------------------------------
    // Streaming de assets (sprites que o convidado nao possui)
    // ------------------------------------------------------------------

    private void maybeRequestAsset(String path) {
        if (path == null || path.isEmpty() || requestedAssets.contains(path)) return;
        File f = AssetResolver.resolve(path);
        if (f != null && f.isFile()) return; // ja existe localmente
        requestedAssets.add(path);
        CollabSession.get().sendEvent(CollabSession.CH_SCENE, new JSONObject().put("assetReq", path));
    }

    // Host: le o arquivo pedido e transmite em base64 (limitado por tamanho).
    private void sendAssetData(String path) {
        try {
            if (path == null || path.isEmpty()) return;
            File f = AssetResolver.resolve(path);
            if (f == null || !f.isFile() || f.length() > MAX_ASSET_BYTES) return;
            byte[] bytes = Files.readAllBytes(f.toPath());
            String b64 = Base64.getEncoder().encodeToString(bytes);
            CollabSession.get().sendEvent(CollabSession.CH_SCENE,
                    new JSONObject().put("assetData", path).put("b64", b64));
        } catch (Exception ignore) { /* asset opcional */ }
    }

    // Convidado: grava o arquivo recebido no projeto local e limpa o cache de imagem.
    private void receiveAssetData(String path, String b64) {
        try {
            if (path == null || path.isEmpty() || b64 == null || b64.isEmpty()) return;
            File base = AssetResolver.getProjectFolder();
            if (base == null) return;
            File dest = new File(base, path);
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(dest.toPath(), Base64.getDecoder().decode(b64));
            requestedAssets.remove(path);
            AssetResolver.clearImageCache(); // forca recarregar o sprite recem-recebido
            System.out.println("[Collab] asset recebido do host: " + path);
        } catch (Exception ignore) { /* asset opcional */ }
    }
}
