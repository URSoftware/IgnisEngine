package com.ignis.editor.fx;

import com.ignis.core.Camera;
import com.ignis.core.ColliderComponent;
import com.ignis.core.GameObject;
import com.ignis.core.HealthComponent;
import com.ignis.core.RigidbodyComponent;
import com.ignis.core.SpriteComponent;
import com.ignis.core.Texture2D;

import java.io.File;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Construtores das secoes do Inspector do editor (Fase F -- quebra do
 * {@link IgnisEditorApp}, a maior god class). Cada metodo monta a UI de inspecao de
 * um tipo de entidade/componente (sprite, collider, rigidbody, health, camera,
 * camada de fundo, particulas, tilemap, texto, luz, hierarquia, scripts) mais os
 * helpers de linha compartilhados (sectionTitle, doubleRow, intRow, checkRow,
 * labeledInspectorRow).
 *
 * <p>Recebe o {@link IgnisEditorApp} e le o estado (objeto selecionado, game) e os
 * callbacks (markProjectDirty, rebuildInspectorExtras, undo) dele. Mesmo pacote,
 * entao usa membros package-private sem API publica nova. O dispatch por tipo
 * (que secao construir) continua no editor, em {@code rebuildInspectorExtras}.</p>
 */
final class InspectorSectionBuilder {

    private final IgnisEditorApp app;

    InspectorSectionBuilder(IgnisEditorApp app) {
        this.app = app;
    }

