package com.ignis.core;

/**
 * Classe de teste para validar o desacoplamento do sistema de eventos de colisão.
 */
public class EventSystemTest {
    public static void main(String[] args) {
        System.out.println("=== Testando Sistema de Eventos de Colisao ===");

        // 1. Criar GameObject
        GameObject obj = new GameObject();
        obj.setName("PlayerEntity");

        // 2. Criar e acoplar componentes
        ColliderComponent collider = new ColliderComponent();
        HealthComponent health = new HealthComponent();

        obj.addComponent(collider);
        obj.addComponent(health);

        // Os componentes sao inicializados (awake) automaticamente durante o addComponent.

        // 3. Simular impacto fisico
        GameObject enemy = new GameObject();
        enemy.setName("SpikeTrap");

        CollisionData collision = new CollisionData(enemy, "x:10.0, y:20.0");

        System.out.println("Disparando colisao simulada...");
        collider.onPhysicsImpact(collision);

        System.out.println("Vida final apos colisao: " + health.getHealth());
    }
}
