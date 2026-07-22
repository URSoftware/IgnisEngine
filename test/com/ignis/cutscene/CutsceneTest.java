package com.ignis.cutscene;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dominio puro da timeline de cutscene (P1): amostragem deterministica, easing,
 * eventos por frame, validacao e round-trip JSON — tudo sem editor nem JavaFX.
 */
class CutsceneTest {

    private static Cutscene.Keyframe kf(int frame, Cutscene.Easing easing, Double x, Double y) {
        return new Cutscene.Keyframe(frame, easing, x, y, null, null, null);
    }

    @Test
    void interpolacaoLinearNoMeioDoSegmento() {
        Cutscene.Track track = new Cutscene.Track(Cutscene.TrackType.ACTOR, "Hero");
        track.addKeyframe(kf(0, Cutscene.Easing.LINEAR, 0.0, 100.0));
        track.addKeyframe(kf(60, Cutscene.Easing.LINEAR, 120.0, 100.0));

        Cutscene.Pose pose = Cutscene.samplePose(track, 30);
        assertEquals(60.0, pose.x, 0.001);
        assertEquals(100.0, pose.y, 0.001);
    }

    @Test
    void clampAntesDoPrimeiroEDepoisDoUltimoKeyframe() {
        Cutscene.Track track = new Cutscene.Track(Cutscene.TrackType.ACTOR, "Hero");
        track.addKeyframe(kf(10, Cutscene.Easing.LINEAR, 5.0, 5.0));
        track.addKeyframe(kf(20, Cutscene.Easing.LINEAR, 15.0, 15.0));

        assertEquals(5.0, Cutscene.samplePose(track, 0).x, 0.001, "antes do primeiro: clamp");
        assertEquals(15.0, Cutscene.samplePose(track, 99).x, 0.001, "depois do ultimo: clamp");
    }

    @Test
    void stepSeguraOValorAteOProximoKeyframe() {
        Cutscene.Track track = new Cutscene.Track(Cutscene.TrackType.ACTOR, "Hero");
        track.addKeyframe(kf(0, Cutscene.Easing.STEP, 0.0, 0.0));
        track.addKeyframe(kf(10, Cutscene.Easing.LINEAR, 100.0, 0.0));

        assertEquals(0.0, Cutscene.samplePose(track, 9).x, 0.001, "STEP segura ate o proximo");
        assertEquals(100.0, Cutscene.samplePose(track, 10).x, 0.001, "no keyframe seguinte troca");
    }

    @Test
    void easingRespeitaOsExtremosDoSegmento() {
        for (Cutscene.Easing easing : Cutscene.Easing.values()) {
            Cutscene.Track track = new Cutscene.Track(Cutscene.TrackType.ACTOR, "Hero");
            track.addKeyframe(kf(0, easing, 0.0, 0.0));
            track.addKeyframe(kf(10, Cutscene.Easing.LINEAR, 10.0, 0.0));
            assertEquals(0.0, Cutscene.samplePose(track, 0).x, 0.001, easing + " em t=0");
            assertEquals(10.0, Cutscene.samplePose(track, 10).x, 0.001, easing + " em t=1");
        }
    }

    @Test
    void keyframeNoMesmoFrameSubstituiOAnterior() {
        Cutscene.Track track = new Cutscene.Track(Cutscene.TrackType.ACTOR, "Hero");
        track.addKeyframe(kf(5, Cutscene.Easing.LINEAR, 1.0, 1.0));
        track.addKeyframe(kf(5, Cutscene.Easing.LINEAR, 9.0, 9.0));
        assertEquals(1, track.getKeyframes().size());
        assertEquals(9.0, track.getKeyframes().get(0).x, 0.001);
    }

    @Test
    void eventosDisparamExatamenteNoFrame() {
        Cutscene cs = new Cutscene("intro", 100);
        Cutscene.Track dialog = new Cutscene.Track(Cutscene.TrackType.DIALOG, "Rimuru");
        dialog.addKeyframe(new Cutscene.Keyframe(30, null, null, null, null, "Ola!", null));
        cs.addTrack(dialog);

        assertTrue(cs.eventsAt(dialog, 29).isEmpty());
        assertEquals(1, cs.eventsAt(dialog, 30).size());
        assertTrue(cs.eventsAt(dialog, 31).isEmpty());
    }

    @Test
    void validacaoApontaAtorAusenteEProblemasDeTrack() {
        Cutscene cs = new Cutscene("intro", 60);
        Cutscene.Track actor = new Cutscene.Track(Cutscene.TrackType.ACTOR, "Fantasma");
        actor.addKeyframe(kf(90, Cutscene.Easing.LINEAR, 0.0, 0.0)); // alem da duracao
        cs.addTrack(actor);
        Cutscene.Track dialog = new Cutscene.Track(Cutscene.TrackType.DIALOG, "");
        dialog.addKeyframe(new Cutscene.Keyframe(10, null, null, null, null, null, null)); // sem texto
        cs.addTrack(dialog);
        cs.addTrack(new Cutscene.Track(Cutscene.TrackType.AUDIO, "musica")); // sem keyframes

        List<String> issues = cs.validate(Set.of("Hero"), rel -> false);
        assertTrue(issues.stream().anyMatch(i -> i.contains("Ator ausente")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("alem da duracao")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("Dialogo sem texto")), issues.toString());
        assertTrue(issues.stream().anyMatch(i -> i.contains("sem keyframes")), issues.toString());
    }

    @Test
    void jsonRoundTripPreservaTimeline() {
        Cutscene cs = new Cutscene("intro", 240);
        Cutscene.Track actor = new Cutscene.Track(Cutscene.TrackType.ACTOR, "Hero");
        actor.addKeyframe(new Cutscene.Keyframe(0, Cutscene.Easing.EASE_IN, 10.0, 20.0, true, null, null));
        actor.addKeyframe(new Cutscene.Keyframe(120, Cutscene.Easing.STEP, 300.0, 20.0, null, null, null));
        cs.addTrack(actor);
        Cutscene.Track signal = new Cutscene.Track(Cutscene.TrackType.SIGNAL, "");
        signal.addKeyframe(new Cutscene.Keyframe(60, null, null, null, null, null, "veldora_desperta"));
        cs.addTrack(signal);

        Cutscene back = Cutscene.fromJSON(new JSONObject(cs.toJSON().toString()));
        assertEquals("intro", back.getName());
        assertEquals(240, back.getDurationFrames());
        assertEquals(2, back.getTracks().size());

        Cutscene.Track actorBack = back.findTrack(Cutscene.TrackType.ACTOR, "Hero");
        assertEquals(2, actorBack.getKeyframes().size());
        assertEquals(Cutscene.Easing.EASE_IN, actorBack.getKeyframes().get(0).easing);
        assertEquals(true, actorBack.getKeyframes().get(0).visible);
        assertNull(actorBack.getKeyframes().get(1).visible);
        assertEquals(300.0, actorBack.getKeyframes().get(1).x, 0.001);

        Cutscene.Track signalBack = back.findTrack(Cutscene.TrackType.SIGNAL, "");
        assertEquals("veldora_desperta", signalBack.getKeyframes().get(0).data);
    }
}
