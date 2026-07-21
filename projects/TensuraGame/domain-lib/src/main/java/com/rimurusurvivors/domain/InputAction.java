package com.rimurusurvivors.domain;

/**
 * Uma acao atomica de uma {@link InputTape}: direcao com duracao, interacao,
 * avanco de dialogo ou espera. {@code Interact} e {@code AdvanceDialogue}
 * produzem o mesmo sinal que a simulacao consome ({@code interactPressed} no
 * frame) — o jogo real usa a mesma tecla para os dois; nomes distintos aqui so
 * documentam a intencao de quem escreve a fita.
 */
public sealed interface InputAction {

    /** Direcao em coordenadas de mundo (ver {@link RunInput}), por durationSeconds. */
    record Move(double horizontal, double vertical, double durationSeconds) implements InputAction {
        public Move {
            if (!Double.isFinite(horizontal) || !Double.isFinite(vertical)) {
                throw new IllegalArgumentException("Move axes must be finite.");
            }
            if (!Double.isFinite(durationSeconds) || durationSeconds < 0) {
                throw new IllegalArgumentException("Move duration must be finite and non-negative.");
            }
        }
    }

    /** Um unico frame de interacao: abre dialogo, coleta ou entra numa saida. */
    record Interact() implements InputAction {
    }

    /** Um unico frame de avanco/fechamento de um dialogo ja aberto. */
    record AdvanceDialogue() implements InputAction {
    }

    /** Parado, sem mover nem interagir, por durationSeconds. */
    record Wait(double durationSeconds) implements InputAction {
        public Wait {
            if (!Double.isFinite(durationSeconds) || durationSeconds < 0) {
                throw new IllegalArgumentException("Wait duration must be finite and non-negative.");
            }
        }
    }
}
