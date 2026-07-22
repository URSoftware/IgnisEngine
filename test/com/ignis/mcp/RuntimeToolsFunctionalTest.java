package com.ignis.mcp;

import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.Square;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comportamento (nao so contrato) das ferramentas P0 de fechamento: fita de input
 * determinista, snapshots nomeados de cena e arvore de UI — headless, executando os
 * handlers direto (o dispatch FX do call() e coberto no editor vivo).
 */
class RuntimeToolsFunctionalTest {

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

    // ------------------------------------------------------------------
    // run_input_tape
    // ------------------------------------------------------------------

    @Test
    void fitaAvancaAplicaEZeraOInput() throws Exception {
        game.playWorld();
        game.pauseWorld();

        JSONArray tape = new JSONArray()
                .put(new JSONObject().put("at", 0).put("action", "right").put("state", "down"))
                .put(new JSONObject().put("at", 5).put("action", "right").put("state", "up"))
                .put(new JSONObject().put("at", 6).put("mouseButton", "left").put("state", "down"));
        String r = exec("run_input_tape", new JSONObject().put("tape", tape).put("maxFrames", 10));

        assertTrue(r.contains("3/3 evento(s)"), r);
        assertTrue(r.contains("10/10 frame(s)"), r);
        assertTrue(r.contains("Input zerado"), r);
        assertFalse(com.ignis.core.Input.isKeyPressed(java.awt.event.KeyEvent.VK_D),
                "resetAll deve soltar a tecla da fita");
    }

    @Test
    void fitaExigePlayERecusaEventosInvalidos() throws Exception {
        JSONArray tape = new JSONArray().put(new JSONObject().put("at", 0).put("action", "right"));
        String r = exec("run_input_tape", new JSONObject().put("tape", tape));
        assertTrue(r.startsWith("Erro"), "sem Play deve recusar: " + r);

        game.playWorld();
        game.pauseWorld();
        JSONArray mixed = new JSONArray()
                .put(new JSONObject().put("at", 0).put("action", "right"))
                .put(new JSONObject().put("at", 1).put("action", "nao_existe"));
        r = exec("run_input_tape", new JSONObject().put("tape", mixed));
        assertTrue(r.contains("1/2 evento(s)"), r);
        assertTrue(r.contains("Ignorados"), r);
    }

    // ------------------------------------------------------------------
    // inject_input durationFrames
    // ------------------------------------------------------------------

    @Test
    void durationFramesSeguraEAvancaSozinho() throws Exception {
        String r = exec("inject_input", new JSONObject().put("action", "jump").put("durationFrames", 3));
        assertTrue(r.startsWith("Erro"), "durationFrames exige Play: " + r);

        game.playWorld();
        game.pauseWorld();
        r = exec("inject_input", new JSONObject().put("action", "jump").put("durationFrames", 3));
        assertTrue(r.contains("3/3 frame(s)"), r);
        assertTrue(r.contains("soltou"), r);
        assertFalse(com.ignis.core.Input.isKeyPressed(java.awt.event.KeyEvent.VK_SPACE),
                "apos o hold a tecla deve estar solta");
    }

    // ------------------------------------------------------------------
    // snapshot_scene / compare_scene_snapshot
    // ------------------------------------------------------------------

    @Test
    void snapshotNomeadoDetectaMudancaDePosicao() throws Exception {
        GameObject hero = new Square();
        hero.setName("Hero");
        game.getEntities().add(hero);

        String r = exec("snapshot_scene", new JSONObject().put("label", "antes"));
        assertTrue(r.contains("'antes'"), r);

        hero.setX(hero.getX() + 50);
        r = exec("compare_scene_snapshot", new JSONObject().put("before", "antes"));
        assertTrue(r.contains("~1"), "deve apontar 1 objeto alterado: " + r);
        assertTrue(r.contains("Hero"), r);

        // Sem mudanca adicional, comparar consigo mesmo e vazio.
        exec("snapshot_scene", new JSONObject().put("label", "depois"));
        r = exec("compare_scene_snapshot",
                new JSONObject().put("before", "depois").put("after", "depois"));
        assertTrue(r.contains("nenhuma mudanca"), r);
    }

