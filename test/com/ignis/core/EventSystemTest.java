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
        Game game = new Game();
        GameObject obj = new GameObject();
        obj.setName("PlayerEntity");
        obj.setGame(game);

        ColliderComponent collider = new ColliderComponent();
        HealthComponent health = new HealthComponent();
        obj.addComponent(collider);
        obj.addComponent(health); // awake() assina via game.getSceneDispatcher()

        GameObject enemy = new GameObject();
        enemy.setName("SpikeTrap");

        collider.onPhysicsImpact(new CollisionData(enemy, "x:10.0, y:20.0"));
        game.getSceneDispatcher().processPendingSignals();

        assertEquals(90, health.getHealth(), "impacto deve descontar 10 de vida via evento");
    }

    @Test
    void notifyCollisionDisparaEventoDesacoplado() {
        Game game = new Game();
        GameObject a = new GameObject();
        a.setName("A");
        a.setGame(game);
        GameObject b = new GameObject();
        b.setName("B");

        final GameObject[] recebido = new GameObject[1];
        game.getSceneDispatcher().connect("onCollisionEnter_" + a.getId(), data -> {
            if (data instanceof CollisionData) {
                recebido[0] = ((CollisionData) data).getOther();
            }
        });

        a.notifyCollision(b);
        game.getSceneDispatcher().processPendingSignals();

        assertSame(b, recebido[0], "onCollisionEnter deve carregar o outro objeto da colisao");
    }

    @Test
    void unsubscribeParaDeReceber() {
        Game game = new Game();
        GameObject obj = new GameObject();
        obj.setGame(game);
        
        final int[] count = {0};
        com.fxutilities.fxevents.core.SignalReceiver listener = d -> count[0]++;

        game.getSceneDispatcher().connect("onCollisionEnter_" + obj.getId(), listener);
        obj.notifyCollision(new GameObject());
        game.getSceneDispatcher().processPendingSignals();
        
        game.getSceneDispatcher().disconnect("onCollisionEnter_" + obj.getId(), listener);
        obj.notifyCollision(new GameObject());
        game.getSceneDispatcher().processPendingSignals();

        assertEquals(1, count[0], "listener removido nao deve receber novos eventos");
    }
}
