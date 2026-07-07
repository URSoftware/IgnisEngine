package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de unidade do {@link RigidbodyComponent}: integracao de gravidade,
 * applyImpulse, applyForce, resetForces e resposta a colisao (zeragem da
 * componente de velocidade na normal do impacto).
 */
class RigidbodyComponentTest {

    private static final float DT = 1.0f / 60.0f;
    private static final double EPS = 0.01;

    @Test
    void gravidadeAceleraObjetoParaBaixo() {
        GameObject go = new GameObject();
        go.setX(0.0);
        go.setY(0.0);
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setUseGravity(true);
        rb.setGravityScale(1.0);
        double g0 = RigidbodyComponent.getGlobalGravity();
        RigidbodyComponent.setGlobalGravity(980.0);
        go.addComponent(rb);

        // Posicao Y inicial = 0. Apos um tick, Y > 0 (gravidade puxa para baixo).
        rb.update(DT);

        assertTrue(go.getY() > 0.0, "gravidade deve mover o objeto para baixo (Y crescente)");
        assertEquals(0.0, go.getX(), EPS, "sem forca horizontal, X nao muda");
        RigidbodyComponent.setGlobalGravity(g0);
    }

    @Test
    void useGravityFalseNaoAcelera() {
        GameObject go = new GameObject();
        go.setX(100.0);
        go.setY(100.0);
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setUseGravity(false);
        go.addComponent(rb);

        rb.update(DT);

        assertEquals(100.0, go.getX(), EPS, "X deve permanecer inalterado");
        assertEquals(100.0, go.getY(), EPS, "Y deve permanecer inalterado sem gravidade");
    }

    @Test
    void applyImpulseMudaVelocidadeInstantaneamente() {
        GameObject go = new GameObject();
        go.setX(0.0);
        go.setY(0.0);
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setUseGravity(false);
        go.addComponent(rb);

        // Impulso horizontal de 100 px/s para a direita.
        rb.applyImpulse(100.0, 0.0);

        rb.update(DT);

        // Velocidade X = 100, posicao apos dt = 100 * (1/60) ~= 1.67.
        assertEquals(100.0, rb.getVelocityX(), EPS, "impulso deve setar velocidade X");
        assertEquals(100.0 / 60.0, go.getX(), EPS, "posicao X deve refletir velocidade * dt");
        assertEquals(0.0, rb.getVelocityY(), EPS, "impulso so em X nao afeta Y");
    }

    @Test
    void applyForceAcumuladaIntegradaNoProximoTick() {
        GameObject go = new GameObject();
        go.setX(0.0);
        go.setY(0.0);
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setUseGravity(false);
        rb.setMass(1.0);
        go.addComponent(rb);

        // Forca de 600 px/s^2 (massa 1) -> aceleracao = 600.
        rb.applyForce(600.0, 0.0);

        rb.update(DT);

        // Velocidade X = 600 * dt = 600/60 = 10.
        assertEquals(10.0, rb.getVelocityX(), EPS, "forca deve integrar em velocidade");
        // Posicao X = 10 * dt = 10/60 ~= 0.167.
        assertEquals(10.0 / 60.0, go.getX(), EPS, "posicao X deve refletir velocidade * dt");
    }

    @Test
    void resetForcesZeraAceleracaoAcumulada() {
        GameObject go = new GameObject();
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setUseGravity(false);
        go.addComponent(rb);

        rb.applyForce(500.0, 0.0);
        rb.resetForces();
        assertEquals(0.0, rb.getAccelerationX(), EPS, "resetForces deve zerar aceleracao X");

        rb.update(DT);
        assertEquals(0.0, rb.getVelocityX(), EPS, "sem forca, velocidade permanece zero");
    }

    @Test
    void frozenIgnoraForcasEImpulsos() {
        GameObject go = new GameObject();
        go.setX(50.0);
        go.setY(50.0);
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setUseGravity(true);
        rb.setFrozen(true);
        go.addComponent(rb);

        rb.applyImpulse(200.0, 0.0);
        rb.applyForce(0.0, -500.0);
        rb.update(DT);

        assertEquals(50.0, go.getX(), EPS, "congelado: X nao muda");
        assertEquals(50.0, go.getY(), EPS, "congelado: Y nao muda (ignora gravidade e forca)");
        assertEquals(0.0, rb.getVelocityX(), EPS, "congelado: velocidade X zero");
        assertEquals(0.0, rb.getVelocityY(), EPS, "congelado: velocidade Y zero");
    }

    @Test
    void linearDragDesaceleraComTempo() {
        GameObject go = new GameObject();
        go.setX(0.0);
        go.setY(0.0);
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setUseGravity(false);
        rb.setLinearDrag(1.0); // forte arrasto
        go.addComponent(rb);

        // Impulso inicial de 1000 px/s.
        rb.applyImpulse(1000.0, 0.0);
        rb.update(DT);

        // Apos um tick, arrasto reduz velocidade: v = v0 * (1 - drag * dt).
        double expectedVx = 1000.0 * (1.0 - 1.0 * DT);
        assertEquals(expectedVx, rb.getVelocityX(), 1.0, "drag deve reduzir velocidade no tick");
    }

