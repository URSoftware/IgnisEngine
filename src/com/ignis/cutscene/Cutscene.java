package com.ignis.cutscene;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cutscene orientada a tracks e keyframes (roadmap P1 — "timeline de cutscene").
 *
 * <p>Modelo de dados puro e deterministico, medido em FRAMES da simulacao fixa
 * (60 passos/s): a mesma cutscene reproduz identica no preview do editor, no MCP
 * ({@code run_cutscene}) e no jogo. Segue o principio do roadmap: primeiro a
 * operacao segura e testavel por API/MCP; a timeline visual sera construida por
 * cima DESTE mesmo modelo.</p>
 *
 * <p>Tracks espaciais (ACTOR, CAMERA) interpolam x/y entre keyframes com easing;
 * tracks de evento (DIALOG, AUDIO, SIGNAL, FLAG) disparam exatamente no frame do
 * keyframe. O easing pertence ao keyframe de SAIDA (a curva do segmento ate o
 * proximo keyframe), com STEP segurando o valor ate o proximo.</p>
 */
public final class Cutscene {

    public enum TrackType { ACTOR, CAMERA, DIALOG, AUDIO, SIGNAL, FLAG }

    public enum Easing {
        LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, STEP;

        /** Aplica a curva ao progresso t em [0,1]. */
        double apply(double t) {
            return switch (this) {
                case LINEAR -> t;
                case EASE_IN -> t * t;
                case EASE_OUT -> 1 - (1 - t) * (1 - t);
                case EASE_IN_OUT -> t * t * (3 - 2 * t);
                case STEP -> 0; // segura o keyframe de saida ate o proximo
            };
        }
    }

    /** Um ponto na timeline de uma track. Campos nulos = "nao anima essa propriedade". */
    public static final class Keyframe {
        public final int frame;
        public final Easing easing;
        public final Double x;
        public final Double y;
        public final Boolean visible;
        /** Texto de dialogo (tracks DIALOG). */
        public final String text;
        /** Carga generica: caminho de audio, nome de sinal ou "flag=valor". */
        public final String data;

        public Keyframe(int frame, Easing easing, Double x, Double y, Boolean visible,
                String text, String data) {
            this.frame = frame;
            this.easing = easing != null ? easing : Easing.LINEAR;
            this.x = x;
            this.y = y;
            this.visible = visible;
            this.text = text;
            this.data = data;
        }

        JSONObject toJSON() {
            JSONObject o = new JSONObject();
            o.put("frame", frame);
            o.put("easing", easing.name());
            if (x != null) o.put("x", x.doubleValue());
            if (y != null) o.put("y", y.doubleValue());
            if (visible != null) o.put("visible", visible.booleanValue());
            if (text != null) o.put("text", text);
            if (data != null) o.put("data", data);
            return o;
        }

        static Keyframe fromJSON(JSONObject o) {
            Easing e;
            try {
                e = Easing.valueOf(o.optString("easing", "LINEAR").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException iae) {
                e = Easing.LINEAR;
            }
            return new Keyframe(
                    Math.max(0, o.optInt("frame", 0)), e,
                    o.has("x") ? o.optDouble("x") : null,
                    o.has("y") ? o.optDouble("y") : null,
                    o.has("visible") ? o.optBoolean("visible") : null,
                    o.has("text") ? o.optString("text") : null,
                    o.has("data") ? o.optString("data") : null);
        }
    }

    /** Uma track anima UM alvo (ator/camera por nome) ou dispara eventos de um tipo. */
    public static final class Track {
        public final TrackType type;
        /** Nome do ator/camera, canal de audio, nome do sinal/flag — conforme o tipo. */
        public final String target;
        private final List<Keyframe> keyframes = new ArrayList<>();

        public Track(TrackType type, String target) {
            this.type = type;
            this.target = target != null ? target : "";
        }

