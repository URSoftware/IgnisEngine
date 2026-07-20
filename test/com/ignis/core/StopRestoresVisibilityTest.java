package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressao: o Stop deve restaurar a VISIBILIDADE original dos objetos, nao so
 * posicao/tamanho. Scripts (cutscenes, diretores) escondem objetos durante o
 * Play; sem restaurar a visibilidade no Stop, o estado invisivel vazava para o
 * editor e o auto-save persistia a cena inteira invisivel — deixando o viewport
 * em branco ao reabrir o projeto (bug observado no TensuraGame).
 */
class StopRestoresVisibilityTest {

    @Test
    void stopRestauraVisibilidadeAlteradaDuranteOPlay() {
        Game game = new Game();
        game.setSuppressAwtRepaint(true);

        GameObject visivel = new GameObject("Prop", game, 0, 0, 32, 32);
        GameObject tambemVisivel = new GameObject("Rimuru", game, 10, 10, 28, 28);
        game.addEntity(visivel);
        game.addEntity(tambemVisivel);

        // Estado autoral: visiveis.
        assertTrue(visivel.isVisible());
        assertTrue(tambemVisivel.isVisible());

        // Play tira o snapshot inicial.
        game.playWorld();

        // Um script esconde os objetos durante o Play (cutscene/diretor).
        visivel.setVisible(false);
        tambemVisivel.setVisible(false);
        assertFalse(visivel.isVisible());

        // Stop deve restaurar a visibilidade original.
        game.stopWorld();

        assertTrue(visivel.isVisible(), "Stop deve restaurar a visibilidade original do objeto");
        assertTrue(tambemVisivel.isVisible(), "Stop deve restaurar a visibilidade de todos os objetos");
    }

    @Test
    void stopPreservaObjetoAutoralmenteInvisivel() {
        Game game = new Game();
        game.setSuppressAwtRepaint(true);

        GameObject oculto = new GameObject("Trigger", game, 0, 0, 16, 16);
        oculto.setVisible(false); // invisivel por design, antes do Play
        game.addEntity(oculto);

        game.playWorld();
        oculto.setVisible(true); // script revela durante o Play
        game.stopWorld();

        assertFalse(oculto.isVisible(),
                "Stop deve restaurar o estado autoral (invisivel), nao o de runtime");
    }
}