    @Test
    void compareComRotuloInexistenteExplicaOsDisponiveis() throws Exception {
        exec("snapshot_scene", new JSONObject().put("label", "unico"));
        String r = exec("compare_scene_snapshot", new JSONObject().put("before", "nao_existe"));
        assertTrue(r.startsWith("Erro"), r);
        assertTrue(r.contains("unico"), "deve listar os rotulos salvos: " + r);
    }

    // ------------------------------------------------------------------
    // get_ui_tree
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // click_ui / move_mouse — clique de UI por coordenada
    // ------------------------------------------------------------------

    @Test
    void clickUiDisparaOnClickDeBotaoPorCoordenada() throws Exception {
        // Botao no canvas global cobrindo (20,20)-(170,60).
        com.ignis.core.ui.UICanvas canvas = game.getUICanvas();
        if (canvas == null) { canvas = new com.ignis.core.ui.UICanvas(); game.setUICanvas(canvas); }
        boolean[] clicado = {false};
        com.ignis.core.ui.UIButton btn = new com.ignis.core.ui.UIButton("OK", 20, 20, 150, 40);
        btn.setName("ok");
        btn.setOnClick(() -> clicado[0] = true);
        canvas.addChild(btn);

        // Sem Play: recusa.
        String r = exec("click_ui", new JSONObject().put("x", 50).put("y", 40));
        assertTrue(r.startsWith("Erro"), r);

        game.playWorld();
        game.pauseWorld();

        // Clique dentro do botao: consome e dispara onClick.
        r = exec("click_ui", new JSONObject().put("x", 50).put("y", 40));
        assertTrue(r.contains("consumido"), r);
        assertTrue(clicado[0], "o onClick do botao deve ter disparado");

        // Clique fora de qualquer widget: nao consome.
        clicado[0] = false;
        r = exec("click_ui", new JSONObject().put("x", 300).put("y", 170));
        assertTrue(r.contains("NAO atingiu"), r);
        assertFalse(clicado[0]);
    }

    @Test
    void moveMouseAtualizaPosicaoDoInput() throws Exception {
        game.playWorld();
        String r = exec("move_mouse", new JSONObject().put("x", 123).put("y", 45));
        assertTrue(r.contains("(123,45)"), r);
        assertEquals(123, com.ignis.core.Input.getMouseX());
        assertEquals(45, com.ignis.core.Input.getMouseY());
    }

    @Test
    void fitaComClickUiDisparaBotao() throws Exception {
        com.ignis.core.ui.UICanvas canvas = game.getUICanvas();
        if (canvas == null) { canvas = new com.ignis.core.ui.UICanvas(); game.setUICanvas(canvas); }
        boolean[] clicado = {false};
        com.ignis.core.ui.UIButton btn = new com.ignis.core.ui.UIButton("Nomear", 10, 10, 120, 40);
        btn.setName("nomear");
        btn.setOnClick(() -> clicado[0] = true);
        canvas.addChild(btn);

        game.playWorld();
        game.pauseWorld();
        JSONArray tape = new JSONArray()
                .put(new JSONObject().put("at", 0).put("clickUi", true).put("x", 40).put("y", 25));
        String r = exec("run_input_tape", new JSONObject().put("tape", tape).put("maxFrames", 2));
        assertTrue(r.contains("1/1 evento"), r);
        assertTrue(clicado[0], "clique de UI na fita deve disparar o botao");
    }

    @Test
    void arvoreDeUiListaWidgetsComBoundsETexto() throws Exception {
        String r = exec("get_ui_tree", new JSONObject());
        assertTrue(r.contains("nenhuma UI"), r);

        exec("ui_create_label", new JSONObject().put("name", "hp").put("text", "HP: 100")
                .put("x", 10).put("y", 20));
        exec("ui_create_button", new JSONObject().put("name", "pausar").put("text", "Pausar"));

        r = exec("get_ui_tree", new JSONObject());
        assertTrue(r.contains("Canvas global de runtime"), r);
        assertTrue(r.contains("hp"), r);
        assertTrue(r.contains("HP: 100"), "deve expor o texto do widget: " + r);
        assertTrue(r.contains("pausar"), r);
        assertTrue(r.contains("[interativo]"), "botao deve aparecer como interativo: " + r);

        String err = exec("get_ui_tree", new JSONObject().put("objectName", "NaoExiste"));
        assertTrue(err.startsWith("Erro"), err);
    }
}