        /** Insere mantendo a ordem por frame (substitui keyframe existente no mesmo frame). */
        public void addKeyframe(Keyframe kf) {
            keyframes.removeIf(k -> k.frame == kf.frame);
            int i = 0;
            while (i < keyframes.size() && keyframes.get(i).frame < kf.frame) i++;
            keyframes.add(i, kf);
        }

        public boolean removeKeyframe(int frame) {
            return keyframes.removeIf(k -> k.frame == frame);
        }

        public List<Keyframe> getKeyframes() {
            return new ArrayList<>(keyframes);
        }

        JSONObject toJSON() {
            JSONObject o = new JSONObject();
            o.put("type", type.name());
            o.put("target", target);
            JSONArray arr = new JSONArray();
            for (Keyframe kf : keyframes) arr.put(kf.toJSON());
            o.put("keyframes", arr);
            return o;
        }

        static Track fromJSON(JSONObject o) {
            TrackType t;
            try {
                t = TrackType.valueOf(o.optString("type", "ACTOR").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException iae) {
                t = TrackType.ACTOR;
            }
            Track track = new Track(t, o.optString("target", ""));
            JSONArray arr = o.optJSONArray("keyframes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject kf = arr.optJSONObject(i);
                    if (kf != null) track.addKeyframe(Keyframe.fromJSON(kf));
                }
            }
            return track;
        }
    }

    /** Estado espacial interpolado de uma track num frame. Campos nulos = sem keyframe. */
    public static final class Pose {
        public final Double x;
        public final Double y;
        public final Boolean visible;

        Pose(Double x, Double y, Boolean visible) {
            this.x = x;
            this.y = y;
            this.visible = visible;
        }
    }

    private final String name;
    private int durationFrames;
    private final List<Track> tracks = new ArrayList<>();

    public Cutscene(String name, int durationFrames) {
        this.name = name;
        this.durationFrames = Math.max(1, durationFrames);
    }

    public String getName() { return name; }

    public int getDurationFrames() { return durationFrames; }

    public void setDurationFrames(int durationFrames) {
        this.durationFrames = Math.max(1, durationFrames);
    }

    public List<Track> getTracks() { return new ArrayList<>(tracks); }

    public void addTrack(Track track) { tracks.add(track); }

