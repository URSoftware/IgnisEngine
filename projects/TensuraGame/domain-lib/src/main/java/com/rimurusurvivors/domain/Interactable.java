package com.rimurusurvivors.domain;

/**
 * Alvo interativo do mundo (cristal, agua, parede vibrando, saida de area...). O
 * dominio conhece apenas identidade, posicao e verbo; arte, icone e texto final
 * vivem no apresentador e nos dados de dialogo (ver dialogueId).
 */
public record Interactable(
        String id,
        double x,
        double y,
        double radius,
        InteractionVerb verb,
        String dialogueId,
        boolean collectible,
        String targetAreaId,
        double targetSpawnX,
        double targetSpawnY) {

    public Interactable {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Interactable id is required.");
        }
        if (radius <= 0) {
            throw new IllegalArgumentException("Interactable radius must be positive.");
        }
        if (verb == null) {
            throw new IllegalArgumentException("Interactable verb is required.");
        }
        if (verb == InteractionVerb.ENTER && (targetAreaId == null || targetAreaId.isBlank())) {
            throw new IllegalArgumentException("ENTER interactable requires a targetAreaId.");
        }
    }

    public boolean isNear(double px, double py, double extraRange) {
        // Distancia ao quadrado, sem alocar Vec2 (chamado por interagivel a cada
        // tick de exploracao — ver historico de OOM da sessao longa do projeto).
        double range = radius + extraRange;
        double dx = x - px;
        double dy = y - py;
        return dx * dx + dy * dy <= range * range;
    }
}
