package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consistência do modelo EC do GameObject: {@code components} é a fonte única
 * de verdade e as listas derivadas ({@code scripts}, {@code scriptNames})
 * precisam permanecer coerentes em anexar/remover. A dessincronia dessas
 * listas foi a causa raiz da perda de scripts ao salvar e do estado corrompido
 * após Play/Stop (plano de consertos 2.1/2.2/2.3).
 */
class GameObjectComponentSyncTest {

    /** Script de usuário mínimo para os testes (sem depender de compilação). */
    static class StubScript extends IgnisScript {
        @Serialize
        public int velocidade = 7;
    }

    @Test
    void addComponentMantemAsTresListasCoerentes() {
        GameObject go = new GameObject();
        StubScript script = new StubScript();

        go.addComponent(script);

        assertTrue(go.getComponents().contains(script), "script deve entrar em components (serializado)");
        assertTrue(go.getScripts().contains(script), "script deve entrar em scripts (tick)");
        assertTrue(go.getScriptNames().contains("StubScript"), "nome deve entrar em scriptNames (reload)");
    }

    @Test
    void spriteComponentNaoEntraEmScriptNames() {
        GameObject go = new GameObject();
        go.addComponent(new SpriteComponent());

        assertFalse(go.getScriptNames().contains("SpriteComponent"),
                "componente nativo nao pode ser tratado como script de usuario nos reloads");
        assertTrue(go.getComponents().stream().anyMatch(c -> c instanceof SpriteComponent));
    }

    @Test
    void removeComponentLimpaAsTresListas() {
        GameObject go = new GameObject();
        StubScript script = new StubScript();
        go.addComponent(script);

        go.removeComponent(script);

        assertFalse(go.getComponents().contains(script));
        assertFalse(go.getScripts().contains(script));
        assertFalse(go.getScriptNames().contains("StubScript"));
    }

    @Test
    void removeScriptByNameLimpaComponentsTambem() {
        GameObject go = new GameObject();
        StubScript script = new StubScript();
        go.addComponent(script);

        go.removeScriptByName("StubScript");

        assertFalse(go.getComponents().contains(script),
                "instancia removida por nome nao pode sobrar em components (era re-serializada)");
        assertFalse(go.getScripts().contains(script));
        assertFalse(go.getScriptNames().contains("StubScript"));
    }

    @Test
    void tickComponentsAtualizaSoNaoScripts() {
        GameObject go = new GameObject();
        final int[] compUpdates = {0};
        Component nativo = new Component() {
            @Override
            public void update(float dt) { compUpdates[0]++; }
        };
        StubScript script = new StubScript() {
            @Override
            public void update(float dt) {
                throw new AssertionError("script nao deve ser atualizado por tickComponents");
            }
        };
        go.addComponent(nativo);
        go.addComponent(script);

        go.tickComponents(1.0f / 60.0f);

        assertEquals(1, compUpdates[0], "componente nao-script deve receber update(dt) do loop");
    }
}