    private Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("panel-title");
        VBox.setMargin(l, new Insets(8, 0, 2, 0));
        return l;
    }

    // Cor (so para objetos que expoem getColor/setColor via reflexao) + Sprite.
    javafx.scene.Node buildSpriteComponentSection(GameObject go, SpriteComponent spriteComp) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("SpriteComponent"));

        // 1. Textura / Sprite
        Label spriteVal = new Label(app.spriteLabel(spriteComp.getTexture() != null ? spriteComp.getTexture().getPath() : null));
        spriteVal.getStyleClass().add("field-label");
        spriteVal.setWrapText(true);
        Button pick = new Button("Escolher…");
        pick.setOnAction(e -> {
            File f = app.chooseSpriteFile();
            if (f != null) {
                String path = app.importSpriteToProject(f);
                spriteComp.setTexture(new Texture2D(path));
                spriteVal.setText(app.spriteLabel(path));
                app.markProjectDirty();
                // update hierarchy when texture changes
                app.refreshHierarchy();
            }
        });
        Button clear = new Button("Limpar");
        clear.setOnAction(e -> {
            spriteComp.setTexture(null);
            spriteVal.setText(app.spriteLabel(null));
            app.markProjectDirty();
            app.refreshHierarchy();
        });
        Label spriteLbl = new Label("Sprite");
        spriteLbl.getStyleClass().add("field-label");
        sec.getChildren().add(new VBox(4, spriteLbl, spriteVal, new HBox(6, pick, clear)));

        // 2. Tipo de Forma (shapeType)
        ComboBox<String> shapeBox = new ComboBox<>();
        shapeBox.getItems().addAll("None", "Square", "Circle", "Triangle", "Star", "Pentagon");
        shapeBox.setValue(spriteComp.getShapeType());
        shapeBox.setOnAction(e -> {
            spriteComp.setShapeType(shapeBox.getValue());
            app.markProjectDirty();
        });
        sec.getChildren().add(labeledInspectorRow("Forma", shapeBox));

        // 3. Cor / Tint
        ColorPicker tintPicker = new ColorPicker();
        if (spriteComp.getTint() != null) {
            tintPicker.setValue(app.awtToFx(spriteComp.getTint()));
        }
        tintPicker.setOnAction(e -> {
            spriteComp.setTint(app.fxToAwt(tintPicker.getValue()));
            app.markProjectDirty();
        });
        sec.getChildren().add(labeledInspectorRow("Cor / Tint", tintPicker));

        // 4. FlipX e FlipY
        CheckBox fxCheck = new CheckBox("Flip X");
        fxCheck.setSelected(spriteComp.isFlipX());
        fxCheck.setOnAction(e -> {
            spriteComp.setFlipX(fxCheck.isSelected());
            app.markProjectDirty();
        });

        CheckBox fyCheck = new CheckBox("Flip Y");
        fyCheck.setSelected(spriteComp.isFlipY());
        fyCheck.setOnAction(e -> {
            spriteComp.setFlipY(fyCheck.isSelected());
            app.markProjectDirty();
        });

        sec.getChildren().add(new HBox(12, fxCheck, fyCheck));

        return sec;
    }

    // Seccao de migracao do collider legado (GameObject.colliderType/collisionMode,
    // aposentado no item 8c). So aparece para objetos que ainda usam o par legado e
    // nao tem ColliderComponent. Converte para um ColliderComponent equivalente.
    @SuppressWarnings("deprecation") // le a API legada de collider para migra-la
    javafx.scene.Node buildLegacyColliderMigrationSection(GameObject go) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Collider (legado)"));

        Label info = new Label("Este objeto usa o sistema de collider antigo ("
                + go.getColliderType() + " / " + go.getCollisionMode()
                + "). Migre para o ColliderComponent para editar a hitbox no viewport.");
        info.setWrapText(true);
        info.setStyle("-fx-text-fill: #c8a45a; -fx-font-style: italic;");

        Button migrate = new Button("Migrar para ColliderComponent");
        migrate.setOnAction(e -> {
            ColliderComponent cc = app.convertLegacyCollider(go);
            go.addComponent(cc);
            app.selectedComponentName = null;
            app.markProjectDirty();
            app.refreshHierarchy();
            app.rebuildInspectorExtras(go);
            app.setStatus("Collider migrado para ColliderComponent.");
        });

        sec.getChildren().addAll(info, migrate);
        return sec;
    }

    // Seção Tags & Camadas: tag livre (busca/gameplay) + camada nomeada (agrupamento).
    javafx.scene.Node buildTagsLayersSection(GameObject go) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Tags & Camadas"));

        TextField tagField = new TextField(go.getTag());
        tagField.setPromptText("(sem tag)");
        tagField.textProperty().addListener((o, a, b) -> { go.setTag(b); app.markProjectDirty(); });

        ComboBox<String> layerBox = new ComboBox<>();
        layerBox.setEditable(true);
        java.util.LinkedHashSet<String> layers = new java.util.LinkedHashSet<>(java.util.Arrays.asList(
                "Default", "Background", "Foreground", "Player", "Enemy", "UI"));
        // Inclui camadas ja usadas na cena para reaproveitar.
        for (GameObject e : app.game.getEntities()) {
            if (e.getLayer() != null && !e.getLayer().isEmpty()) layers.add(e.getLayer());
        }
        layerBox.getItems().addAll(layers);
        layerBox.setValue(go.getLayer());
        layerBox.setMaxWidth(Double.MAX_VALUE);
        Runnable applyLayer = () -> {
            String v = layerBox.getValue();
            go.setLayer(v);
            app.markProjectDirty();
        };
        layerBox.setOnAction(e -> applyLayer.run());
        layerBox.getEditor().focusedProperty().addListener((o, a, focused) -> { if (!focused) applyLayer.run(); });

        sec.getChildren().addAll(labeledInspectorRow("Tag", tagField),
                labeledInspectorRow("Camada", layerBox));
        return sec;
    }

    javafx.scene.Node buildCameraSection(com.ignis.core.Camera cam) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Camera"));

        TextField zoom = new TextField(String.valueOf(cam.getZoom()));
        Runnable applyZoom = () -> { cam.setZoom(app.parseD(zoom.getText(), cam.getZoom())); app.markProjectDirty(); };
        zoom.setOnAction(e -> applyZoom.run());
        zoom.focusedProperty().addListener((o, a, focused) -> { if (!focused) applyZoom.run(); });

        CheckBox active = new CheckBox("Camera ativa");
        active.setSelected(cam.isActiveCamera());
        active.selectedProperty().addListener((o, a, b) -> { cam.setActive(b); app.markProjectDirty(); });

        sec.getChildren().addAll(labeledInspectorRow("Zoom", zoom), active);
        return sec;
    }

    private HBox doubleRow(String label, java.util.function.DoubleSupplier get,
                           java.util.function.DoubleConsumer set) {
        TextField tf = new TextField(String.valueOf(get.getAsDouble()));
        Runnable apply = () -> { set.accept(app.parseD(tf.getText(), get.getAsDouble())); app.markProjectDirty(); };
        tf.setOnAction(e -> apply.run());
        tf.focusedProperty().addListener((o, a, focused) -> { if (!focused) apply.run(); });
        return labeledInspectorRow(label, tf);
    }

    private HBox intRow(String label, java.util.function.IntSupplier get,
                        java.util.function.IntConsumer set) {
        TextField tf = new TextField(String.valueOf(get.getAsInt()));
        Runnable apply = () -> { set.accept(app.parseI(tf.getText(), get.getAsInt())); app.markProjectDirty(); };
        tf.setOnAction(e -> apply.run());
        tf.focusedProperty().addListener((o, a, focused) -> { if (!focused) apply.run(); });
        return labeledInspectorRow(label, tf);
    }

    private CheckBox checkRow(String label, boolean initial, java.util.function.Consumer<Boolean> set) {
        CheckBox cb = new CheckBox(label);
        cb.setSelected(initial);
        cb.selectedProperty().addListener((o, a, b) -> { set.accept(b); app.markProjectDirty(); });
        return cb;
    }

    /** Secao do Inspector para camadas de fundo com parallax (Fase C). */
    javafx.scene.Node buildBackgroundLayerSection(com.ignis.core.BackgroundLayer bg) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Camada de Fundo (Parallax)"));

        TextField path = new TextField(bg.getImagePath() != null ? bg.getImagePath() : "");
        path.setPromptText("assets/sprites/ceu.png");
        Runnable applyPath = () -> { bg.setImagePath(path.getText().trim()); app.markProjectDirty(); };
        path.setOnAction(e -> applyPath.run());
        path.focusedProperty().addListener((o, a, f) -> { if (!f) applyPath.run(); });

        sec.getChildren().addAll(
                labeledInspectorRow("Sprite", path),
                doubleRow("Parallax X", bg::getParallaxX, bg::setParallaxX),
                doubleRow("Parallax Y", bg::getParallaxY, bg::setParallaxY),
                checkRow("Repetir em X", bg.isRepeatX(), bg::setRepeatX),
                checkRow("Repetir em Y", bg.isRepeatY(), bg::setRepeatY));
        Label dica = new Label("0 = fixo no mundo · 1 = preso à câmera");
        dica.getStyleClass().add("toolbar-label");
        sec.getChildren().add(dica);
        return sec;
    }

    /** Secao do Inspector para emissores de particulas (Fase C). */
    javafx.scene.Node buildParticleEmitterSection(com.ignis.core.ParticleEmitter pe) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Emissor de Partículas"));

        Button burst = new Button("Disparar rajada (50)");
        burst.setOnAction(e -> pe.burst(50));

        sec.getChildren().addAll(
                checkRow("Emitindo", pe.isEmitting(), pe::setEmitting),
                doubleRow("Taxa (part/s)", pe::getEmissionRate, pe::setEmissionRate),
                intRow("Máx. partículas", pe::getMaxParticles, pe::setMaxParticles),
                doubleRow("Vida (s)", pe::getLifetime, pe::setLifetime),
                doubleRow("Velocidade X", pe::getVelX, pe::setVelX),
                doubleRow("Velocidade Y", pe::getVelY, pe::setVelY),
                doubleRow("Gravidade Y", pe::getGravityY, pe::setGravityY),
                doubleRow("Tamanho inicial", pe::getSizeStart, pe::setSizeStart),
                doubleRow("Tamanho final", pe::getSizeEnd, pe::setSizeEnd),
                burst);
        return sec;
    }

    /** Secao do Inspector para tilemaps (Fase C). Pintura fica no viewport/MCP. */
    javafx.scene.Node buildTilemapSection(com.ignis.core.TilemapObject tm) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Tilemap"));

        Label info = new Label(String.format("%d x %d células de %dx%d px · %d camada(s)",
                tm.getCols(), tm.getRows(), tm.getTileW(), tm.getTileH(), tm.getLayerCount()));
        info.getStyleClass().add("toolbar-label");

        TextField tileset = new TextField(tm.getTilesetPath() != null ? tm.getTilesetPath() : "");
        tileset.setPromptText("assets/tilesets/dungeon.png");
        Runnable applyTs = () -> { tm.setTilesetPath(tileset.getText().trim()); app.markProjectDirty(); };
        tileset.setOnAction(e -> applyTs.run());
        tileset.focusedProperty().addListener((o, a, f) -> { if (!f) applyTs.run(); });

        Button addLayer = new Button("Adicionar camada");
        addLayer.setOnAction(e -> {
            tm.addLayer();
            app.markProjectDirty();
            app.rebuildInspectorExtras(app.selected);
        });
        Button clearLayer = new Button("Limpar camada 0");
        clearLayer.setOnAction(e -> {
            tm.fillTiles(0, 0, 0, tm.getCols() - 1, tm.getRows() - 1, com.ignis.core.TilemapObject.EMPTY);
            app.markProjectDirty();
        });

        // Pintura no viewport: escolhe o indice do tile e a camada, e ativa a
        // ferramenta TILE_PAINT sobre ESTE tilemap. Ctrl+clique/arraste apaga.
        TextField tileIdx = new TextField("0");
        TextField layerIdx = new TextField("0");
        ToggleButton paint = new ToggleButton("Pintar Tiles");
        paint.setTooltip(new Tooltip("Clique/arraste no viewport para pintar; Ctrl apaga"));
        paint.selectedProperty().addListener((o, a, on) -> {
            if (on) {
                app.game.setActiveTilemap(tm);
                app.game.setActiveTileIndex(app.parseI(tileIdx.getText(), 0));
                app.game.setActiveTileLayer(app.parseI(layerIdx.getText(), 0));
                app.game.setCurrentTool(com.ignis.core.Game.ToolType.TILE_PAINT);
                app.setStatus("Pintando tiles em " + tm.getName() + " — Ctrl apaga. Escolha 'Mover' para sair.");
            } else if (app.game.getCurrentTool() == com.ignis.core.Game.ToolType.TILE_PAINT) {
                app.game.setCurrentTool(com.ignis.core.Game.ToolType.MOVE);
            }
        });
        tileIdx.textProperty().addListener((o, a, b) -> app.game.setActiveTileIndex(app.parseI(b, 0)));
        layerIdx.textProperty().addListener((o, a, b) -> app.game.setActiveTileLayer(app.parseI(b, 0)));

        sec.getChildren().addAll(labeledInspectorRow("Tileset", tileset), info,
                new HBox(6, addLayer, clearLayer),
                labeledInspectorRow("Tile a pintar", tileIdx),
                labeledInspectorRow("Camada de pintura", layerIdx),
                paint);
        return sec;
    }

    /** Secao do Inspector para TilemapRendererComponent nativo. */
    javafx.scene.Node buildTilemapRendererComponentSection(GameObject go, com.ignis.core.TilemapRendererComponent tmComp) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("TilemapRendererComponent"));

        Label info = new Label(String.format("%d x %d células de %dx%d px · %d camada(s)",
                tmComp.getCols(), tmComp.getRows(), tmComp.getTileW(), tmComp.getTileH(), tmComp.getLayerCount()));
        info.getStyleClass().add("toolbar-label");

        TextField tileset = new TextField(tmComp.getTilesetPath() != null ? tmComp.getTilesetPath() : "");
        tileset.setPromptText("assets/tilesets/dungeon.png");
        Runnable applyTs = () -> { tmComp.setTilesetPath(tileset.getText().trim()); app.markProjectDirty(); };
        tileset.setOnAction(e -> applyTs.run());
        tileset.focusedProperty().addListener((o, a, f) -> { if (!f) applyTs.run(); });

        Button addLayer = new Button("Adicionar camada");
        addLayer.setOnAction(e -> {
            tmComp.addLayer();
            app.markProjectDirty();
            app.rebuildInspectorExtras(app.selected);
        });
        Button clearLayer = new Button("Limpar camada 0");
        clearLayer.setOnAction(e -> {
            tmComp.clearLayer(0);
            app.markProjectDirty();
        });

        // Botão para gerar colisores compostos por Greedy Meshing
        Button greedyBtn = new Button("Gerar Colisores Compostos (Greedy Meshing)");
        greedyBtn.setTooltip(new Tooltip("Funde blocos sólidos adjacentes da Camada 0 em colisores retangulares consolidados"));
        greedyBtn.getStyleClass().add("btn-primary");
        greedyBtn.setOnAction(e -> {
            java.util.List<com.ignis.core.ColliderComponent> colliders = tmComp.generateGreedyColliders(0);
            if (!colliders.isEmpty()) {
                for (com.ignis.core.ColliderComponent cc : colliders) {
                    go.addComponent(cc);
                }
                app.markProjectDirty();
                app.rebuildInspectorExtras(go);
                app.setStatus(colliders.size() + " colisores compostos gerados por Greedy Meshing.");
            } else {
                app.setStatus("Nenhum bloco sólido encontrado na Camada 0 para gerar colisores.");
            }
        });

        sec.getChildren().addAll(labeledInspectorRow("Tileset", tileset), info,
                new HBox(6, addLayer, clearLayer),
                greedyBtn);
        return sec;
    }

    /** Secao do Inspector para texto no mundo (Fase D, item 3.9). */
    javafx.scene.Node buildTextObjectSection(com.ignis.core.TextObject txt) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Texto no Mundo"));

        // Conteudo multilinha (\n cria varias linhas). Aplica ao perder foco/Enter+Ctrl.
        javafx.scene.control.TextArea content = new javafx.scene.control.TextArea(txt.getText());
        content.setPrefRowCount(2);
        content.setWrapText(true);
        Runnable applyText = () -> { txt.setText(content.getText()); app.markProjectDirty(); };
        content.focusedProperty().addListener((o, a, f) -> { if (!f) applyText.run(); });

        ColorPicker colorPicker = new ColorPicker(app.awtToFx(txt.getColor()));
        colorPicker.setOnAction(e -> { txt.setColor(app.fxToAwt(colorPicker.getValue())); app.markProjectDirty(); });

        ComboBox<com.ignis.core.TextObject.TextAlign> align = new ComboBox<>();
        align.getItems().setAll(com.ignis.core.TextObject.TextAlign.values());
        align.setValue(txt.getAlign());
        align.setOnAction(e -> { txt.setAlign(align.getValue()); app.markProjectDirty(); });

        sec.getChildren().addAll(
                labeledInspectorRow("Texto", content),
                intRow("Tamanho (px)", txt::getFontSize, txt::setFontSize),
                labeledInspectorRow("Cor", colorPicker),
                labeledInspectorRow("Alinhamento", align),
                checkRow("Negrito", txt.isBold(), txt::setBold),
                checkRow("Itálico", txt.isItalic(), txt::setItalic));

        TextField family = new TextField(txt.getFontFamily());
        family.setPromptText("SansSerif / Serif / Monospaced");
        Runnable applyFamily = () -> { txt.setFontFamily(family.getText().trim()); app.markProjectDirty(); };
        family.setOnAction(e -> applyFamily.run());
        family.focusedProperty().addListener((o, a, f) -> { if (!f) applyFamily.run(); });
        sec.getChildren().add(labeledInspectorRow("Fonte", family));
        return sec;
    }

    /** Secao do Inspector para luzes 2D (Fase D, item 3.11). */
    javafx.scene.Node buildLightObjectSection(com.ignis.core.LightObject light) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Luz 2D"));

        ColorPicker colorPicker = new ColorPicker(app.awtToFx(light.getLightColor()));
        colorPicker.setOnAction(e -> { light.setLightColor(app.fxToAwt(colorPicker.getValue())); app.markProjectDirty(); });

        sec.getChildren().addAll(
                labeledInspectorRow("Cor", colorPicker),
                doubleRow("Raio (px)", light::getRadius, light::setRadius),
                doubleRow("Intensidade (0-1)", light::getIntensity, light::setIntensity));

        // Luz ambiente da cena (global): sem ela as luzes nao tem efeito visivel.
        sec.getChildren().add(sectionTitle("Luz Ambiente da Cena"));
        java.awt.Color amb = app.game.getAmbientLight();
        ColorPicker ambPicker = new ColorPicker(amb != null ? app.awtToFx(amb) : javafx.scene.paint.Color.rgb(5, 5, 16, 0.88));
        ambPicker.setOnAction(e -> { app.game.setAmbientLight(app.fxToAwt(ambPicker.getValue())); app.markProjectDirty(); });
        Button ambOff = new Button("Desligar luz ambiente");
        ambOff.setOnAction(e -> { app.game.setAmbientLight(null); app.markProjectDirty(); });
        Label dica = new Label("O alpha da cor ambiente = intensidade da escuridão.");
        dica.getStyleClass().add("toolbar-label");
        sec.getChildren().addAll(labeledInspectorRow("Ambiente", ambPicker), ambOff, dica);
        return sec;
    }

    /** Secao do Inspector com o vinculo pai-filho do objeto (Fase C). */
    javafx.scene.Node buildHierarchySection(GameObject go) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Hierarquia"));
        GameObject parent = go.getParent();
        Label info = new Label(parent != null ? "Pai: " + parent.getName() : "Sem pai (raiz)");
        info.getStyleClass().add("toolbar-label");
        sec.getChildren().add(info);
        if (parent != null) {
            Button clear = new Button("Remover pai");
            clear.setOnAction(e -> {
                go.clearParent();
                app.markProjectDirty();
                app.rebuildInspectorExtras(go);
            });
            sec.getChildren().add(clear);
        }
        return sec;
    }

    javafx.scene.Node buildScriptsSection(GameObject go) {
        VBox sec = new VBox(6);
        sec.getChildren().add(sectionTitle("Componentes / Scripts"));

        ListView<String> list = new ListView<>();
        java.util.List<String> listItems = new java.util.ArrayList<>();
        if (go.getComponent(SpriteComponent.class) != null) {
            listItems.add("SpriteComponent");
        }
        if (go.getComponent(ColliderComponent.class) != null) {
            listItems.add("ColliderComponent");
        }
        if (go.getComponent(HealthComponent.class) != null) {
            listItems.add("HealthComponent");
        }
        listItems.addAll(go.getScriptNames());
        list.getItems().setAll(listItems);
        list.setPrefHeight(90);

        Button attach = new Button("Adicionar Componente…");
        attach.setOnAction(e -> { app.openAddComponentDialog(go); });
        
        Button remove = new Button("Remover");
        remove.setOnAction(e -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            // Componente nativo: remove por referencia, com undo/redo.
            com.ignis.core.Component comp = null;
            if (sel.equals("SpriteComponent")) comp = go.getComponent(SpriteComponent.class);
            else if (sel.equals("ColliderComponent")) comp = go.getComponent(ColliderComponent.class);
            else if (sel.equals("HealthComponent")) comp = go.getComponent(HealthComponent.class);

            if (comp != null) {
                final com.ignis.core.Component removed = comp;
                final String label = sel;
                go.removeComponent(removed);
                app.undoManager.push("Remover " + label,
                        () -> { go.addComponent(removed); app.afterComponentChange(go); },
                        () -> { go.removeComponent(removed); app.afterComponentChange(go); });
            } else {
                // Script anexado (por nome): captura a instancia viva, se houver.
                final String scriptName = sel;
                com.ignis.core.IgnisScript found = null;
                for (com.ignis.core.IgnisScript s : go.getScripts()) {
                    if (s.getScriptName().equals(scriptName)) { found = s; break; }
                }
                final com.ignis.core.IgnisScript inst = found;
                go.removeScriptByName(scriptName);
                app.undoManager.push("Remover " + scriptName,
                        () -> {
                            if (inst != null) go.addComponent(inst);
                            if (!go.getScriptNames().contains(scriptName)) go.getScriptNames().add(scriptName);
                            app.afterComponentChange(go);
                        },
                        () -> {
                            if (inst != null) go.removeComponent(inst);
                            go.removeScriptByName(scriptName);
                            app.afterComponentChange(go);
                        });
            }
            app.afterComponentChange(go);
        });
        
        Button open = new Button("Abrir");
        open.setOnAction(e -> {
            String sel = list.getSelectionModel().getSelectedItem();
            if (sel != null && !sel.equals("SpriteComponent") && !sel.equals("ColliderComponent") && !sel.equals("HealthComponent")) {
                app.openScriptByName(sel);
            }
        });

        // Habilita/Desabilita o botao Abrir dependendo do item selecionado
        list.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            open.setDisable(newV == null || newV.equals("SpriteComponent") || newV.equals("ColliderComponent") || newV.equals("HealthComponent"));
        });
        String activeSel = list.getSelectionModel().getSelectedItem();
        open.setDisable(activeSel == null || "SpriteComponent".equals(activeSel) || "ColliderComponent".equals(activeSel) || "HealthComponent".equals(activeSel));

        sec.getChildren().addAll(list, new HBox(6, attach, remove, open));

        // Renderizar painel de variaveis para cada script instanciado
        for (com.ignis.core.IgnisScript script : go.getScripts()) {
            javafx.scene.Node varsNode = app.createScriptVariablesNode(script);
            if (varsNode != null) {
                sec.getChildren().add(varsNode);
            }
        }

        return sec;
    }

    javafx.scene.Node buildColliderComponentSection(GameObject go, ColliderComponent comp) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionTitle("Collider Component"));

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        javafx.scene.layout.ColumnConstraints labelCol = new javafx.scene.layout.ColumnConstraints();
        labelCol.setMinWidth(90);
        javafx.scene.layout.ColumnConstraints fieldCol = new javafx.scene.layout.ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        int r = 0;

        // Campos de geometria (mostrados conforme a forma). Referencias finais para
        // o handler da forma poder alternar a visibilidade sem reconstruir a secao.
        final TextField widthField = new TextField(app.fmt(comp.effectiveWidth()));
        final TextField heightField = new TextField(app.fmt(comp.effectiveHeight()));
        final TextField radiusField = new TextField(app.fmt(comp.effectiveRadius()));
        final Label widthLbl = new Label("Largura");
        final Label heightLbl = new Label("Altura");
        final Label radiusLbl = new Label("Raio");

        // Shape type ComboBox
        javafx.scene.control.ComboBox<String> shapeCombo = new javafx.scene.control.ComboBox<>();
        shapeCombo.getItems().addAll("Box", "Sphere", "Capsule");
        shapeCombo.setValue(comp.getShape());
        shapeCombo.setOnAction(e -> {
            comp.setShape(shapeCombo.getValue());
            app.applyColliderShapeVisibility(comp.getShape(), widthLbl, widthField,
                    heightLbl, heightField, radiusLbl, radiusField);
            app.markProjectDirty();
        });
        grid.add(new Label("Forma"), 0, r);
        grid.add(shapeCombo, 1, r++);

        // Habilitado
        CheckBox enabledCheck = new CheckBox("Habilitado");
        enabledCheck.setSelected(comp.isEnabled());
        enabledCheck.selectedProperty().addListener((o, a, b) -> {
            comp.setEnabled(b);
            app.markProjectDirty();
        });
        grid.add(enabledCheck, 1, r++);

        // Largura (Box/Capsule)
        widthField.textProperty().addListener((o, a, b) -> {
            try { comp.setWidth(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(widthLbl, 0, r);
        grid.add(widthField, 1, r++);

        // Altura (Box/Capsule)
        heightField.textProperty().addListener((o, a, b) -> {
            try { comp.setHeight(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(heightLbl, 0, r);
        grid.add(heightField, 1, r++);

        // Raio (Sphere)
        radiusField.textProperty().addListener((o, a, b) -> {
            try { comp.setRadius(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(radiusLbl, 0, r);
        grid.add(radiusField, 1, r++);

        // Offset X
        TextField offsetXField = new TextField(app.fmt(comp.getOffsetX()));
        offsetXField.textProperty().addListener((o, a, b) -> {
            try { comp.setOffsetX(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Offset X"), 0, r);
        grid.add(offsetXField, 1, r++);

        // Offset Y
        TextField offsetYField = new TextField(app.fmt(comp.getOffsetY()));
        offsetYField.textProperty().addListener((o, a, b) -> {
            try { comp.setOffsetY(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Offset Y"), 0, r);
        grid.add(offsetYField, 1, r++);

        // Friction TextField
        TextField frictionField = new TextField(app.fmt(comp.getFriction()));
        frictionField.textProperty().addListener((o, a, b) -> {
            try {
                comp.setFriction(Double.parseDouble(b));
                app.markProjectDirty();
            } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Fricção"), 0, r);
        grid.add(frictionField, 1, r++);

        // Bounciness TextField
        TextField bounceField = new TextField(app.fmt(comp.getBounciness()));
        bounceField.textProperty().addListener((o, a, b) -> {
            try {
                comp.setBounciness(Double.parseDouble(b));
                app.markProjectDirty();
            } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Elasticidade"), 0, r);
        grid.add(bounceField, 1, r++);

        // IsTrigger CheckBox
        CheckBox triggerCheck = new CheckBox("Is Trigger");
        triggerCheck.setSelected(comp.isTrigger());
        triggerCheck.selectedProperty().addListener((o, a, b) -> {
            comp.setTrigger(b);
            app.markProjectDirty();
        });
        grid.add(triggerCheck, 1, r++);

        // Collision Layer TextField
        TextField layerField = new TextField(comp.getCollisionLayer());
        layerField.textProperty().addListener((o, a, b) -> {
            comp.setCollisionLayer(b);
            app.markProjectDirty();
        });
        grid.add(new Label("Layer"), 0, r);
        grid.add(layerField, 1, r++);

        app.applyColliderShapeVisibility(comp.getShape(), widthLbl, widthField,
                heightLbl, heightField, radiusLbl, radiusField);

        box.getChildren().add(grid);
        return box;
    }

    javafx.scene.Node buildRigidbodyComponentSection(GameObject go, RigidbodyComponent comp) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionTitle("Rigidbody Component"));

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);

        javafx.scene.layout.ColumnConstraints labelCol = new javafx.scene.layout.ColumnConstraints();
        labelCol.setMinWidth(100);
        javafx.scene.layout.ColumnConstraints fieldCol = new javafx.scene.layout.ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        int row = 0;

        // Velocidade X
        TextField velXField = new TextField(String.format(java.util.Locale.ROOT, "%.2f", comp.getVelocityX()));
        velXField.textProperty().addListener((o, a, b) -> {
            try { comp.setVelocityX(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Velocidade X"), 0, row);
        grid.add(velXField, 1, row); row++;

        // Velocidade Y
        TextField velYField = new TextField(String.format(java.util.Locale.ROOT, "%.2f", comp.getVelocityY()));
        velYField.textProperty().addListener((o, a, b) -> {
            try { comp.setVelocityY(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Velocidade Y"), 0, row);
        grid.add(velYField, 1, row); row++;

        // Gravidade (useGravity)
        CheckBox gravityCheck = new CheckBox("Usar gravidade global");
        gravityCheck.setSelected(comp.isUseGravity());
        gravityCheck.selectedProperty().addListener((o, a, b) -> {
            comp.setUseGravity(b); app.markProjectDirty();
        });
        grid.add(gravityCheck, 0, row, 2, 1); row++;

        // Gravidade global (estática)
        TextField gravityField = new TextField(String.format(java.util.Locale.ROOT, "%.1f", RigidbodyComponent.getGlobalGravity()));
        gravityField.textProperty().addListener((o, a, b) -> {
            try { RigidbodyComponent.setGlobalGravity(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Gravidade Global"), 0, row);
        grid.add(gravityField, 1, row); row++;

        // Gravity scale
        TextField gravScaleField = new TextField(String.format(java.util.Locale.ROOT, "%.2f", comp.getGravityScale()));
        gravScaleField.textProperty().addListener((o, a, b) -> {
            try { comp.setGravityScale(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Escala Gravidade"), 0, row);
        grid.add(gravScaleField, 1, row); row++;

        // Massa
        TextField massField = new TextField(String.format(java.util.Locale.ROOT, "%.2f", comp.getMass()));
        massField.textProperty().addListener((o, a, b) -> {
            try { comp.setMass(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Massa"), 0, row);
        grid.add(massField, 1, row); row++;

        // Arrasto linear
        TextField dragField = new TextField(String.format(java.util.Locale.ROOT, "%.2f", comp.getLinearDrag()));
        dragField.textProperty().addListener((o, a, b) -> {
            try { comp.setLinearDrag(Double.parseDouble(b)); app.markProjectDirty(); } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Arrasto Linear"), 0, row);
        grid.add(dragField, 1, row); row++;

        // Congelado
        CheckBox frozenCheck = new CheckBox("Congelado (ignora forcas)");
        frozenCheck.setSelected(comp.isFrozen());
        frozenCheck.selectedProperty().addListener((o, a, b) -> {
            comp.setFrozen(b); app.markProjectDirty();
        });
        grid.add(frozenCheck, 0, row, 2, 1); row++;

        box.getChildren().add(grid);
        return box;
    }

    javafx.scene.Node buildHealthComponentSection(GameObject go, HealthComponent comp) {
        VBox box = new VBox(6);
        box.getChildren().add(sectionTitle("Health Component"));

        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        
        javafx.scene.layout.ColumnConstraints labelCol = new javafx.scene.layout.ColumnConstraints();
        labelCol.setMinWidth(90);
        javafx.scene.layout.ColumnConstraints fieldCol = new javafx.scene.layout.ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);

        TextField healthField = new TextField(String.valueOf(comp.getHealth()));
        healthField.textProperty().addListener((o, a, b) -> {
            try {
                comp.setHealth(Integer.parseInt(b));
                app.markProjectDirty();
            } catch (NumberFormatException ignore) {}
        });
        grid.add(new Label("Vida"), 0, 0);
        grid.add(healthField, 1, 0);

        box.getChildren().add(grid);
        return box;
    }

    private HBox labeledInspectorRow(String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        l.setMinWidth(70);
        if (control instanceof javafx.scene.layout.Region) {
            ((javafx.scene.layout.Region) control).setMaxWidth(Double.MAX_VALUE);
        }
        HBox.setHgrow(control, Priority.ALWAYS);
        HBox row = new HBox(8, l, control);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }
}
