package com.ignis.mcp;

import com.ignis.core.CanvasComponent;
import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.core.Scene;
import com.ignis.core.Square;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UIComponent;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Autoria de UI PERSISTENTE por CanvasComponent via MCP (P1 fatia 2), headless.
 * Executa os handlers direto (o dispatch FX do {@code call()} é coberto no editor
 * vivo). O teste-chave é o round-trip: UI montada por MCP sobrevive a serializar e
 * recarregar a cena — prova que o trabalho do agente não se perde ao fechar o editor.
 */
class UiPersistentAuthoringTest {

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

    @AfterEach
    void releaseClaims() {
        McpCoordination.get().release("Ana", "objeto:Player");
    }

    private String exec(String tool, JSONObject args) throws Exception {
        return registry.get(tool).handler.execute(args);
    }

    private GameObject spawn(String name) {
        GameObject go = new Square();
        go.setName(name);
        game.getEntities().add(go);
        return go;
    }

    @Test
    void objectNameAutoAnexaCanvasComponentEMontaNoObjetoNaoNoGlobal() throws Exception {
        GameObject player = spawn("Player");

        String r = exec("ui_create_progressbar",
                new JSONObject().put("name", "hp").put("objectName", "Player"));
        assertTrue(r.contains("CanvasComponent anexado a 'Player'"), r);
        assertTrue(r.contains("persiste"), r);

        CanvasComponent cc = player.getComponent(CanvasComponent.class);
        assertNotNull(cc, "componente deve ter sido anexado");
        assertNotNull(cc.getCanvas().findByName("hp"), "widget vai no canvas DO objeto");

        // Nao vazou para o canvas global volatil.
        UICanvas global = game.getUICanvas();
        assertTrue(global == null || global.findByName("hp") == null,
                "o widget persistente nao deve estar no canvas global");
    }

    @Test
    void nomeEhUnicoPorCanvasNaoGlobalmente() throws Exception {
        spawn("Player");
        spawn("Boss");

        assertTrue(exec("ui_create_label", new JSONObject().put("name", "hp").put("text", "P")
                .put("objectName", "Player")).startsWith("Label criado"));
        // Mesmo nome em OUTRO objeto: OK (canvas diferente).
        assertTrue(exec("ui_create_label", new JSONObject().put("name", "hp").put("text", "B")
                .put("objectName", "Boss")).startsWith("Label criado"));
        // Mesmo nome no MESMO canvas: erro.
        assertTrue(exec("ui_create_label", new JSONObject().put("name", "hp").put("text", "X")
                .put("objectName", "Player")).startsWith("Erro"));
    }

    @Test
    void setERemoveRespeitamOCanvasAlvo() throws Exception {
        spawn("Player");
        exec("ui_create_label", new JSONObject().put("name", "hp").put("text", "100")
                .put("objectName", "Player"));

        assertTrue(exec("ui_set_text", new JSONObject().put("name", "hp").put("text", "50")
                .put("objectName", "Player")).contains("atualizado"));

        // dryRun nao remove.
        String dry = exec("ui_remove_element", new JSONObject().put("name", "hp")
                .put("objectName", "Player").put("dryRun", true));
        assertTrue(dry.startsWith("[dryRun]"), dry);
        CanvasComponent cc = game.getEntities().get(0).getComponent(CanvasComponent.class);
        assertNotNull(cc.getCanvas().findByName("hp"), "dryRun nao pode remover");

        assertTrue(exec("ui_remove_element", new JSONObject().put("name", "hp")
                .put("objectName", "Player")).contains("removido"));
        assertNull(cc.getCanvas().findByName("hp"));
    }

    @Test
    void setSemCanvasComponentOrientaEmVezDeAnexar() throws Exception {
        spawn("Player");
        String r = exec("ui_set_text", new JSONObject().put("name", "hp").put("text", "x")
                .put("objectName", "Player"));
        assertTrue(r.startsWith("Erro") && r.contains("CanvasComponent"), r);
    }

    @Test
    void ancoraEEstiloAplicamNoWidgetPersistente() throws Exception {
        spawn("Player");
        exec("ui_create_button", new JSONObject().put("name", "menu").put("text", "Menu")
                .put("objectName", "Player"));

        assertTrue(exec("ui_set_anchor", new JSONObject().put("name", "menu")
                .put("anchorX", 1).put("anchorY", 0).put("objectName", "Player")).contains("Ancora"));
        assertTrue(exec("ui_set_style", new JSONObject().put("name", "menu")
                .put("fontSize", 20).put("borderRadius", 8).put("objectName", "Player")).contains("atualizado"));

        UIComponent el = game.getEntities().get(0).getComponent(CanvasComponent.class)
                .getCanvas().findByName("menu");
        assertEquals(1.0, el.getAnchorX(), 0.001);
        assertEquals(20, el.getFont().getSize());
    }

