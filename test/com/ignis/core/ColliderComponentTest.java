package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Testes de unidade do {@link ColliderComponent} (item 8c): geometria da hitbox,
 * bounds em mundo, redimensionamento (usado pelo gizmo do item 8b), mapeamento de
 * forma para o tipo concreto de collider e construcao do collider de runtime.
 */
class ColliderComponentTest {

    private static final double EPS = 0.0001;

    private GameObject owner(double x, double y, int w, int h) {
        GameObject go = new GameObject();
        go.setX(x);
        go.setY(y);
        go.setWidth(w);
        go.setHeight(h);
        return go;
    }

    @Test
    void boxDefaultBoundsMatchOwner() {
        GameObject go = owner(10, 20, 100, 50);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);

        double[] b = cc.getWorldBounds();
        assertNotNull(b);
        assertEquals(10, b[0], EPS, "minX = owner.x");
        assertEquals(20, b[1], EPS, "minY = owner.y");
        assertEquals(100, b[2], EPS, "largura default = largura do dono");
        assertEquals(50, b[3], EPS, "altura default = altura do dono");
    }

    @Test
    void boxOffsetShiftsBounds() {
        GameObject go = owner(0, 0, 40, 40);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);
        cc.setOffsetX(5);
        cc.setOffsetY(-3);

        double[] b = cc.getWorldBounds();
        assertEquals(5, b[0], EPS);
        assertEquals(-3, b[1], EPS);
    }

    @Test
    void explicitBoxSizeOverridesOwner() {
        GameObject go = owner(0, 0, 40, 40);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);
        cc.setWidth(64);
        cc.setHeight(24);

        assertEquals(64, cc.effectiveWidth(), EPS);
        assertEquals(24, cc.effectiveHeight(), EPS);
        double[] b = cc.getWorldBounds();
        assertEquals(64, b[2], EPS);
        assertEquals(24, b[3], EPS);
    }

    @Test
    void sphereBoundsFromDefaultRadius() {
        GameObject go = owner(0, 0, 80, 40);
        ColliderComponent cc = new ColliderComponent();
        cc.setShape("Sphere");
        go.addComponent(cc);

        // Raio default = metade do menor lado = 20; centro no meio do dono (40,20).
        assertEquals(20, cc.effectiveRadius(), EPS);
        double[] b = cc.getWorldBounds();
        assertEquals(40 - 20, b[0], EPS, "minX = centroX - raio");
        assertEquals(20 - 20, b[1], EPS, "minY = centroY - raio");
        assertEquals(40, b[2], EPS, "largura = 2*raio");
        assertEquals(40, b[3], EPS, "altura = 2*raio");
    }

    @Test
    void resolveColliderTypeMapsShapes() {
        GameObject go = owner(0, 0, 10, 10);
        ColliderComponent box = new ColliderComponent();
        go.addComponent(box);
        assertEquals(IgnisSampleCollisions.ColliderType.AABB, box.resolveColliderType());

        box.setShape("Sphere");
        assertEquals(IgnisSampleCollisions.ColliderType.CIRCLE, box.resolveColliderType());

        box.setShape("Capsule");
        assertEquals(IgnisSampleCollisions.ColliderType.AABB, box.resolveColliderType(),
                "Capsule ainda cai em AABB (sem forma dedicada no motor)");
    }

    @Test
    void resizeBoxRoundTripsThroughBounds() {
        GameObject go = owner(100, 100, 32, 32);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);

        cc.resizeToWorldBounds(90, 95, 50, 60);
        double[] b = cc.getWorldBounds();
        assertEquals(90, b[0], EPS);
        assertEquals(95, b[1], EPS);
        assertEquals(50, b[2], EPS);
        assertEquals(60, b[3], EPS);
        // offset relativo ao dono
        assertEquals(-10, cc.getOffsetX(), EPS);
        assertEquals(-5, cc.getOffsetY(), EPS);
    }

    @Test
    void resizeClampsToMinimumSize() {
        GameObject go = owner(0, 0, 32, 32);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);

        cc.resizeToWorldBounds(0, 0, 0, 0);
        double[] b = cc.getWorldBounds();
        assertTrue(b[2] >= 1, "largura minima de 1");
        assertTrue(b[3] >= 1, "altura minima de 1");
    }

    @Test
    void runtimeColliderIsBuiltAndSynced() {
        GameObject go = owner(10, 20, 40, 40);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);
        cc.setWidth(48);
        cc.setHeight(24);

        IgnisSampleCollisions.Collider rc = cc.getOrBuildRuntimeCollider();
        assertNotNull(rc);
        assertInstanceOf(IgnisSampleCollisions.AABBCollider.class, rc);
        IgnisSampleCollisions.AABBCollider aabb = (IgnisSampleCollisions.AABBCollider) rc;
        assertEquals(48, aabb.getWidth(), EPS);
        assertEquals(24, aabb.getHeight(), EPS);
        // minX = owner.x + offsetX (offset default 0)
        assertEquals(10, aabb.getMinX(), EPS);
        assertEquals(20, aabb.getMinY(), EPS);
    }

    @Test
    void shapeChangeRebuildsRuntimeColliderType() {
        GameObject go = owner(0, 0, 40, 40);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);

        assertInstanceOf(IgnisSampleCollisions.AABBCollider.class, cc.getOrBuildRuntimeCollider());
        cc.setShape("Sphere");
        assertInstanceOf(IgnisSampleCollisions.CircleCollider.class, cc.getOrBuildRuntimeCollider());
    }

    @Test
    void triggerFlagMapsToCollisionMode() {
        GameObject go = owner(0, 0, 40, 40);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);
        cc.setTrigger(true);

        IgnisSampleCollisions.Collider rc = cc.getOrBuildRuntimeCollider();
        assertEquals(IgnisSampleCollisions.CollisionMode.TRIGGER, rc.getMode());

        cc.setTrigger(false);
        rc = cc.getOrBuildRuntimeCollider();
        assertEquals(IgnisSampleCollisions.CollisionMode.COLLISION, rc.getMode());
    }

    @Test
    void detachWithoutGameDoesNotThrow() {
        GameObject go = owner(0, 0, 10, 10);
        ColliderComponent cc = new ColliderComponent();
        go.addComponent(cc);
        // Sem Game associado, remover nao deve lancar excecao.
        go.removeComponent(cc);
        assertFalse(go.getComponents().contains(cc));
    }

    @Test
    void serializationRoundTripsGeometry() {
        GameObject go = owner(0, 0, 40, 40);
        ColliderComponent cc = new ColliderComponent();
        cc.setShape("Sphere");
        cc.setRadius(17);
        cc.setOffsetX(3);
        cc.setOffsetY(-4);
        cc.setFriction(0.8);
        cc.setBounciness(0.25);
        cc.setTrigger(true);
        cc.setEnabled(false);
        cc.setCollisionLayer("Enemy");
        go.addComponent(cc);

        org.json.JSONObject props = cc.saveProperties();

        GameObject go2 = owner(0, 0, 40, 40);
        ColliderComponent loaded = new ColliderComponent();
        go2.addComponent(loaded);
        loaded.loadProperties(props, name -> null);

        assertEquals("Sphere", loaded.getShape());
        assertEquals(17, loaded.getRadius(), EPS);
        assertEquals(3, loaded.getOffsetX(), EPS);
        assertEquals(-4, loaded.getOffsetY(), EPS);
        assertEquals(0.8, loaded.getFriction(), EPS);
        assertEquals(0.25, loaded.getBounciness(), EPS);
        assertTrue(loaded.isTrigger());
        assertFalse(loaded.isEnabled());
        assertEquals("Enemy", loaded.getCollisionLayer());
    }

    @Test
    void sphereResizeUsesSmallerHalfSide() {
        GameObject go = owner(0, 0, 40, 40);
        ColliderComponent cc = new ColliderComponent();
        cc.setShape("Sphere");
        go.addComponent(cc);

        // Bounds 60x40 => raio = min(60,40)/2 = 20, centro em (30,20).
        cc.resizeToWorldBounds(0, 0, 60, 40);
        assertEquals(20, cc.getRadius(), EPS);
        double[] b = cc.getWorldBounds();
        assertEquals(20 * 2, b[2], EPS, "bounds do circulo = 2*raio");
        assertEquals(20 * 2, b[3], EPS);
    }
}
