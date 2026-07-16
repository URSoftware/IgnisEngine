package com.ignis.mcp;

import com.ignis.core.GameObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ferramentas MCP de <b>conteudo de cena</b> (Fases C/D do motor grafico): camadas
 * de fundo com parallax, emissores de particulas, tilemaps, textos no mundo e luzes
 * 2D. Extraido do {@link IgnisToolRegistry} (Fase F -- quebra das god classes): o
 * registry tinha ~120 ferramentas num arquivo unico; este grupo e coeso e cabe
 * numa classe propria.
 *
 * <p>Recebe o {@link IgnisToolRegistry} e registra as ferramentas nele via
 * {@code reg.add(...)}; os handlers leem o editor vivo e os helpers compartilhados
 * (findObject, resolveInProject, parseColor, schemaWith) do proprio registry. Mesmo
 * pacote, entao usa os membros package-private sem API publica nova.</p>
 */
final class ContentTools {

    private final IgnisToolRegistry reg;

    ContentTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    /** Registra todas as ferramentas de conteudo no registry. */
    void registerAll() {
        registerBackgroundTools();
        registerParticleTools();
        registerTilemapTools();
        registerTextTools();
        registerLightTools();
    }

    void registerTilemapTools() {
        // create_tilemap
        Map<String, String> tmProps = new LinkedHashMap<>();
        tmProps.put("name", "Nome unico do tilemap");
        tmProps.put("tileset", "Caminho da imagem do tileset, relativo ao projeto");
        tmProps.put("tileW", "Largura de cada tile em px (padrao 32)");
        tmProps.put("tileH", "Altura de cada tile em px (padrao 32)");
        tmProps.put("cols", "Numero de colunas da grade (padrao 20)");
        tmProps.put("rows", "Numero de linhas da grade (padrao 15)");
        tmProps.put("x", "Posicao X do canto superior-esquerdo (padrao 0)");
        tmProps.put("y", "Posicao Y do canto superior-esquerdo (padrao 0)");
        reg.add("create_tilemap",
            "Cria um tilemap (grade de tiles a partir de um tileset) e o adiciona a cena.",
            IgnisToolRegistry.schemaWith(tmProps, List.of("name", "tileset")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (reg.findObject(name) != null) return "Erro: ja existe objeto com o nome: " + name;
                String tileset = args.optString("tileset", "");
                if (reg.resolveInProject(tileset) == null) return "Erro: tileset fora do projeto: " + tileset;
                com.ignis.core.TilemapObject tm = new com.ignis.core.TilemapObject();
                tm.setName(name);
                tm.setGame(reg.liveGame);
                tm.configure(tileset,
                        args.optInt("tileW", 32), args.optInt("tileH", 32),
                        args.optInt("cols", 20), args.optInt("rows", 15));
                tm.setX(args.optDouble("x", 0));
                tm.setY(args.optDouble("y", 0));
                reg.liveGame.addEntity(tm);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Tilemap criado: " + name + " (" + tm.getCols() + "x" + tm.getRows()
                        + " tiles de " + tm.getTileW() + "x" + tm.getTileH() + ")";
            });

        // add_tilemap_layer
        reg.add("add_tilemap_layer",
            "Adiciona uma nova camada vazia a um tilemap e retorna o indice dela.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome do tilemap"), List.of("name")),
            args -> {
                com.ignis.core.TilemapObject tm = findTilemap(args.optString("name", ""));
                if (tm == null) return "Erro: tilemap nao encontrado: " + args.optString("name", "");
                int idx = tm.addLayer();
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Camada adicionada ao tilemap " + tm.getName() + ": indice " + idx;
            });

