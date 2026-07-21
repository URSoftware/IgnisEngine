package com.rimurusurvivors.domain;

/**
 * Resultado da tentativa de execucao de um comando de batalha.
 */
public record BattleActionResult(
        boolean valid,
        boolean turnConsumed,
        String message,
        BattleCommand commandExecuted) {

    public static BattleActionResult invalid(String reason) {
        return new BattleActionResult(false, false, reason, null);
    }

    public static BattleActionResult success(String message, BattleCommand command) {
        return new BattleActionResult(true, true, message, command);
    }

    public static BattleActionResult info(String message, BattleCommand command) {
        return new BattleActionResult(true, false, message, command);
    }
}
