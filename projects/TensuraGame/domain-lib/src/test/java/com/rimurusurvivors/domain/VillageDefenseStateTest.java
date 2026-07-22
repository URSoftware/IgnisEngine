package com.rimurusurvivors.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageDefenseStateTest {

    @Test
    @DisplayName("Estado inicial deve comecar sem preparacoes")
    void initialStateIsEmpty() {
        VillageDefenseState state = new VillageDefenseState();
        assertEquals(0, state.count());
        assertFalse(state.isComplete());
    }

    @Test
    @DisplayName("Permite selecionar ate duas preparacoes defensivas")
    void allowsUpToTwoPreparations() {
        VillageDefenseState state = new VillageDefenseState();

        state = state.togglePreparation(VillagePreparation.REINFORCE_ENTRANCE);
        assertEquals(1, state.count());
        assertTrue(state.isSelected(VillagePreparation.REINFORCE_ENTRANCE));
        assertFalse(state.isComplete());

        state = state.togglePreparation(VillagePreparation.LIGHT_FLANK);
        assertEquals(2, state.count());
        assertTrue(state.isSelected(VillagePreparation.LIGHT_FLANK));
        assertTrue(state.isComplete());
    }

    @Test
    @DisplayName("Nao permite exceder limite de duas preparacoes")
    void enforcesMaximumTwoPreparations() {
        VillageDefenseState state = new VillageDefenseState()
                .togglePreparation(VillagePreparation.REINFORCE_ENTRANCE)
                .togglePreparation(VillagePreparation.LIGHT_FLANK);

        VillageDefenseState attempted = state.togglePreparation(VillagePreparation.CONTROLLED_BAIT);
        assertEquals(2, attempted.count());
        assertFalse(attempted.isSelected(VillagePreparation.CONTROLLED_BAIT));
    }

    @Test
    @DisplayName("Permite desmarcar uma preparacao previamente selecionada")
    void allowsTogglingOffSelectedPreparation() {
        VillageDefenseState state = new VillageDefenseState()
                .togglePreparation(VillagePreparation.REINFORCE_ENTRANCE)
                .togglePreparation(VillagePreparation.LIGHT_FLANK);

        state = state.togglePreparation(VillagePreparation.REINFORCE_ENTRANCE);
        assertEquals(1, state.count());
        assertFalse(state.isSelected(VillagePreparation.REINFORCE_ENTRANCE));
        assertTrue(state.isSelected(VillagePreparation.LIGHT_FLANK));
        assertFalse(state.isComplete());
    }

    @Test
    @DisplayName("Gera payload correto para o barramento de sinais de batalha")
    void generatesCorrectPayloadArray() {
        VillageDefenseState state = new VillageDefenseState()
                .togglePreparation(VillagePreparation.REINFORCE_ENTRANCE)
                .togglePreparation(VillagePreparation.CONTROLLED_BAIT);

        String[] payload = state.toPayload();
        assertArrayEquals(new String[]{"REINFORCE_ENTRANCE", "CONTROLLED_BAIT"}, payload);
    }

    @Test
    @DisplayName("Valida restricao no construtor direto")
    void constructorEnforcesLimit() {
        assertThrows(IllegalArgumentException.class, () -> new VillageDefenseState(Set.of(
                VillagePreparation.REINFORCE_ENTRANCE,
                VillagePreparation.LIGHT_FLANK,
                VillagePreparation.CONTROLLED_BAIT
        )));
    }
}
