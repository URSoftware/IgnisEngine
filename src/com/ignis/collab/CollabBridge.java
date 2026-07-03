package com.ignis.collab;

import com.ignis.core.Camera;
import com.ignis.core.EntityFactory;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import org.json.JSONArray;
import org.json.JSONObject;

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

    private final Game game;
    private long lastBroadcastNanos = 0L;
    // ~12 Hz e suficiente para movimento fluido e leve na rede/VPN.
    private static final long BROADCAST_INTERVAL_NS = 1_000_000_000L / 12;

    private CollabBridge(Game game) {
        this.game = game;
    }

    /** Liga o executor de comandos do host (chamado pelo editor). */
    public static void setCommandExecutor(CommandExecutor executor) {
        commandExecutor = executor;
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

    /** Chamado a cada frame pelo AnimationTimer do editor. So o host transmite. */
    public void onEditorFrame() {
        CollabSession s = CollabSession.get();
        if (s.getRole() != CollabSession.Role.HOST) return;
        long now = System.nanoTime();
        if (now - lastBroadcastNanos < BROADCAST_INTERVAL_NS) return;
        lastBroadcastNanos = now;
        try {
            s.sendEvent(CollabSession.CH_SCENE, buildSnapshot());
        } catch (Exception ignore) { /* nao derruba o render */ }
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
        if (!CollabSession.CH_SCENE.equals(channel) || payload == null) return;

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
            if (go == null) {
                go = EntityFactory.create(o.optString("t", "Square"));
                if (go == null) continue;
                go.setName(name);
                go.setGame(game);
                game.addEntity(go);
            }
            go.setX(o.optDouble("x", go.getX()));
            go.setY(o.optDouble("y", go.getY()));
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
            // Sprite: so troca se mudou (evita recarregar a imagem todo frame).
            String sp = o.has("sp") ? o.optString("sp", "") : null;
            if (sp != null && !sp.equals(go.getSpritePath())) {
                go.setSpritePath(sp);
            }
        }

        // Remove localmente os objetos que sumiram no host.
        for (Map.Entry<String, GameObject> e : existing.entrySet()) {
            if (!seen.contains(e.getKey())) game.removeEntity(e.getValue());
        }

        // Espelha a camera do host (mesmo enquadramento).
        Camera cam = game.getActiveCamera();
        if (cam != null && snap.has("camx")) {
            cam.setPosition(snap.optDouble("camx", cam.getX()), snap.optDouble("camy", cam.getY()));
            cam.setZoom(snap.optDouble("camz", cam.getZoom()));
        }
    }
}
