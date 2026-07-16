package com.rimurusurvivors.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Porta de fidelidade de RimuruRunState (RimuruRuntimeRules.cs) — as mesmas
 * transicoes de forma/evolucao/imunidade do mod original, testadas sem motor.
 */
class RimuruRunStateTest {

    @Test
    void startsAsSlime() {
        RimuruRunState state = new RimuruRunState();
        assertEquals(RimuruForm.SLIME, state.getForm());
        assertFalse(state.isDemonLord());
    }

    @Test
    void humanoidRequiresLevel20() {
        RimuruRunState state = new RimuruRunState();
        assertFalse(state.tryUnlockHumanoid(19));
        assertEquals(RimuruForm.SLIME, state.getForm());

        assertTrue(state.tryUnlockHumanoid(20));
        assertEquals(RimuruForm.HUMANOID, state.getForm());

        // ja e Humanoid: nao pode "desbloquear" de novo
        assertFalse(state.tryUnlockHumanoid(25));
    }

    @Test
    void demonLordRequiresWeaponPassiveAndTreasureAfterHumanoid() {
        RimuruRunState state = new RimuruRunState();

        // ainda Slime: falha mesmo com requisitos de arma/passiva satisfeitos
        assertFalse(state.tryEvolveDemonLord(8, 5, true));

        state.tryUnlockHumanoid(20);
        assertFalse(state.tryEvolveDemonLord(7, 5, true)); // arma abaixo do nivel 8
        assertFalse(state.tryEvolveDemonLord(8, 4, true)); // passiva abaixo do nivel 5
        assertFalse(state.tryEvolveDemonLord(8, 5, false)); // sem bau

        assertTrue(state.tryEvolveDemonLord(8, 5, true));
        assertTrue(state.isDemonLord());
    }

    @Test
    void cielRequiresDemonLordAndCombatAnalysis() {
        RimuruRunState state = new RimuruRunState();
        assertFalse(state.tryAwakenCielFromCombatAnalysis(55, 6)); // ainda Slime

        state.tryUnlockHumanoid(20);
        state.tryEvolveDemonLord(8, 5, true);

        assertFalse(state.tryAwakenCielFromCombatAnalysis(54, 6)); // nivel de personagem baixo
        assertFalse(state.tryAwakenCielFromCombatAnalysis(55, 5)); // beelzebuth abaixo do nivel 6
        assertTrue(state.tryAwakenCielFromCombatAnalysis(55, 6));
        assertTrue(state.isCiel());
    }

    @Test
    void azathothRequiresCielRangaAndTreasure() {
        RimuruRunState state = new RimuruRunState();
        state.tryUnlockHumanoid(20);
        state.tryEvolveDemonLord(8, 5, true);

        assertFalse(state.tryEvolveAzathoth(8, true)); // sem Ciel nem Ranga ainda

        state.tryAwakenCielFromCombatAnalysis(55, 6);
        assertFalse(state.tryEvolveAzathoth(8, true)); // ainda sem Ranga

        state.trySummonRanga(4);
        assertFalse(state.tryEvolveAzathoth(7, true)); // beelzebuth abaixo do nivel 8
        assertTrue(state.tryEvolveAzathoth(8, true));
        assertTrue(state.hasAzathoth());
    }

    @Test
    void rangaRequiresPredatorLevel4AndOnlySummonsOnce() {
        RimuruRunState state = new RimuruRunState();
        assertFalse(state.trySummonRanga(3));
        assertTrue(state.trySummonRanga(4));
        assertTrue(state.isRangaSummoned());
        assertFalse(state.trySummonRanga(5)); // ja invocado
    }

    @Test
    void analyzeRevivalGrantsImmunityAndIsCaseInsensitive() {
        RimuruRunState state = new RimuruRunState();

        var first = state.analyzeRevival("Red_Reaper", "reaper_slash");
        assertTrue(first.isNewEnemyAnalysis());
        assertTrue(first.immunityGranted());
        assertFalse(first.cielAwakened()); // ainda nao e Demon Lord

        // mesma familia, capitalizacao diferente: nao deve contar como nova analise
        var second = state.analyzeRevival("RED_REAPER", "reaper_slash_2");
        assertFalse(second.isNewEnemyAnalysis());

        assertTrue(state.isImmuneTo("red_reaper"));
        assertTrue(state.isImmuneTo("RED_REAPER")); // case-insensitive, como StringComparer.OrdinalIgnoreCase no C#
    }

    @Test
    void analyzeRevivalAsDemonLordAwakensCielAndCopiesAbility() {
        RimuruRunState state = new RimuruRunState();
        state.tryUnlockHumanoid(20);
        state.tryEvolveDemonLord(8, 5, true);

        var result = state.analyzeRevival("goblin_lord", "spear_thrust");
        assertTrue(result.cielAwakened());
        assertTrue(result.abilityCopied());
        assertTrue(state.isCiel());
        assertTrue(state.canCounterstrike("goblin_lord"));
        assertTrue(state.canCounterstrike("GOBLIN_LORD"));
    }

    @Test
    void analyzeRevivalRejectsBlankIds() {
        RimuruRunState state = new RimuruRunState();
        assertThrows(IllegalArgumentException.class, () -> state.analyzeRevival("", "x"));
        assertThrows(IllegalArgumentException.class, () -> state.analyzeRevival("family", " "));
    }

    @Test
    void reaperSeveranceAndExecuteRequireAzathothAndLowHealth() {
        RimuruRunState state = new RimuruRunState();
        assertFalse(state.canApplyReaperSeverance("red_reaper"));

        state.tryUnlockHumanoid(20);
        state.tryEvolveDemonLord(8, 5, true);
        state.tryAwakenCielFromCombatAnalysis(55, 6);
        state.trySummonRanga(4);
        state.tryEvolveAzathoth(8, true);

        assertTrue(state.canApplyReaperSeverance("red_reaper"));
        assertFalse(state.canApplyReaperSeverance("goblin")); // familia nao e reaper

        assertFalse(state.canExecuteDeath("red_reaper", 0.02f)); // vida acima do limiar de 1%
        assertTrue(state.canExecuteDeath("red_reaper", 0.01f));
        assertFalse(state.canExecuteDeath("red_reaper", 0f)); // zero nao conta (precisa ser > 0)
    }
}
