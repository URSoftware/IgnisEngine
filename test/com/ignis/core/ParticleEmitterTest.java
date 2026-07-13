package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sistema de particulas (Fase C): emissao por taxa, morte por vida, rajada,
 * limite do pool e round-trip da configuracao. Sem GUI — exercita a simulacao
 * via {@link ParticleEmitter#step(double)}.
 */
class ParticleEmitterTest {

    @Test
    void emiteConformeATaxa() {
        ParticleEmitter e = new ParticleEmitter();
        e.setEmissionRate(60);   // 60/s
        e.setMaxParticles(500);
        e.setLifetime(10);       // vida longa: nenhuma morre no teste
        e.setLifetimeVar(0);
        // 1 segundo em passos de 1/60 => ~60 particulas.
        for (int i = 0; i < 60; i++) e.step(1.0 / 60.0);
        assertTrue(e.getAliveCount() >= 58 && e.getAliveCount() <= 60,
                "após 1s a ~60/s deve haver ~60 vivas, veio " + e.getAliveCount());
    }

    @Test
    void particulasMorremAposAVida() {
        ParticleEmitter e = new ParticleEmitter();
        e.setEmissionRate(60);
        e.setMaxParticles(500);
        e.setLifetime(0.5);
        e.setLifetimeVar(0);
        for (int i = 0; i < 30; i++) e.step(1.0 / 60.0); // 0.5s emitindo
        int pico = e.getAliveCount();
        assertTrue(pico > 0, "deve haver particulas vivas no pico");
        e.setEmitting(false);
        for (int i = 0; i < 40; i++) e.step(1.0 / 60.0); // deixa expirarem
        assertEquals(0, e.getAliveCount(), "todas devem morrer apos a vida quando a emissao para");
    }

    @Test
    void poolLimitaOMaximoDeParticulas() {
        ParticleEmitter e = new ParticleEmitter();
        e.setMaxParticles(50);
        e.setLifetime(100);
        e.setLifetimeVar(0);
        e.burst(1000); // tenta muito mais que o pool
        assertEquals(50, e.getAliveCount(), "o pool limita o total de vivas");
    }

    @Test
    void burstEmiteInstantaneamente() {
        ParticleEmitter e = new ParticleEmitter();
        e.setMaxParticles(200);
        e.setLifetime(100);
        e.burst(30);
        assertEquals(30, e.getAliveCount());
    }

    @Test
    void configuracaoSobreviveAoRoundTrip() {
        Scene scene = new Scene("P");
        ParticleEmitter e = new ParticleEmitter();
        e.setName("Fogo");
        e.setEmissionRate(75);
        e.setMaxParticles(321);
        e.setLifetime(2.5);
        e.setGravityY(50);
        e.setSizeStart(20);
        e.setSizeEnd(1);
        scene.addEntity(e);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject back = loaded.findEntityByName("Fogo");
        assertNotNull(back);
        assertInstanceOf(ParticleEmitter.class, back);
        ParticleEmitter b = (ParticleEmitter) back;
        assertEquals(75, b.getEmissionRate(), 0.001);
        assertEquals(321, b.getMaxParticles());
        assertEquals(2.5, b.getLifetime(), 0.001);
        assertEquals(50, b.getGravityY(), 0.001);
        assertEquals(20, b.getSizeStart(), 0.001);
        assertEquals(1, b.getSizeEnd(), 0.001);
    }
}
