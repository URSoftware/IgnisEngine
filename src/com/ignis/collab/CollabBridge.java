package com.ignis.collab;

import com.ignis.core.IgnisLogger;

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

    // ------------------------------------------------------------------
    // Ponteiro virtual dos participantes (canal cursor)
    // ------------------------------------------------------------------

    /** Estado de um cursor remoto (posicao em coordenadas de MUNDO + atividade). */
    public static final class RemoteCursor {
        public volatile double prevX, prevY, targetX, targetY;
        public volatile long lastMoveNanos;
        public volatile String selection = "";  // objeto selecionado pelo participante
        public volatile String tool = "";       // ferramenta ativa (Mover/Rotacionar/...)
        public final java.awt.Color color;
        RemoteCursor(java.awt.Color color) { this.color = color; }
    }

    // Cursores dos demais participantes, por nome de exibicao.
    private final Map<String, RemoteCursor> remoteCursors = new java.util.concurrent.ConcurrentHashMap<>();
    // Throttle de envio do cursor local (~20 Hz e suficiente para fluidez).
    private static final long CURSOR_INTERVAL_NS = 1_000_000_000L / 20;
    private static long lastCursorSentNanos = 0L;
    private static double lastCursorX, lastCursorY;
    // Cursor remoto some depois de inatividade (participante fora da Scene View).
    private static final long CURSOR_TTL_NANOS = 5_000_000_000L;

    // Paleta de cores exclusivas por participante (indexada por hash do nome).
    private static final java.awt.Color[] CURSOR_PALETTE = {
            new java.awt.Color(0x4FC3F7), new java.awt.Color(0xFF8A65),
            new java.awt.Color(0x81C784), new java.awt.Color(0xBA68C8),
            new java.awt.Color(0xFFD54F), new java.awt.Color(0xF06292),
            new java.awt.Color(0x4DB6AC), new java.awt.Color(0xA1887F),
    };

    private static java.awt.Color colorFor(String name) {
        int h = name == null ? 0 : name.hashCode();
        return CURSOR_PALETTE[Math.abs(h) % CURSOR_PALETTE.length];
    }

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
    // Ponteiro virtual: envio (local) e render (remotos)
    // ------------------------------------------------------------------

    /**
     * Transmite a posicao do cursor local na Scene View (coordenadas de MUNDO),
     * junto da selecao atual e da ferramenta ativa. Chamado pelo editor no mouse
     * move/drag; throttle interno de ~20 Hz.
     */
    public static void broadcastCursor(double worldX, double worldY, String selectionName, String tool) {
        if (!CollabSession.get().isActive()) return;
        long now = System.nanoTime();
        if (now - lastCursorSentNanos < CURSOR_INTERVAL_NS) return;
        lastCursorSentNanos = now;
        lastCursorX = worldX;
        lastCursorY = worldY;
        JSONObject p = new JSONObject()
                .put("x", worldX).put("y", worldY)
                .put("sel", selectionName == null ? "" : selectionName)
                .put("tool", tool == null ? "" : tool);
        CollabSession.get().sendEvent(CollabSession.CH_CURSOR, p);
    }

    /**
     * Transmite imediatamente uma mudanca de atividade (selecao/ferramenta) na
     * ultima posicao conhecida do cursor — os demais veem o que o participante
     * esta manipulando mesmo sem o mouse se mover.
     */
    public static void broadcastActivity(String selectionName, String tool) {
        if (!CollabSession.get().isActive()) return;
        lastCursorSentNanos = 0L; // fura o throttle
        broadcastCursor(lastCursorX, lastCursorY, selectionName, tool);
    }

    /**
     * Desenha os cursores remotos (e a selecao de cada participante) por cima do
     * frame ja renderizado da Scene View. Chamado pelo editor a cada frame do
     * AnimationTimer, na FX thread.
     */
    public void renderOverlay(javafx.scene.canvas.GraphicsContext gc, int width, int height) {
        if (remoteCursors.isEmpty()) return;
        long now = System.nanoTime();
        for (Map.Entry<String, RemoteCursor> e : remoteCursors.entrySet()) {
            RemoteCursor rc = e.getValue();
            if (now - rc.lastMoveNanos > CURSOR_TTL_NANOS) continue; // inativo: oculta

            // Interpola prev->target no intervalo de envio (~50 ms) para suavidade.
            double alpha = Math.min(1.0, (now - rc.lastMoveNanos) / (double) CURSOR_INTERVAL_NS);
            double wx = rc.prevX + (rc.targetX - rc.prevX) * alpha;
            double wy = rc.prevY + (rc.targetY - rc.prevY) * alpha;
            java.awt.geom.Point2D.Double s = game.worldToScreen(wx, wy);
            if (s == null) continue;

            javafx.scene.paint.Color fx = javafx.scene.paint.Color.rgb(
                    rc.color.getRed(), rc.color.getGreen(), rc.color.getBlue());

            // Contorno do objeto que o participante esta manipulando.
            String sel = rc.selection;
            if (sel != null && !sel.isEmpty()) {
                for (GameObject go : game.getEntities()) {
                    if (go instanceof Camera || !sel.equals(go.getName())) continue;
                    java.awt.geom.Point2D.Double a = game.worldToScreen(go.getX(), go.getY());
                    java.awt.geom.Point2D.Double b = game.worldToScreen(
                            go.getX() + go.getWidth(), go.getY() + go.getHeight());
                    if (a == null || b == null) break;
                    gc.setStroke(fx);
                    gc.setLineWidth(2);
                    gc.setLineDashes(6, 4);
                    gc.strokeRect(Math.min(a.x, b.x) - 3, Math.min(a.y, b.y) - 3,
                            Math.abs(b.x - a.x) + 6, Math.abs(b.y - a.y) + 6);
                    gc.setLineDashes((double[]) null);
                    break;
                }
            }

            // Fora da viewport: nao desenha o ponteiro (mas a selecao acima vale).
            if (s.x < -40 || s.y < -40 || s.x > width + 40 || s.y > height + 40) continue;

            // Ponteiro (seta) na cor do participante.
            gc.setFill(fx);
            gc.setStroke(javafx.scene.paint.Color.color(0, 0, 0, 0.55));
            gc.setLineWidth(1);
            double[] px = { s.x, s.x + 12, s.x + 5.2, s.x + 8.4, s.x + 6.2, s.x + 3.6, s.x };
            double[] py = { s.y, s.y + 4.2, s.y + 6.4, s.y + 13.6, s.y + 14.6, s.y + 8.4, s.y };
            gc.fillPolygon(px, py, px.length);
            gc.strokePolygon(px, py, px.length);

            // Etiqueta: nome (+ ferramenta ativa, se houver).
            String label = e.getKey() + (rc.tool != null && !rc.tool.isEmpty() ? " · " + rc.tool : "");
            gc.setFont(javafx.scene.text.Font.font(11));
            double tw = label.length() * 6.2 + 10;
            gc.setFill(javafx.scene.paint.Color.color(
                    rc.color.getRed() / 255.0, rc.color.getGreen() / 255.0,
                    rc.color.getBlue() / 255.0, 0.92));
            gc.fillRoundRect(s.x + 12, s.y + 14, tw, 17, 8, 8);
            gc.setFill(javafx.scene.paint.Color.color(0, 0, 0, 0.85));
            gc.fillText(label, s.x + 17, s.y + 26);
        }
    }

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

            // Componentes: scripts anexados + propriedades do SpriteComponent +
            // collider — o convidado espelha o Inspector, nao so o transform.
            org.json.JSONArray sn = new org.json.JSONArray();
            for (String s : go.getScriptNames()) {
                if (!"SpriteComponent".equals(s)) sn.put(s);
            }
            o.put("sn", sn);
            com.ignis.core.SpriteComponent sc = go.getComponent(com.ignis.core.SpriteComponent.class);
            if (sc != null) {
                if (sc.getTint() != null) o.put("tc", sc.getTint().getRGB());
                if (sc.getShapeType() != null) o.put("st", sc.getShapeType());
            }
            o.put("ct", go.getColliderType().name());
            o.put("cm", go.getCollisionMode().name());
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
    public void onStatus(String message, boolean connected) {
        if (!connected) {
            remoteCursors.clear();
            objInterp.clear();
            camInterp = null;
            lastSnapshotNanos = 0L;
            requestedAssets.clear();
        }
    }

    @Override
    public void onEvent(String channel, String from, JSONObject payload) {
        if (payload == null) return;

        // Canal de cursor: atualiza o ponteiro virtual do participante (qualquer papel).
        if (CollabSession.CH_CURSOR.equals(channel)) {
            RemoteCursor rc = remoteCursors.computeIfAbsent(from, n -> {
                RemoteCursor c = new RemoteCursor(colorFor(n));
                c.prevX = c.targetX = payload.optDouble("x", 0);
                c.prevY = c.targetY = payload.optDouble("y", 0);
                return c;
            });
            // prev = posicao interpolada atual (evita "pulo" quando chega novo alvo).
            long now = System.nanoTime();
            double alpha = Math.min(1.0, (now - rc.lastMoveNanos) / (double) CURSOR_INTERVAL_NS);
            rc.prevX = rc.prevX + (rc.targetX - rc.prevX) * alpha;
            rc.prevY = rc.prevY + (rc.targetY - rc.prevY) * alpha;
            rc.targetX = payload.optDouble("x", rc.targetX);
            rc.targetY = payload.optDouble("y", rc.targetY);
            rc.selection = payload.optString("sel", "");
            rc.tool = payload.optString("tool", "");
            rc.lastMoveNanos = now;
            return;
        }

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
                        IgnisLogger.info("[Collab] comando de " + from + ": " + tool + " -> " + res);
                    }
                } catch (Exception ignore) { /* comando invalido nao derruba o host */ }
            });
            return;
        }

        // Snapshot da cena vindo do host -> aplicado no convidado (espelho).
        if (CollabSession.get().getRole() != CollabSession.Role.GUEST) return;
        // Ate a copia do projeto da sessao abrir, nada e aplicado ao projeto local
        // do convidado (isolamento: nao cria objetos nem baixa assets nele).
        if (!CollabProjectSync.get().isReady()) return;
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

            // Componentes (Inspector): tint/forma do SpriteComponent e collider.
            com.ignis.core.SpriteComponent sc = go.getComponent(com.ignis.core.SpriteComponent.class);
            if (sc != null) {
                if (o.has("tc")) {
                    int rgb = o.optInt("tc");
                    if (sc.getTint() == null || sc.getTint().getRGB() != rgb) {
                        sc.setTint(new java.awt.Color(rgb, true));
                    }
                }
                String st = o.optString("st", null);
                if (st != null && !st.equals(sc.getShapeType())) sc.setShapeType(st);
            }
            String ct = o.optString("ct", null);
            if (ct != null && !ct.equals(go.getColliderType().name())) {
                try {
                    go.setColliderType(com.ignis.core.IgnisSampleCollisions.ColliderType.valueOf(ct));
                } catch (IllegalArgumentException ignore) { /* tipo desconhecido */ }
            }
            String cm = o.optString("cm", null);
            if (cm != null && !cm.equals(go.getCollisionMode().name())) {
                try {
                    go.setCollisionMode(com.ignis.core.IgnisSampleCollisions.CollisionMode.valueOf(cm));
                } catch (IllegalArgumentException ignore) { /* modo desconhecido */ }
            }

            // Scripts anexados: espelha a lista do host (anexa novos, remove os que
            // sairam). Instancias vem do ScriptManager do convidado, que compila os
            // .java recebidos na sincronizacao de projeto.
            JSONArray sn = o.optJSONArray("sn");
            if (sn != null) {
                syncScripts(go, sn);
            }
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

    // Espelha a lista de scripts do host num objeto do convidado: anexa os que
    // faltam (instanciando pelo ScriptManager local) e remove os que sairam.
    private void syncScripts(GameObject go, JSONArray hostScripts) {
        Set<String> wanted = new HashSet<>();
        for (int i = 0; i < hostScripts.length(); i++) {
            String s = hostScripts.optString(i, "");
            if (!s.isEmpty()) wanted.add(s);
        }
        // Remove scripts que o host nao tem mais (nunca o SpriteComponent).
        for (String name : new java.util.ArrayList<>(go.getScriptNames())) {
            if (!"SpriteComponent".equals(name) && !wanted.contains(name)) {
                go.removeScriptByName(name);
            }
        }
        // Anexa os que faltam (a compilacao pode ainda nao ter ocorrido; tenta de novo
        // no proximo snapshot ate o ScriptManager conseguir instanciar).
        com.ignis.core.ScriptManager sm = game.getScriptManager();
        for (String name : wanted) {
            if (go.getScriptNames().contains(name)) continue;
            try {
                if (sm != null) {
                    com.ignis.core.IgnisScript inst = sm.createScriptInstance(name, go, game);
                    if (inst != null) {
                        go.addComponent(inst);
                        continue;
                    }
                }
            } catch (Exception ignore) { /* tenta no proximo snapshot */ }
        }
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

    // Convidado: grava o arquivo recebido no projeto da sessao e limpa o cache de
    // imagem. So depois da copia da sessao aberta — nunca no projeto local.
    private void receiveAssetData(String path, String b64) {
        try {
            if (path == null || path.isEmpty() || b64 == null || b64.isEmpty()) return;
            if (!CollabProjectSync.get().isReady()) return;
            File base = AssetResolver.getProjectFolder();
            if (base == null) return;
            File dest = new File(base, path);
            File parent = dest.getParentFile();
            if (parent != null) parent.mkdirs();
            Files.write(dest.toPath(), Base64.getDecoder().decode(b64));
            requestedAssets.remove(path);
            AssetResolver.clearImageCache(); // forca recarregar o sprite recem-recebido
            IgnisLogger.info("[Collab] asset recebido do host: " + path);
        } catch (Exception ignore) { /* asset opcional */ }
    }
}