        // set_tile
        Map<String, String> setTileProps = new LinkedHashMap<>();
        setTileProps.put("name", "Nome do tilemap");
        setTileProps.put("col", "Coluna da celula");
        setTileProps.put("row", "Linha da celula");
        setTileProps.put("tile", "Indice do tile no tileset (-1 = vazio)");
        setTileProps.put("layer", "Indice da camada (padrao 0)");
        reg.add("set_tile",
            "Define o tile de uma celula de um tilemap (indice do tileset; -1 apaga).",
            IgnisToolRegistry.schemaWith(setTileProps, List.of("name", "col", "row", "tile")),
            args -> {
                com.ignis.core.TilemapObject tm = findTilemap(args.optString("name", ""));
                if (tm == null) return "Erro: tilemap nao encontrado: " + args.optString("name", "");
                tm.setTile(args.optInt("layer", 0), args.optInt("col"), args.optInt("row"), args.optInt("tile"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Tile (" + args.optInt("col") + "," + args.optInt("row") + ") = " + args.optInt("tile")
                        + " no tilemap " + tm.getName();
            });

        // paint_tiles
        Map<String, String> paintProps = new LinkedHashMap<>();
        paintProps.put("name", "Nome do tilemap");
        paintProps.put("col0", "Coluna inicial do retangulo");
        paintProps.put("row0", "Linha inicial do retangulo");
        paintProps.put("col1", "Coluna final do retangulo");
        paintProps.put("row1", "Linha final do retangulo");
        paintProps.put("tile", "Indice do tile a pintar (-1 = apagar)");
        paintProps.put("layer", "Indice da camada (padrao 0)");
        reg.add("paint_tiles",
            "Pinta um retangulo de celulas [col0..col1]x[row0..row1] com um tile.",
            IgnisToolRegistry.schemaWith(paintProps, List.of("name", "col0", "row0", "col1", "row1", "tile")),
            args -> {
                com.ignis.core.TilemapObject tm = findTilemap(args.optString("name", ""));
                if (tm == null) return "Erro: tilemap nao encontrado: " + args.optString("name", "");
                tm.fillTiles(args.optInt("layer", 0),
                        args.optInt("col0"), args.optInt("row0"),
                        args.optInt("col1"), args.optInt("row1"), args.optInt("tile"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Retangulo pintado no tilemap " + tm.getName();
            });

        // clear_tilemap_layer
        Map<String, String> clearProps = new LinkedHashMap<>();
        clearProps.put("name", "Nome do tilemap");
        clearProps.put("layer", "Indice da camada a limpar (padrao 0)");
        reg.add("clear_tilemap_layer",
            "Apaga todos os tiles de uma camada de um tilemap.",
            IgnisToolRegistry.schemaWith(clearProps, List.of("name")),
            args -> {
                com.ignis.core.TilemapObject tm = findTilemap(args.optString("name", ""));
                if (tm == null) return "Erro: tilemap nao encontrado: " + args.optString("name", "");
                int layer = args.optInt("layer", 0);
                tm.fillTiles(layer, 0, 0, tm.getCols() - 1, tm.getRows() - 1, com.ignis.core.TilemapObject.EMPTY);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Camada " + layer + " do tilemap " + tm.getName() + " limpa";
            });
    }

    /** Localiza um TilemapObject por nome na cena viva, ou null. */
    private com.ignis.core.TilemapObject findTilemap(String name) {
        GameObject go = reg.findObject(name);
        return (go instanceof com.ignis.core.TilemapObject) ? (com.ignis.core.TilemapObject) go : null;
    }

    void registerTextTools() {
        // create_text_object
        Map<String, String> txtProps = new LinkedHashMap<>();
        txtProps.put("name", "Nome unico do objeto de texto");
        txtProps.put("text", "Conteudo do texto (use \\n para varias linhas)");
        txtProps.put("x", "Posicao X do canto (padrao 0)");
        txtProps.put("y", "Posicao Y do canto (padrao 0)");
        txtProps.put("fontSize", "Tamanho da fonte em px (padrao 24)");
        txtProps.put("fontFamily", "Familia da fonte (padrao SansSerif)");
        txtProps.put("color", "Cor do texto 0xAARRGGBB/#RRGGBB (padrao branco)");
        txtProps.put("bold", "Negrito (padrao false)");
        txtProps.put("italic", "Italico (padrao false)");
        txtProps.put("align", "Alinhamento: LEFT|CENTER|RIGHT (padrao LEFT)");
        txtProps.put("zIndex", "Ordem de render (padrao 100, na frente das entidades)");
        reg.add("create_text_object",
            "Cria um texto no espaco do mundo (placa/rotulo/dano flutuante) e o adiciona a cena.",
            IgnisToolRegistry.schemaWith(txtProps, List.of("name", "text")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (reg.findObject(name) != null) return "Erro: ja existe objeto com o nome: " + name;
                com.ignis.core.TextObject txt = new com.ignis.core.TextObject();
                txt.setName(name);
                txt.setGame(reg.liveGame);
                txt.setText(args.optString("text", "Texto"));
                txt.setX(args.optDouble("x", 0));
                txt.setY(args.optDouble("y", 0));
                txt.setFontSize(args.optInt("fontSize", 24));
                if (args.has("fontFamily")) txt.setFontFamily(args.optString("fontFamily"));
                java.awt.Color c = IgnisToolRegistry.parseColor(args.optString("color", ""));
                if (c != null) txt.setColor(c);
                txt.setBold(args.optBoolean("bold", false));
                txt.setItalic(args.optBoolean("italic", false));
                txt.setAlign(parseAlign(args.optString("align", "")));
                txt.setZIndex(args.optInt("zIndex", 100));
                reg.liveGame.addEntity(txt);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Texto criado: " + name + " (\"" + txt.getText().replace("\n", "\\n") + "\", "
                        + txt.getFontSize() + "px)";
            });

        // set_text — edita conteudo/estilo de um TextObject existente
        Map<String, String> setTxtProps = new LinkedHashMap<>();
        setTxtProps.put("name", "Nome do objeto de texto");
        setTxtProps.put("text", "Novo conteudo (opcional)");
        setTxtProps.put("fontSize", "Novo tamanho em px (opcional)");
        setTxtProps.put("fontFamily", "Nova familia de fonte (opcional)");
        setTxtProps.put("color", "Nova cor 0xAARRGGBB/#RRGGBB (opcional)");
        setTxtProps.put("bold", "Negrito (opcional)");
        setTxtProps.put("italic", "Italico (opcional)");
        setTxtProps.put("align", "Alinhamento LEFT|CENTER|RIGHT (opcional)");
        reg.add("set_text",
            "Edita o conteudo e/ou o estilo de um objeto de texto existente.",
            IgnisToolRegistry.schemaWith(setTxtProps, List.of("name")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (!(go instanceof com.ignis.core.TextObject)) {
                    return "Erro: objeto de texto nao encontrado: " + args.optString("name", "");
                }
                com.ignis.core.TextObject txt = (com.ignis.core.TextObject) go;
                if (args.has("text")) txt.setText(args.optString("text"));
                if (args.has("fontSize")) txt.setFontSize(args.optInt("fontSize"));
                if (args.has("fontFamily")) txt.setFontFamily(args.optString("fontFamily"));
                if (args.has("color")) {
                    java.awt.Color c = IgnisToolRegistry.parseColor(args.optString("color", ""));
                    if (c != null) txt.setColor(c);
                }
                if (args.has("bold")) txt.setBold(args.optBoolean("bold"));
                if (args.has("italic")) txt.setItalic(args.optBoolean("italic"));
                if (args.has("align")) txt.setAlign(parseAlign(args.optString("align", "")));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Texto atualizado: " + txt.getName();
            });
    }

    void registerLightTools() {
        // create_light_object
        Map<String, String> lightProps = new LinkedHashMap<>();
        lightProps.put("name", "Nome unico da luz");
        lightProps.put("x", "Posicao X do centro (padrao 0)");
        lightProps.put("y", "Posicao Y do centro (padrao 0)");
        lightProps.put("color", "Cor da luz 0xAARRGGBB/#RRGGBB (padrao quente)");
        lightProps.put("radius", "Raio de alcance em px (padrao 160)");
        lightProps.put("intensity", "Intensidade 0..1 no centro (padrao 1)");
        reg.add("create_light_object",
            "Cria um ponto de luz 2D. So ilumina se a cena tiver luz ambiente (set_scene_ambient_light).",
            IgnisToolRegistry.schemaWith(lightProps, List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (reg.findObject(name) != null) return "Erro: ja existe objeto com o nome: " + name;
                com.ignis.core.LightObject light = new com.ignis.core.LightObject();
                light.setName(name);
                light.setGame(reg.liveGame);
                light.setX(args.optDouble("x", 0));
                light.setY(args.optDouble("y", 0));
                java.awt.Color c = IgnisToolRegistry.parseColor(args.optString("color", ""));
                if (c != null) light.setLightColor(c);
                light.setRadius(args.optDouble("radius", 160));
                light.setIntensity(args.optDouble("intensity", 1.0));
                reg.liveGame.addEntity(light);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Luz criada: " + name + " (raio=" + (int) light.getRadius()
                        + ", intensidade=" + light.getIntensity() + ")";
            });

        // set_light_properties
        Map<String, String> setLightProps = new LinkedHashMap<>();
        setLightProps.put("name", "Nome da luz");
        setLightProps.put("color", "Nova cor (opcional)");
        setLightProps.put("radius", "Novo raio em px (opcional)");
        setLightProps.put("intensity", "Nova intensidade 0..1 (opcional)");
        reg.add("set_light_properties",
            "Ajusta cor, raio e/ou intensidade de uma luz existente.",
            IgnisToolRegistry.schemaWith(setLightProps, List.of("name")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (!(go instanceof com.ignis.core.LightObject)) {
                    return "Erro: luz nao encontrada: " + args.optString("name", "");
                }
                com.ignis.core.LightObject light = (com.ignis.core.LightObject) go;
                if (args.has("color")) {
                    java.awt.Color c = IgnisToolRegistry.parseColor(args.optString("color", ""));
                    if (c != null) light.setLightColor(c);
                }
                if (args.has("radius")) light.setRadius(args.optDouble("radius"));
                if (args.has("intensity")) light.setIntensity(args.optDouble("intensity"));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Luz atualizada: " + light.getName();
            });

        // set_scene_ambient_light
        Map<String, String> ambProps = new LinkedHashMap<>();
        ambProps.put("color", "Cor/escuridao ambiente 0xAARRGGBB (o alpha e a intensidade da escuridao), ex: 0xE0050510. Vazio/none desliga.");
        reg.add("set_scene_ambient_light",
            "Define a luz ambiente (escuridao) da cena ativa. Sem ela, as luzes nao tem efeito. Passe 'none' para desligar.",
            IgnisToolRegistry.schemaWith(ambProps, List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String raw = args.optString("color", "").trim();
                if (raw.isEmpty() || raw.equalsIgnoreCase("none")) {
                    reg.liveGame.setAmbientLight(null);
                    if (reg.refreshHook != null) reg.refreshHook.run();
                    return "Luz ambiente desligada (cena totalmente visivel).";
                }
                java.awt.Color c = IgnisToolRegistry.parseColor(raw);
                if (c == null) return "Erro: cor invalida: " + raw;
                reg.liveGame.setAmbientLight(c);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Luz ambiente definida (alpha/escuridao=" + c.getAlpha() + ").";
            });
    }

    void registerParticleTools() {
        // create_particle_emitter
        Map<String, String> peProps = new LinkedHashMap<>();
        peProps.put("name", "Nome unico do emissor");
        peProps.put("x", "Posicao X (padrao 0)");
        peProps.put("y", "Posicao Y (padrao 0)");
        peProps.put("rate", "Particulas por segundo (padrao 40)");
        peProps.put("maxParticles", "Tamanho do pool (padrao 200)");
        peProps.put("lifetime", "Vida base da particula em segundos (padrao 1.2)");
        peProps.put("velX", "Velocidade inicial media X px/s (padrao 0)");
        peProps.put("velY", "Velocidade inicial media Y px/s (padrao 80)");
        peProps.put("gravityY", "Aceleracao vertical px/s^2 (padrao -120)");
        peProps.put("sizeStart", "Diametro inicial px (padrao 10)");
        peProps.put("sizeEnd", "Diametro final px (padrao 2)");
        peProps.put("colorStart", "Cor inicial 0xAARRGGBB/#RRGGBB (opcional)");
        peProps.put("colorEnd", "Cor final 0xAARRGGBB/#RRGGBB (opcional)");
        peProps.put("sprite", "Sprite opcional da particula (relativo ao projeto)");
        reg.add("create_particle_emitter",
            "Cria um emissor de particulas (poeira/fogo/explosao) e o adiciona a cena.",
            IgnisToolRegistry.schemaWith(peProps, List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (reg.findObject(name) != null) return "Erro: ja existe objeto com o nome: " + name;
                com.ignis.core.ParticleEmitter pe = new com.ignis.core.ParticleEmitter();
                pe.setName(name);
                pe.setGame(reg.liveGame);
                pe.setX(args.optDouble("x", 0));
                pe.setY(args.optDouble("y", 0));
                if (args.has("rate")) pe.setEmissionRate(args.optDouble("rate"));
                if (args.has("maxParticles")) pe.setMaxParticles(args.optInt("maxParticles"));
                if (args.has("lifetime")) pe.setLifetime(args.optDouble("lifetime"));
                if (args.has("velX")) pe.setVelX(args.optDouble("velX"));
                if (args.has("velY")) pe.setVelY(args.optDouble("velY"));
                if (args.has("gravityY")) pe.setGravityY(args.optDouble("gravityY"));
                if (args.has("sizeStart")) pe.setSizeStart(args.optDouble("sizeStart"));
                if (args.has("sizeEnd")) pe.setSizeEnd(args.optDouble("sizeEnd"));
                java.awt.Color cs = IgnisToolRegistry.parseColor(args.optString("colorStart", ""));
                if (cs != null) pe.setColorStart(cs);
                java.awt.Color ce = IgnisToolRegistry.parseColor(args.optString("colorEnd", ""));
                if (ce != null) pe.setColorEnd(ce);
                String sprite = args.optString("sprite", "");
                if (!sprite.isEmpty()) {
                    if (reg.resolveInProject(sprite) == null) return "Erro: sprite fora do projeto: " + sprite;
                    pe.setParticleSprite(sprite);
                }
                reg.liveGame.addEntity(pe);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Emissor de particulas criado: " + name + " (rate=" + pe.getEmissionRate()
                        + ", pool=" + pe.getMaxParticles() + ")";
            });

        // particle_burst
        Map<String, String> burstProps = new LinkedHashMap<>();
        burstProps.put("name", "Nome do emissor");
        burstProps.put("count", "Quantidade de particulas a emitir de uma vez");
        reg.add("particle_burst",
            "Emite uma rajada instantanea de particulas de um emissor (explosao).",
            IgnisToolRegistry.schemaWith(burstProps, List.of("name", "count")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (!(go instanceof com.ignis.core.ParticleEmitter)) {
                    return "Erro: emissor nao encontrado: " + args.optString("name", "");
                }
                int count = args.optInt("count", 0);
                if (count <= 0) return "Erro: 'count' deve ser > 0.";
                ((com.ignis.core.ParticleEmitter) go).burst(count);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Rajada de " + count + " particulas em " + go.getName();
            });

        // set_particle_emitting
        Map<String, String> emitProps = new LinkedHashMap<>();
        emitProps.put("name", "Nome do emissor");
        emitProps.put("emitting", "true para emitir continuamente, false para pausar");
        reg.add("set_particle_emitting",
            "Liga/desliga a emissao continua de um emissor de particulas.",
            IgnisToolRegistry.schemaWith(emitProps, List.of("name", "emitting")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (!(go instanceof com.ignis.core.ParticleEmitter)) {
                    return "Erro: emissor nao encontrado: " + args.optString("name", "");
                }
                ((com.ignis.core.ParticleEmitter) go).setEmitting(args.optBoolean("emitting", true));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Emissao de " + go.getName() + ": " + args.optBoolean("emitting", true);
            });
    }

    void registerBackgroundTools() {
        // create_background_layer
        Map<String, String> bgProps = new LinkedHashMap<>();
        bgProps.put("name", "Nome unico da camada de fundo");
        bgProps.put("path", "Caminho do sprite de fundo, relativo ao projeto (opcional; sem ele use 'color')");
        bgProps.put("color", "Cor solida de fundo em hex 0xAARRGGBB ou #RRGGBB (opcional)");
        bgProps.put("parallax", "Fator de parallax 0..1 nos dois eixos (0=fixo no mundo, 1=preso a camera). Padrao 0.5");
        bgProps.put("zIndex", "Ordem de render (padrao -1000, atras de tudo). Menor = mais ao fundo");
        bgProps.put("repeat", "true para repetir o sprite cobrindo a tela (padrao true)");
        reg.add("create_background_layer",
            "Cria uma camada de fundo com parallax e a adiciona a cena ativa.",
            IgnisToolRegistry.schemaWith(bgProps, List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String name = args.optString("name", "").trim();
                if (name.isEmpty()) return "Erro: 'name' obrigatorio.";
                if (reg.findObject(name) != null) return "Erro: ja existe objeto com o nome: " + name;
                com.ignis.core.BackgroundLayer bg = new com.ignis.core.BackgroundLayer();
                bg.setName(name);
                bg.setGame(reg.liveGame);
                String path = args.optString("path", "");
                if (!path.isEmpty()) {
                    if (reg.resolveInProject(path) == null) return "Erro: path invalido (fora do projeto): " + path;
                    bg.setImagePath(path);
                }
                java.awt.Color c = IgnisToolRegistry.parseColor(args.optString("color", ""));
                if (c != null) bg.setColor(c);
                if (path.isEmpty() && c == null) {
                    return "Erro: informe 'path' (sprite) ou 'color' (fundo solido).";
                }
                bg.setParallax(IgnisToolRegistry.clamp01(args.optDouble("parallax", 0.5)));
                bg.setZIndex(args.optInt("zIndex", -1000));
                boolean repeat = args.optBoolean("repeat", true);
                bg.setRepeatX(repeat);
                bg.setRepeatY(repeat);
                reg.liveGame.addEntity(bg);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Camada de fundo criada: " + name + " (parallax=" + bg.getParallaxX() + ", zIndex=" + bg.getZIndex() + ")";
            });

        // set_parallax_factor
        Map<String, String> pfProps = new LinkedHashMap<>();
        pfProps.put("name", "Nome da camada de fundo");
        pfProps.put("parallax", "Fator 0..1 aplicado aos dois eixos (opcional)");
        pfProps.put("parallaxX", "Fator do eixo X (opcional, sobrescreve 'parallax')");
        pfProps.put("parallaxY", "Fator do eixo Y (opcional, sobrescreve 'parallax')");
        reg.add("set_parallax_factor",
            "Ajusta o fator de parallax de uma camada de fundo existente.",
            IgnisToolRegistry.schemaWith(pfProps, List.of("name")),
            args -> {
                GameObject go = reg.findObject(args.optString("name", ""));
                if (!(go instanceof com.ignis.core.BackgroundLayer)) {
                    return "Erro: camada de fundo nao encontrada: " + args.optString("name", "");
                }
                com.ignis.core.BackgroundLayer bg = (com.ignis.core.BackgroundLayer) go;
                if (args.has("parallax")) bg.setParallax(IgnisToolRegistry.clamp01(args.optDouble("parallax")));
                if (args.has("parallaxX")) bg.setParallaxX(IgnisToolRegistry.clamp01(args.optDouble("parallaxX")));
                if (args.has("parallaxY")) bg.setParallaxY(IgnisToolRegistry.clamp01(args.optDouble("parallaxY")));
                if (reg.refreshHook != null) reg.refreshHook.run();
                return "Parallax de " + bg.getName() + ": X=" + bg.getParallaxX() + " Y=" + bg.getParallaxY();
            });
    }

    /** Interpreta o alinhamento textual; default LEFT para vazio/invalido. */
    private static com.ignis.core.TextObject.TextAlign parseAlign(String s) {
        if (s == null) return com.ignis.core.TextObject.TextAlign.LEFT;
        try {
            return com.ignis.core.TextObject.TextAlign.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return com.ignis.core.TextObject.TextAlign.LEFT;
        }
    }
}