    /** Track do tipo+alvo dados, ou null. Tipos de evento sem alvo casam por tipo. */
    public Track findTrack(TrackType type, String target) {
        for (Track t : tracks) {
            if (t.type == type && t.target.equals(target != null ? target : "")) return t;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Amostragem deterministica
    // ------------------------------------------------------------------

    /**
     * Interpola o estado espacial da track no frame dado. Antes do primeiro keyframe
     * vale o primeiro; depois do ultimo vale o ultimo (clamp) — a cutscene nunca
     * extrapola para fora do que foi autorado.
     */
    public static Pose samplePose(Track track, int frame) {
        List<Keyframe> kfs = track.getKeyframes();
        if (kfs.isEmpty()) return new Pose(null, null, null);
        Keyframe before = null, after = null;
        for (Keyframe kf : kfs) {
            if (kf.frame <= frame) before = kf;
            else { after = kf; break; }
        }
        if (before == null) before = kfs.get(0);           // antes do primeiro: clamp
        if (after == null || before.frame == after.frame) {
            return new Pose(before.x, before.y, before.visible);
        }
        double t = (frame - before.frame) / (double) (after.frame - before.frame);
        double eased = before.easing.apply(Math.max(0, Math.min(1, t)));
        Double x = lerp(before.x, after.x, eased);
        Double y = lerp(before.y, after.y, eased);
        // Visibilidade nao interpola: segura a do keyframe de saida (semantica STEP).
        return new Pose(x, y, before.visible);
    }

    private static Double lerp(Double a, Double b, double t) {
        if (a == null) return b;
        if (b == null) return a;
        return a + (b - a) * t;
    }

    /** Keyframes de EVENTO (dialogo/audio/sinal/flag) que disparam exatamente neste frame. */
    public List<Keyframe> eventsAt(Track track, int frame) {
        List<Keyframe> fired = new ArrayList<>();
        if (track.type == TrackType.ACTOR || track.type == TrackType.CAMERA) return fired;
        for (Keyframe kf : track.getKeyframes()) {
            if (kf.frame == frame) fired.add(kf);
        }
        return fired;
    }

    // ------------------------------------------------------------------
    // Validacao (roadmap P1: "validacao de ator/asset ausente")
    // ------------------------------------------------------------------

    /**
     * Valida a cutscene. {@code actorNames} nulo pula a checagem de atores (sem cena
     * viva); {@code assetExists} nulo pula a checagem de arquivos de audio.
     */
    public List<String> validate(Set<String> actorNames,
            java.util.function.Predicate<String> assetExists) {
        List<String> issues = new ArrayList<>();
        if (tracks.isEmpty()) issues.add("Cutscene sem tracks.");
        Set<String> seenTracks = new HashSet<>();
        for (Track track : tracks) {
            String label = track.type + (track.target.isEmpty() ? "" : ":" + track.target);
            if (!seenTracks.add(track.type + "|" + track.target)) {
                issues.add("Track duplicada: " + label + ".");
            }
            if (track.getKeyframes().isEmpty()) {
                issues.add("Track " + label + " sem keyframes.");
            }
            if ((track.type == TrackType.ACTOR || track.type == TrackType.CAMERA)
                    && track.target.isEmpty()) {
                issues.add("Track " + track.type + " sem alvo (target).");
            }
            if (track.type == TrackType.ACTOR && actorNames != null
                    && !track.target.isEmpty() && !actorNames.contains(track.target)) {
                issues.add("Ator ausente: track ACTOR mira '" + track.target
                        + "' que nao existe na cena.");
            }
            for (Keyframe kf : track.getKeyframes()) {
                if (kf.frame > durationFrames) {
                    issues.add("Keyframe alem da duracao: " + label + " frame " + kf.frame
                            + " > " + durationFrames + ".");
                }
                if (track.type == TrackType.DIALOG && (kf.text == null || kf.text.isBlank())) {
                    issues.add("Dialogo sem texto: " + label + " frame " + kf.frame + ".");
                }
                if ((track.type == TrackType.SIGNAL || track.type == TrackType.FLAG)
                        && (kf.data == null || kf.data.isBlank())) {
                    issues.add("Evento sem 'data': " + label + " frame " + kf.frame
                            + " (nome do sinal ou flag=valor).");
                }
                if (track.type == TrackType.AUDIO) {
                    if (kf.data == null || kf.data.isBlank()) {
                        issues.add("Audio sem caminho: " + label + " frame " + kf.frame + ".");
                    } else if (assetExists != null && !assetExists.test(kf.data)) {
                        issues.add("Asset de audio ausente: " + label + " frame " + kf.frame
                                + " aponta para " + kf.data + ".");
                    }
                }
            }
        }
        return issues;
    }

    // ------------------------------------------------------------------
    // Serializacao JSON (formato consumido pela futura timeline visual)
    // ------------------------------------------------------------------

    public JSONObject toJSON() {
        JSONObject o = new JSONObject();
        o.put("name", name);
        o.put("durationFrames", durationFrames);
        o.put("fps", 60); // informativo: a simulacao da engine e fixa em 60 passos/s
        JSONArray arr = new JSONArray();
        for (Track t : tracks) arr.put(t.toJSON());
        o.put("tracks", arr);
        return o;
    }

    public static Cutscene fromJSON(JSONObject o) {
        Cutscene cs = new Cutscene(o.optString("name", "cutscene"),
                o.optInt("durationFrames", 60));
        JSONArray arr = o.optJSONArray("tracks");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject t = arr.optJSONObject(i);
                if (t != null) cs.addTrack(Track.fromJSON(t));
            }
        }
        return cs;
    }
}
