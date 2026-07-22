package com.ignis.cutscene;

import com.ignis.core.Camera;
import com.ignis.core.Game;
import com.ignis.core.GameObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Aplica uma {@link Cutscene} ao mundo vivo, frame a frame — o MESMO codigo do
 * preview do editor, do MCP ({@code run_cutscene}) e do runtime, garantindo que a
 * conclusao natural e o skip cheguem ao mesmo estado final (roadmap P1).
 *
 * <p>Tracks ACTOR posicionam objetos pelo nome; CAMERA move a camera ativa (ou a
 * nomeada no target). Tracks de evento (DIALOG/AUDIO/SIGNAL/FLAG) sao devolvidas
 * como texto para o chamador decidir (o script/agent conecta ao sistema de
 * dialogo/sinais do jogo) — o player nao acopla a engine a nenhum sistema de
 * narrativa especifico.</p>
 */
public final class CutscenePlayer {

    private CutscenePlayer() { }

    /**
     * Aplica o estado da cutscene no frame dado e devolve os eventos disparados
     * exatamente nesse frame (formatados "TIPO[:alvo] frame=N: carga").
     */
    public static List<String> applyFrame(Game game, Cutscene cs, int frame) {
        List<String> events = new ArrayList<>();
        for (Cutscene.Track track : cs.getTracks()) {
            switch (track.type) {
                case ACTOR -> {
                    GameObject go = findByName(game, track.target);
                    if (go == null) break; // validate_cutscene ja aponta ator ausente
                    Cutscene.Pose pose = Cutscene.samplePose(track, frame);
                    if (pose.x != null) go.setX(pose.x);
                    if (pose.y != null) go.setY(pose.y);
                    if (pose.visible != null) go.setVisible(pose.visible);
                }
                case CAMERA -> {
                    Camera cam = resolveCamera(game, track.target);
                    if (cam == null) break;
                    Cutscene.Pose pose = Cutscene.samplePose(track, frame);
                    double cx = pose.x != null ? pose.x : cam.getCameraTransform().getX();
                    double cy = pose.y != null ? pose.y : cam.getCameraTransform().getY();
                    cam.setPosition(cx, cy);
                }
                default -> {
                    for (Cutscene.Keyframe kf : cs.eventsAt(track, frame)) {
                        String payload = kf.text != null ? kf.text : (kf.data != null ? kf.data : "");
                        events.add(track.type + (track.target.isEmpty() ? "" : ":" + track.target)
                                + " frame=" + frame + ": " + payload);
                    }
                }
            }
        }
        return events;
    }

    /**
     * "Skip": aplica direto o estado do ultimo frame e devolve TODOS os eventos de
     * {@code fromFrame} ate o fim, para o chamador processa-los de uma vez — mesmo
     * estado final da conclusao natural (roadmap P1).
     */
    public static List<String> skipToEnd(Game game, Cutscene cs, int fromFrame) {
        List<String> events = new ArrayList<>();
        int last = cs.getDurationFrames();
        for (Cutscene.Track track : cs.getTracks()) {
            switch (track.type) {
                case ACTOR -> {
                    GameObject go = findByName(game, track.target);
                    if (go == null) break;
                    Cutscene.Pose pose = Cutscene.samplePose(track, last);
                    if (pose.x != null) go.setX(pose.x);
                    if (pose.y != null) go.setY(pose.y);
                    if (pose.visible != null) go.setVisible(pose.visible);
                }
                case CAMERA -> {
                    Camera cam = resolveCamera(game, track.target);
                    if (cam == null) break;
                    Cutscene.Pose pose = Cutscene.samplePose(track, last);
                    double cx = pose.x != null ? pose.x : cam.getCameraTransform().getX();
                    double cy = pose.y != null ? pose.y : cam.getCameraTransform().getY();
                    cam.setPosition(cx, cy);
                }
                default -> {
                    for (Cutscene.Keyframe kf : track.getKeyframes()) {
                        if (kf.frame >= fromFrame && kf.frame <= last) {
                            String payload = kf.text != null ? kf.text : (kf.data != null ? kf.data : "");
                            events.add(track.type + (track.target.isEmpty() ? "" : ":" + track.target)
                                    + " frame=" + kf.frame + ": " + payload);
                        }
                    }
                }
            }
        }
        return events;
    }

    private static GameObject findByName(Game game, String name) {
        if (name == null || name.isEmpty()) return null;
        for (GameObject go : game.getEntities()) {
            if (name.equals(go.getName())) return go;
        }
        return null;
    }

    private static Camera resolveCamera(Game game, String target) {
        if (target != null && !target.isEmpty()) {
            for (Camera cam : game.getCameras()) {
                if (target.equals(cam.getCameraName()) || target.equals(cam.getName())) return cam;
            }
        }
        return game.getActiveCamera();
    }
}
