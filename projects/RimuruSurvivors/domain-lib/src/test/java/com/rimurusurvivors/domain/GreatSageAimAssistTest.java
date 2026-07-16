package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Porta de fidelidade de GreatSageAimAssist (RimuruRuntimeRules.cs) — prioridade de
 * alvo (vida baixa > ameaca > distancia) e previsao de interceptacao.
 */
class GreatSageAimAssistTest {

    private static TargetSnapshot target(String id, double x, double y, float healthRatio, boolean isThreat, boolean isBoss) {
        return new TargetSnapshot(id, new Vec2(x, y), new Vec2(0, 0), healthRatio, isThreat, isBoss);
    }

    @Test
    void picksLowestHealthRatioFirst() {
        var weak = target("weak", 5, 0, 0.10f, false, false);
        var strong = target("strong", 5, 0, 0.90f, false, false);

        var chosen = GreatSageAimAssist.chooseTarget(List.of(strong, weak), new Vec2(0, 0), 100, false);
        assertEquals(weak, chosen);
    }

    @Test
    void sameHealthThreatBeatsNonThreatRegardlessOfDistance() {
        var farThreat = target("farThreat", 50, 0, 0.5f, true, false);
        var closeNonThreat = target("closeNonThreat", 5, 0, 0.5f, false, false);

        var chosen = GreatSageAimAssist.chooseTarget(List.of(farThreat, closeNonThreat), new Vec2(0, 0), 100, false);
        assertEquals(farThreat, chosen); // ameaca=true bate closeNonThreat mesmo estando mais longe
    }

    @Test
    void sameHealthAndThreatTieBrokenByDistance() {
        var farThreat = target("farThreat", 50, 0, 0.5f, true, false);
        var closeThreat = target("closeThreat", 10, 0, 0.5f, true, false);

        var chosen = GreatSageAimAssist.chooseTarget(List.of(farThreat, closeThreat), new Vec2(0, 0), 100, false);
        assertEquals(closeThreat, chosen); // entre dois ameacas empatados, o mais perto vence
    }

    @Test
    void excludesBossesByDefault() {
        var boss = target("boss", 1, 0, 0.01f, true, true);
        var regular = target("regular", 5, 0, 0.5f, false, false);

        var chosen = GreatSageAimAssist.chooseTarget(List.of(boss, regular), new Vec2(0, 0), 100, false);
        assertEquals(regular, chosen);

        var chosenWithBosses = GreatSageAimAssist.chooseTarget(List.of(boss, regular), new Vec2(0, 0), 100, true);
        assertEquals(boss, chosenWithBosses); // vida mais baixa e agora elegivel
    }

    @Test
    void excludesDeadAndOutOfRangeTargets() {
        var dead = target("dead", 1, 0, 0f, true, false);
        var outOfRange = target("outOfRange", 500, 0, 0.1f, true, false);

        var chosen = GreatSageAimAssist.chooseTarget(List.of(dead, outOfRange), new Vec2(0, 0), 50, false);
        assertNull(chosen);
    }

    @Test
    void predictInterceptLeadsMovingTarget() {
        var moving = target("moving", 10, 0, 0.5f, false, false);
        var withVelocity = new TargetSnapshot(
                moving.enemyFamilyId(), moving.position(), new Vec2(20, 0), moving.healthRatio(),
                moving.isThreat(), moving.isBoss());

        Vec2 intercept = GreatSageAimAssist.predictIntercept(withVelocity, new Vec2(0, 0), 100);
        // distancia 10, velocidade projetil 100 -> lead = 0.10 (dentro do cap de 0.45)
        assertEquals(10 + 20 * 0.10, intercept.x(), 1e-9);
        assertEquals(0.0, intercept.y(), 1e-9);
    }

    @Test
    void predictInterceptCapsLeadAt045Seconds() {
        var farSlowMoving = new TargetSnapshot("t", new Vec2(1000, 0), new Vec2(5, 0), 0.5f, false, false);
        Vec2 intercept = GreatSageAimAssist.predictIntercept(farSlowMoving, new Vec2(0, 0), 10);
        // distancia/velocidade = 100s, mas o lead e limitado a 0.45s
        assertEquals(1000 + 5 * 0.45, intercept.x(), 1e-9);
    }

    @Test
    void predictInterceptReturnsPositionWhenProjectileHasNoSpeed() {
        var target = target("t", 42, 7, 0.5f, false, false);
        Vec2 intercept = GreatSageAimAssist.predictIntercept(target, new Vec2(0, 0), 0);
        assertEquals(target.position(), intercept);
    }
}
