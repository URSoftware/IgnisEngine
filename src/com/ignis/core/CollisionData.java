package com.ignis.core;

/**
 * Encapsula as informações sobre o impacto de colisão física.
 */
public class CollisionData {
    private final GameObject other;
    private final String collisionPoint;

    /**
     * Cria uma nova instância de dados de colisão.
     * 
     * @param other O GameObject atingido.
     * @param collisionPoint O ponto de colisão representado em texto ou vetor simplificado.
     */
    public CollisionData(GameObject other, String collisionPoint) {
        this.other = other;
        this.collisionPoint = collisionPoint;
    }

    /**
     * Obtém o outro GameObject envolvido no impacto.
     * 
     * @return O GameObject colidido.
     */
    public GameObject getOther() {
        return other;
    }

    /**
     * Obtém o vetor ou ponto textual do impacto.
     * 
     * @return Ponto de colisão.
     */
    public String getCollisionPoint() {
        return collisionPoint;
    }
}
