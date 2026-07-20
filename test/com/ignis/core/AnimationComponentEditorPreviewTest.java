package com.ignis.core;

import com.ignis.animation.AnimationFrame;
import com.ignis.animation.SpriteAnimation;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressão: sprites dirigidos por {@link AnimationComponent} devem aparecer no
 * viewport do editor SEM depender do primeiro Play (bug "assets só carregam
 * depois do play"). O preview de edição avança a máquina de estados no modo
 * EDITING e aplica o frame do estado padrão.
 *
 * <p>Garante também que o preview é NÃO-destrutivo: escreve em
 * {@code previewTexture} (transient, não serializada) e nunca sobrescreve a
 * textura autoral {@code texture} que é persistida no .ignis — abrir e salvar o
 * projeto sem dar Play não pode trocar o sprite do objeto por um frame de
 * animação. O caminho de Play continua dirigindo {@code texture} como antes.</p>
 */
class AnimationComponentEditorPreviewTest {

    private File dir;

    @BeforeEach
    void setup() throws Exception {
        dir = Files.createTempDirectory("ignis-anim-preview").toFile();
        AssetResolver.setProjectFolder(dir);
        writeControllerAndAnimations();
    }

    @AfterEach
    void teardown() {
        AssetResolver.setProjectFolder(null);
        AssetResolver.clearImageCache();
    }

    /** Escreve um controller com estado padrão "idle" e sua animação. */
    private void writeControllerAndAnimations() throws Exception {
        File animDir = new File(dir, "assets/animations");
        animDir.mkdirs();

        SpriteAnimation idle = new SpriteAnimation("idle");
        idle.setLoop(true);
        idle.addFrame(new AnimationFrame("assets/sprites/idle_1.png", 0.5));
        idle.addFrame(new AnimationFrame("assets/sprites/idle_2.png", 0.5));
        Files.write(new File(animDir, "idle.anim.json").toPath(),
                idle.toJSON().toString(2).getBytes(StandardCharsets.UTF_8));

        JSONObject controller = new JSONObject();
        controller.put("name", "TestController");
        controller.put("defaultState", "idle");
        controller.put("states", new JSONArray().put(new JSONObject()
                .put("name", "idle")
                .put("animation", "assets/animations/idle.anim.json")
                .put("speed", 1.0)
                .put("loop", true)));
        Files.write(new File(animDir, "test.controller.json").toPath(),
                controller.toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private GameObject buildAnimatedObject() {
        GameObject go = new GameObject("Player", null, 0, 0, 32, 32);
        go.addComponent(new SpriteComponent());
        AnimationComponent anim = new AnimationComponent();
        go.addComponent(anim);
        anim.setAnimationController("assets/animations/test.controller.json");
        return go;
    }

    @Test
    void previewDeEdicaoMostraFrameSemDarPlay() {
        GameObject go = buildAnimatedObject();
        AnimationComponent anim = go.getComponent(AnimationComponent.class);
        SpriteComponent sprite = go.getComponent(SpriteComponent.class);

        // Antes do preview: nada carregado (era o estado "invisível até o Play").
        assertNull(sprite.getPreviewTexture(), "sem preview antes de qualquer tick de edição");
        assertNull(sprite.getTexture(), "sem textura autoral neste objeto");

        anim.editorPreview(0.1);

        assertNotNull(sprite.getPreviewTexture(), "o preview do editor deve popular a textura de preview");
        assertTrue(sprite.getPreviewTexture().getPath().contains("idle"),
                "preview deve mostrar o frame do estado padrão (idle)");
    }

    @Test
    void previewDeEdicaoNaoSobrescreveTexturaAutoralSerializada() {
        GameObject go = buildAnimatedObject();
        AnimationComponent anim = go.getComponent(AnimationComponent.class);
        SpriteComponent sprite = go.getComponent(SpriteComponent.class);

        for (int i = 0; i < 10; i++) {
            anim.editorPreview(0.1);
        }

        // A textura AUTORAL (a única serializada) permanece intacta — salvar o
        // projeto após abrir não troca o sprite do objeto por um frame de animação.
        assertNull(sprite.getTexture(),
                "o preview de edição não pode escrever na textura autoral serializada");
    }

    @Test
    void previewAcompanhaOEditorViaGamePreviewEditorAnimations() {
        Game game = new Game();
        game.setSuppressAwtRepaint(true);
        GameObject go = buildAnimatedObject();
        go.setGame(game);
        game.addEntity(go);
        SpriteComponent sprite = go.getComponent(SpriteComponent.class);

        // Em EDITING, o hook de preview do editor deve dirigir as animações.
        game.previewEditorAnimations(0.1);

        assertNotNull(sprite.getPreviewTexture(),
                "previewEditorAnimations deve aplicar o frame no modo de edição");
        assertNull(sprite.getTexture(), "sem sobrescrever a textura autoral");
    }

    @Test
    void playContinuaDirigindoATexturaSerializada() {
        GameObject go = buildAnimatedObject();
        AnimationComponent anim = go.getComponent(AnimationComponent.class);
        SpriteComponent sprite = go.getComponent(SpriteComponent.class);

        // Caminho de runtime inalterado: start() carrega o controller, update()
        // resolve o frame e escreve em 'texture' (como antes desta correção).
        anim.start();
        anim.update(0.1f);

        assertNotNull(sprite.getTexture(), "no Play, a textura serializada deve ser dirigida pela animação");
        assertTrue(sprite.getTexture().getPath().contains("idle"),
                "no Play, o frame do estado padrão deve ser aplicado");
    }
}
