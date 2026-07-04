package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Sistema de eventos desacoplado (Event&lt;T&gt; + ColliderComponent/HealthComponent):
 * um impacto físico deve propagar por {@code onCollisionEnter} e ser consumido
 * pelos assinantes (antes era uma classe com main em src/ de produção).
 */
class EventSystemTest {

    @Test
    void impactoFisicoReduzVidaViaEvento() {
        GameObject obj = new GameObject();
        obj.setName("PlayerEntity");

        ColliderComponent collider = new ColliderComponent();
        HealthComponent health = new HealthComponent();
        obj.addComponent(collider);
        obj.addComponent(health); // awake() assina onCollisionEnter

        GameObject enemy = new GameObject();
        enemy.setName("SpikeTrap");

        collider.onPhysicsImpact(new CollisionData(enemy, "x:10.0, y:20.0"));

        assertEquals(90, health.getHealth(), "impacto deve descontar 10 de vida via evento");
    }

    @Test
    void notifyCollisionDisparaEventoDesacoplado() {
        GameObject a = new GameObject();
        a.setName("A");
        GameObject b = new GameObject();
        b.setName("B");

        final GameObject[] recebido = new GameObject[1];
        a.onCollisionEnter.subscribe(data -> recebido[0] = data.getOther());

        // Ponte CollisionManager legado -> evento EC (GameObject.notifyCollision).
        a.notifyCollision(b);

        assertSame(b, recebido[0], "onCollisionEnter deve carregar o outro objeto da colisao");
    }

    @Test
    void unsubscribeParaDeReceber() {
        GameObject obj = new GameObject();
        final int[] count = {0};
        java.util.function.Consumer<CollisionData> listener = d -> count[0]++;

        obj.onCollisionEnter.subscribe(listener);
        obj.notifyCollision(new GameObject());
        obj.onCollisionEnter.unsubscribe(listener);
        obj.notifyCollision(new GameObject());

        assertEquals(1, count[0], "listener removido nao deve receber novos eventos");
    }
}
