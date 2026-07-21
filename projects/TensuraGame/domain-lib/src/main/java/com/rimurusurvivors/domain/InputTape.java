package com.rimurusurvivors.domain;

import java.util.List;

/**
 * Sequencia deterministica de {@link InputAction} para dirigir uma
 * {@link ExplorationSimulation} sem teclado real e sem relogio do sistema (ver
 * {@link ExplorationInputTapeRunner}). Infraestrutura de teste — nao um
 * segundo motor de gameplay.
 */
public record InputTape(List<InputAction> actions) {

    public InputTape {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }
}