    @Test
    void colisaoRemoveComponenteDeVelocidadeNaNormal() {
        Game game = new Game();
        GameObject a = new GameObject();
        a.setName("A");
        a.setX(0.0);
        a.setY(0.0);
        a.setWidth(32);
        a.setHeight(32);
        a.setGame(game);
        RigidbodyComponent rbA = new RigidbodyComponent();
        rbA.setUseGravity(false);
        a.addComponent(rbA);
        // Velocidade de A apontando para +X (direcao do impacto com B, que esta a direita).
        rbA.setVelocityX(100.0);
        rbA.setVelocityY(0.0);

        GameObject b = new GameObject();
        b.setName("B");
        b.setX(40.0); // a direita de A
        b.setY(0.0);
        b.setWidth(32);
        b.setHeight(32);

        // Simula colisao: A invoke onCollisionEnter com CollisionData(B, ...).
        // A normal aponta de B para A (para a esquerda, -X), e a velocidade de A
        // tem componente +X na normal — o Rigbody deve remove-la.
        a.notifyCollision(b);
        game.getSceneDispatcher().processPendingSignals();

        // Velocidade X deve ser zero (componente removida); Y permanece zero.
        assertEquals(0.0, rbA.getVelocityX(), EPS, "colisao deve zerar componente de velocidade na normal");
        assertEquals(0.0, rbA.getVelocityY(), EPS, "velocidade perpendicular a normal nao muda");
    }

    @Test
    void rigidbodyESerializadoComoComponenteNativo() {
        GameObject go = new GameObject();
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setVelocityX(42.5);
        rb.setMass(3.0);
        go.addComponent(rb);

        // isNativeComponent deve reconhece-lo (pacote com.ignis.core, sem inner class).
        assertTrue(GameObject.isNativeComponent(rb), "RigidbodyComponent deve ser reconhecido como nativo");

        // Serializacao generica preserva campos @Serialize.
        org.json.JSONObject props = rb.saveProperties();
        assertTrue(props.has("velocityX"), "velocityX deve ser serializado");
        assertEquals(42.5, props.getDouble("velocityX"), EPS);
        assertEquals(3.0, props.getDouble("mass"), EPS);

        // Desserializacao restaura os valores.
        RigidbodyComponent rb2 = new RigidbodyComponent();
        rb2.loadProperties(props, name -> null);
        assertEquals(42.5, rb2.getVelocityX(), EPS, "velocityX deve ser restaurado");
        assertEquals(3.0, rb2.getMass(), EPS, "mass deve ser restaurada");
    }

    @Test
    void massaMínimaNaoPodeSerZero() {
        RigidbodyComponent rb = new RigidbodyComponent();
        rb.setMass(0.0);
        assertTrue(rb.getMass() >= 0.01, "massa deve ter piso minimo");
    }

    @Test
    void bouncinessRefleteVelocidadeNaNormal() {
        Game game = new Game();
        GameObject a = new GameObject();
        a.setX(0.0); a.setY(0.0); a.setWidth(32); a.setHeight(32);
        a.setGame(game);
        RigidbodyComponent rbA = new RigidbodyComponent();
        rbA.setUseGravity(false);
        a.addComponent(rbA);
        // Collider elastico (bounciness 1, sem atrito).
        ColliderComponent cc = new ColliderComponent();
        cc.setBounciness(1.0);
        cc.setFriction(0.0);
        a.addComponent(cc);
        rbA.setVelocityX(100.0);

        GameObject b = new GameObject();
        b.setX(40.0); b.setY(0.0); b.setWidth(32); b.setHeight(32);
        a.notifyCollision(b); // normal aponta de B para A (-X)
        game.getSceneDispatcher().processPendingSignals();

        assertEquals(-100.0, rbA.getVelocityX(), EPS, "bounciness 1 deve refletir a velocidade na normal");
        assertEquals(0.0, rbA.getVelocityY(), EPS);
    }

    @Test
    void frictionAtenuaVelocidadeTangencial() {
        Game game = new Game();
        GameObject a = new GameObject();
        a.setX(0.0); a.setY(0.0); a.setWidth(32); a.setHeight(32);
        a.setGame(game);
        RigidbodyComponent rbA = new RigidbodyComponent();
        rbA.setUseGravity(false);
        a.addComponent(rbA);
        ColliderComponent cc = new ColliderComponent();
        cc.setBounciness(0.0);
        cc.setFriction(0.5);
        a.addComponent(cc);
        // Componente normal (+X, contra B) + tangencial (+Y, ao longo da parede).
        rbA.setVelocityX(100.0);
        rbA.setVelocityY(50.0);

        GameObject b = new GameObject();
        b.setX(40.0); b.setY(0.0); b.setWidth(32); b.setHeight(32);
        a.notifyCollision(b); // normal -X; tangencial = eixo Y
        game.getSceneDispatcher().processPendingSignals();

        assertEquals(0.0, rbA.getVelocityX(), EPS, "normal removida (bounciness 0)");
        assertEquals(25.0, rbA.getVelocityY(), EPS, "tangencial reduzida pela metade (friction 0.5)");
    }
}
