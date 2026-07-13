package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Hierarquia pai-filho (Fase C): captura de offset sem salto, seguir translacao e
 * rotacao do pai, rejeicao de ciclos, cadeia avo-pai-filho e round-trip por id.
 * x/y permanecem em coordenadas de mundo; o sync recompoe a partir do pai.
 */
class HierarchyTest {

    private static GameObject go(String name, double x, double y) {
        return new GameObject(name, null, x, y, 16, 16);
    }

    @Test
    void setParentCapturaOffsetSemSalto() {
        GameObject pai = go("Pai", 100, 100);
        GameObject filho = go("Filho", 130, 100);
        filho.setParent(pai);
        // Sem o pai mover, sincronizar nao pode mudar a posicao de mundo do filho.
        filho.syncToParent();
        assertEquals(130, filho.getX(), 0.001);
        assertEquals(100, filho.getY(), 0.001);
    }

    @Test
    void filhoSegueTranslacaoDoPai() {
        GameObject pai = go("Pai", 0, 0);
        GameObject filho = go("Filho", 50, 0);
        filho.setParent(pai);
        pai.setX(200);
        pai.setY(-30);
        filho.syncToParent();
        assertEquals(250, filho.getX(), 0.001, "filho translada junto com o pai");
        assertEquals(-30, filho.getY(), 0.001);
    }

    @Test
    void filhoOrbitaAoRotacionarOPai() {
        GameObject pai = go("Pai", 0, 0);
        GameObject filho = go("Filho", 10, 0); // offset (10,0)
        filho.setParent(pai);
        pai.setRotation(90); // rotaciona 90 graus
        filho.syncToParent();
        // offset (10,0) rotacionado 90deg -> (0,10)
        assertEquals(0, filho.getX(), 1e-6);
        assertEquals(10, filho.getY(), 1e-6);
        assertEquals(90, filho.getRotation(), 1e-6, "rotacao do filho compoe com a do pai");
    }

    @Test
    void clearParentDesligaEMantemPosicao() {
        GameObject pai = go("Pai", 0, 0);
        GameObject filho = go("Filho", 40, 40);
        filho.setParent(pai);
        filho.clearParent();
        pai.setX(999);
        filho.syncToParent(); // sem pai, no-op
        assertNull(filho.getParent());
        assertEquals(40, filho.getX(), 0.001, "sem pai, o filho nao segue mais");
    }

    @Test
    void autoParentErejeitado() {
        GameObject a = go("A", 0, 0);
        a.setParent(a);
        assertNull(a.getParent(), "objeto nao pode ser pai de si mesmo");
    }

    @Test
    void cicloERejeitado() {
        GameObject a = go("A", 0, 0);
        GameObject b = go("B", 10, 0);
        b.setParent(a);       // b filho de a
        a.setParent(b);       // tentar a filho de b criaria ciclo
        assertNull(a.getParent(), "ciclo deve ser rejeitado");
        assertSame(a, b.getParent(), "o vinculo valido (b->a) permanece");
    }

    @Test
    void cadeiaAvoPaiFilhoSincronizaEmOrdem() {
        Scene scene = new Scene("H");
        GameObject avo = go("Avo", 0, 0);
        GameObject pai = go("Pai", 10, 0);
        GameObject filho = go("Filho", 20, 0);
        scene.addEntity(avo);
        scene.addEntity(pai);
        scene.addEntity(filho);
        pai.setParent(avo);
        filho.setParent(pai);

        assertEquals(0, avo.hierarchyDepth());
        assertEquals(1, pai.hierarchyDepth());
        assertEquals(2, filho.hierarchyDepth());

        avo.setX(100);
        // Ordem pai-antes-filho: avo -> pai -> filho.
        avo.syncToParent();
        pai.syncToParent();
        filho.syncToParent();
        assertEquals(110, pai.getX(), 0.001);
        assertEquals(120, filho.getX(), 0.001, "o filho segue atraves da cadeia");
    }

    @Test
    void vinculoSobreviveAoRoundTrip() {
        Scene scene = new Scene("H");
        GameObject pai = go("Pai", 0, 0);
        GameObject filho = go("Filho", 25, 15);
        scene.addEntity(pai);
        scene.addEntity(filho);
        filho.setParent(pai);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject paiBack = loaded.findEntityByName("Pai");
        GameObject filhoBack = loaded.findEntityByName("Filho");
        assertNotNull(filhoBack.getParent(), "vinculo pai-filho deve ser religado por id");
        assertSame(paiBack, filhoBack.getParent());

        paiBack.setX(300);
        filhoBack.syncToParent();
        assertEquals(325, filhoBack.getX(), 0.001, "offset local preservado no round-trip");
    }
}
