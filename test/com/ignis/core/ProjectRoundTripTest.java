package com.ignis.core;

import org.junit.jupiter.api.Test;
import org.json.JSONObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Round-trip de serialização do {@link Project} com múltiplas cenas e cena inicial
 * (item 7). Complementa {@link SceneRoundTripTest} (nível de cena).
 */
class ProjectRoundTripTest {

    @Test
    void multiplasCenasECenaInicialPreservadas() {
        Project p = new Project("MeuJogo");
        Scene menu = new Scene("Menu");
        Scene fase1 = new Scene("Fase1");
        p.addScene(menu);
        p.addScene(fase1);
        p.setMainScene("Fase1");

        JSONObject json = p.toJSON();
        Project loaded = Project.fromJSON(json);

        assertEquals("MeuJogo", loaded.getProjectName());
        assertEquals("Fase1", loaded.getMainScene(), "cena inicial preservada");
        // 3 cenas: a MainScene default do construtor + Menu + Fase1.
        assertEquals(3, loaded.getScenes().size());
        assertNotNull(loaded.getSceneByName("Menu"));
        assertNotNull(loaded.getSceneByName("Fase1"));
        // A cena atual deve resolver para a mainScene após o carregamento.
        assertEquals("Fase1", loaded.getCurrentScene().getSceneName());
    }
}