    @Test
    void estiloRgbaEstadosDeBotaoEBoundsPersistemSemPerderAlpha() throws Exception {
        spawn("Player");
        exec("ui_create_button", new JSONObject().put("name", "menu").put("text", "Menu")
                .put("objectName", "Player"));

        assertTrue(exec("ui_set_bounds", new JSONObject().put("name", "menu")
                .put("x", 24).put("y", 32).put("width", 184).put("height", 56)
                .put("objectName", "Player")).contains("184.0x56.0"));
        assertTrue(exec("ui_set_style", new JSONObject().put("name", "menu")
                .put("backgroundColor", "#07111CB8")
                .put("normalColor", "#152033F2")
                .put("hoverColor", "#23486EFF")
                .put("pressedColor", "#0D1624FF")
                .put("disabledColor", "#15203370")
                .put("objectName", "Player")).contains("atualizado"));

        UIButton button = (UIButton) game.getEntities().get(0).getComponent(CanvasComponent.class)
                .getCanvas().findByName("menu");
        assertEquals(24, button.getX(), 0.001);
        assertEquals(32, button.getY(), 0.001);
        assertEquals(184, button.getWidth(), 0.001);
        assertEquals(56, button.getHeight(), 0.001);
        assertEquals(184, button.getBackgroundColor().getAlpha());
        assertEquals(242, button.getNormalColor().getAlpha());
        assertEquals(112, button.getDisabledColor().getAlpha());

        assertTrue(exec("ui_set_bounds", new JSONObject().put("name", "menu")
                .put("width", 0).put("objectName", "Player")).startsWith("Erro"));

        UIButton roundTrip = UIButton.fromJSON(button.toJSON());
        assertEquals(184, roundTrip.getBackgroundColor().getAlpha());
        assertEquals(242, roundTrip.getNormalColor().getAlpha());
        assertEquals(112, roundTrip.getDisabledColor().getAlpha());
    }

    @Test
    void attachEDetachGerenciamOComponente() throws Exception {
        spawn("Player");
        assertTrue(exec("ui_attach_canvas", new JSONObject().put("objectName", "Player")
                .put("sortingOrder", 5)).contains("anexado"));
        // Anexar de novo: erro.
        assertTrue(exec("ui_attach_canvas", new JSONObject().put("objectName", "Player")).startsWith("Erro"));

        assertTrue(exec("ui_set_canvas_props", new JSONObject().put("objectName", "Player")
                .put("visible", false)).contains("visivel=false"));

        exec("ui_create_label", new JSONObject().put("name", "x").put("text", "y").put("objectName", "Player"));
        String dry = exec("ui_detach_canvas", new JSONObject().put("objectName", "Player").put("dryRun", true));
        assertTrue(dry.contains("1 widget"), dry);
        assertNotNull(game.getEntities().get(0).getComponent(CanvasComponent.class), "dryRun nao remove");

        assertTrue(exec("ui_detach_canvas", new JSONObject().put("objectName", "Player")).contains("removido"));
        assertNull(game.getEntities().get(0).getComponent(CanvasComponent.class));
    }

    @Test
    void widgetsNovosSaoCriaveis() throws Exception {
        spawn("Player");
        assertTrue(exec("ui_create_textfield", new JSONObject().put("name", "nome")
                .put("placeholder", "Digite").put("objectName", "Player")).startsWith("Campo"));
        assertTrue(exec("ui_create_checkbox", new JSONObject().put("name", "flag")
                .put("text", "Ativar").put("checked", true).put("objectName", "Player")).startsWith("Checkbox"));
        assertTrue(exec("ui_create_slider", new JSONObject().put("name", "vol")
                .put("min", 0).put("max", 10).put("value", 5).put("objectName", "Player")).startsWith("Slider"));
        // max <= min recusado.
        assertTrue(exec("ui_create_slider", new JSONObject().put("name", "bad")
                .put("min", 5).put("max", 5).put("objectName", "Player")).startsWith("Erro"));
    }

    @Test
    void uiPersistenteSobreviveAoRoundTripDaCena() throws Exception {
        GameObject player = spawn("Player");
        exec("ui_create_progressbar", new JSONObject().put("name", "hp").put("value", 80)
                .put("maxValue", 100).put("objectName", "Player"));
        exec("ui_create_button", new JSONObject().put("name", "menu").put("text", "Menu")
                .put("actionData", "signal:abrir_menu").put("objectName", "Player"));

        Scene scene = new Scene("s1");
        scene.addEntity(player);
        JSONObject json = scene.toJSON();

        Scene loaded = Scene.fromJSON(json, new Game());
        GameObject back = loaded.getEntities().get(0);
        CanvasComponent cc = back.getComponent(CanvasComponent.class);
        assertNotNull(cc, "CanvasComponent deve sobreviver ao round-trip da cena");
        assertNotNull(cc.getCanvas().findByName("hp"), "barra persistiu");
        UIComponent menu = cc.getCanvas().findByName("menu");
        assertNotNull(menu, "botao persistiu");
        assertEquals("signal:abrir_menu", ((UIButton) menu).getActionData(),
                "actionData declarativo persistiu (callbacks transient nao, actionData sim)");
    }

    @Test
    void actionDataRoundTripNoBotao() {
        UIButton btn = new UIButton("X", 0, 0, 100, 40);
        btn.setActionData("scene:Floresta");
        UIButton back = UIButton.fromJSON(btn.toJSON());
        assertEquals("scene:Floresta", back.getActionData());
    }

    @Test
    void claimDeObjetoBloqueiaUiPersistenteDeOutroAgente() {
        McpCoordination.get().claim("Ana", "objeto:Player");

        // Outro agente mirando o MESMO objeto: barrado.
        String conflict = registry.coordConflict("ui_create_label",
                new JSONObject().put("name", "hp").put("objectName", "Player"), "Bruno");
        assertNotNull(conflict);
        assertTrue(conflict.startsWith("CONFLITO"), conflict);

        // Canvas global (sem objectName): sem claim, passa livre.
        assertNull(registry.coordConflict("ui_create_label",
                new JSONObject().put("name", "hp"), "Bruno"));

        // O dono do claim nao e barrado.
        assertNull(registry.coordConflict("ui_create_label",
                new JSONObject().put("name", "hp").put("objectName", "Player"), "Ana"));
    }
}
