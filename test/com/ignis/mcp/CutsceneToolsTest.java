package com.ignis.mcp;

import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.Square;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fluxo completo de cutscene via ferramentas MCP (P1, passo 5), headless: autoria
 * em arquivo, validacao contra a cena viva, preview read-only e execucao
 * deterministica no Play. Os handlers sao executados direto ({@code def.handler}) —
 * o dispatch FX do {@code call()} e exercitado no editor vivo; aqui cobrimos a
 * logica das ferramentas.
 */
class CutsceneToolsTest {

    @TempDir
    File projectFolder;

    private IgnisToolRegistry registry;
    private Game game;

    @BeforeEach
    void setUp() {
        registry = new IgnisToolRegistry(projectFolder);
        game = new Game();
        game.setSize(320, 180);
        registry.attachLiveEditor(game, () -> { }, () -> { }, () -> { }, () -> { }, null, () -> { });
    }

    private String exec(String tool, JSONObject args) throws Exception {
        return registry.get(tool).handler.execute(args);
    }

    @Test
    void autoriaPersisteEmArquivoDoProjeto() throws Exception {
        String r = exec("create_cutscene", new JSONObject().put("name", "intro").put("durationFrames", 120));
        assertTrue(r.contains("criada"), r);
        assertTrue(new File(projectFolder, "cutscenes/intro.cutscene.json").isFile(),
                "cutscene deve virar arquivo no projeto");

        r = exec("add_cutscene_keyframe", new JSONObject().put("name", "intro").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 0).put("x", 0).put("y", 50));
        assertTrue(r.contains("gravado"), r);
        r = exec("add_cutscene_keyframe", new JSONObject().put("name", "intro").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 60).put("x", 120).put("y", 50));
        assertTrue(r.contains("gravado"), r);

        String json = exec("get_cutscene", new JSONObject().put("name", "intro"));
        assertTrue(json.contains("\"Hero\""), json);
        assertTrue(exec("list_cutscenes", new JSONObject()).contains("intro"));
    }

    @Test
    void nomeInvalidoERecusadoAntesDeTocarDisco() throws Exception {
        String r = exec("create_cutscene", new JSONObject().put("name", "../fora"));
        assertTrue(r.startsWith("Erro"), r);
        r = exec("create_cutscene", new JSONObject().put("name", "com espaco"));
        assertTrue(r.startsWith("Erro"), r);
    }

    @Test
    void validacaoUsaOsAtoresDaCenaViva() throws Exception {
        exec("create_cutscene", new JSONObject().put("name", "cena1"));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "cena1").put("type", "ACTOR")
                .put("target", "Fantasma").put("frame", 0).put("x", 0).put("y", 0));

        String r = exec("validate_cutscene", new JSONObject().put("name", "cena1"));
        assertTrue(r.contains("Ator ausente"), r);

        GameObject hero = new Square();
        hero.setName("Fantasma");
        game.getEntities().add(hero);
        r = exec("validate_cutscene", new JSONObject().put("name", "cena1"));
        assertTrue(r.startsWith("OK"), r);
    }

    @Test
    void previewNaoTocaACena() throws Exception {
        GameObject hero = new Square();
        hero.setName("Hero");
        hero.setX(999);
        hero.setY(999);
        game.getEntities().add(hero);

        exec("create_cutscene", new JSONObject().put("name", "prev").put("durationFrames", 60));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "prev").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 0).put("x", 0).put("y", 0));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "prev").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 60).put("x", 100).put("y", 0));

        String r = exec("preview_cutscene", new JSONObject().put("name", "prev").put("frame", 30));
        assertTrue(r.contains("(50.0,0.0)"), "deve amostrar a pose interpolada: " + r);
        assertEquals(999, (int) hero.getX(), "preview e read-only: o ator nao se move");
    }

    @Test
    void runCutsceneExigePlayEMoveOAtorDeterministicamente() throws Exception {
        GameObject hero = new Square();
        hero.setName("Hero");
        game.getEntities().add(hero);

        exec("create_cutscene", new JSONObject().put("name", "andar").put("durationFrames", 10));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "andar").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 0).put("x", 0).put("y", 0));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "andar").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 10).put("x", 100).put("y", 0));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "andar").put("type", "DIALOG")
                .put("target", "Hero").put("frame", 5).put("text", "Chegamos!"));

        String r = exec("run_cutscene", new JSONObject().put("name", "andar"));
        assertTrue(r.startsWith("Erro"), "sem Play deve recusar: " + r);

        game.playWorld();
        game.pauseWorld(); // execucao deterministica passo-a-passo
        r = exec("run_cutscene", new JSONObject().put("name", "andar"));
        assertTrue(r.contains("executada"), r);
        assertTrue(r.contains("Chegamos!"), "evento de dialogo deve ser reportado: " + r);
        assertEquals(100, (int) hero.getX(), "ator termina na pose do ultimo keyframe");
    }

    @Test
    void skipChegaAoMesmoEstadoFinalEListaEventos() throws Exception {
        GameObject hero = new Square();
        hero.setName("Hero");
        game.getEntities().add(hero);

        exec("create_cutscene", new JSONObject().put("name", "fim").put("durationFrames", 30));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "fim").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 0).put("x", 0).put("y", 0));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "fim").put("type", "ACTOR")
                .put("target", "Hero").put("frame", 30).put("x", 77).put("y", 0));
        exec("add_cutscene_keyframe", new JSONObject().put("name", "fim").put("type", "SIGNAL")
                .put("frame", 15).put("data", "meio_da_cena"));

        game.playWorld();
        game.pauseWorld();
        String r = exec("run_cutscene", new JSONObject().put("name", "fim").put("skip", true));
        assertTrue(r.contains("estado final"), r);
        assertTrue(r.contains("meio_da_cena"), "skip lista os eventos que teriam disparado: " + r);
        assertEquals(77, (int) hero.getX(), "skip chega ao mesmo estado final da conclusao natural");
    }
}
