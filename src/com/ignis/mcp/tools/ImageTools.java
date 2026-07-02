package com.ignis.mcp.tools;

import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Content;
import com.ignis.imageeditor.ImageDocument;
import com.ignis.imageeditor.ImageDocument.Layer;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ImageTools - Ferramentas de edicao de imagem baseada em camadas do IgnisEngine expostas ao MCP.
 * Permite a criacao e composicao de imagens em camadas programaticamente.
 */
public final class ImageTools {

    private static final Map<String, ImageDocument> activeDocuments = new ConcurrentHashMap<>();

    private ImageTools() {}

    /**
     * Registra as ferramentas de imagem diretamente no servidor MCP.
     *
     * @param server Servidor MCP sincronizado.
     * @param projectFolder Pasta raiz do projeto ativo.
     */
    public static void register(McpSyncServer server, File projectFolder) {

        // --- 1. Tool: create_image_document ---
        Tool createImageDoc = Tool.builder()
            .name("create_image_document")
            .description("Cria um documento de imagem em branco na memoria com dimensoes especificadas")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "docId", Map.of("type", "string", "description", "Identificador unico para o documento"),
                    "width", Map.of("type", "integer", "description", "Largura da tela em pixels"),
                    "height", Map.of("type", "integer", "description", "Altura da tela em pixels")
                ),
                "required", List.of("docId", "width", "height")
            ))
            .build();
        server.addTool(new SyncToolSpecification(createImageDoc, (exchange, args) -> {
            try {
                String docId = (String) args.arguments().get("docId");
                int w = ((Number) args.arguments().get("width")).intValue();
                int h = ((Number) args.arguments().get("height")).intValue();

                ImageDocument doc = new ImageDocument(w, h);
                activeDocuments.put(docId, doc);

                return new CallToolResult(List.<Content>of(new TextContent("Documento de imagem '" + docId + "' (" + w + "x" + h + ") inicializado na memoria.")), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao criar documento: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 2. Tool: add_image_layer ---
        Tool addImageLayer = Tool.builder()
            .name("add_image_layer")
            .description("Adiciona uma nova camada de desenho ao documento de imagem")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "docId", Map.of("type", "string", "description", "Identificador do documento"),
                    "layerName", Map.of("type", "string", "description", "Nome da nova camada (ex: outline, shadow)")
                ),
                "required", List.of("docId", "layerName")
            ))
            .build();
        server.addTool(new SyncToolSpecification(addImageLayer, (exchange, args) -> {
            try {
                String docId = (String) args.arguments().get("docId");
                String layerName = (String) args.arguments().get("layerName");

                ImageDocument doc = activeDocuments.get(docId);
                if (doc == null) {
                    return new CallToolResult(List.<Content>of(new TextContent("Documento '" + docId + "' nao encontrado na memoria.")), true, null, null);
                }

                doc.addLayer(layerName);
                return new CallToolResult(List.<Content>of(new TextContent("Camada '" + layerName + "' adicionada ao documento '" + docId + "'.")), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao adicionar camada: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 3. Tool: import_image_to_layer ---
        Tool importImageToLayer = Tool.builder()
            .name("import_image_to_layer")
            .description("Importa uma imagem codificada em Base64 para uma camada especifica do documento")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "docId", Map.of("type", "string", "description", "Identificador do documento"),
                    "layerIndex", Map.of("type", "integer", "description", "Indice da camada a receber a imagem (0 = Background)"),
                    "base64Data", Map.of("type", "string", "description", "Dados de imagem serializados em Base64 (PNG/JPG)")
                ),
                "required", List.of("docId", "layerIndex", "base64Data")
            ))
            .build();
        server.addTool(new SyncToolSpecification(importImageToLayer, (exchange, args) -> {
            try {
                String docId = (String) args.arguments().get("docId");
                int index = ((Number) args.arguments().get("layerIndex")).intValue();
                String base64 = (String) args.arguments().get("base64Data");

                ImageDocument doc = activeDocuments.get(docId);
                if (doc == null) {
                    return new CallToolResult(List.<Content>of(new TextContent("Documento nao encontrado.")), true, null, null);
                }

                if (index < 0 || index >= doc.getLayers().size()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Indice de camada invalido.")), true, null, null);
                }

                byte[] bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
                BufferedImage imported;
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                    imported = ImageIO.read(bais);
                }

                if (imported == null) {
                    return new CallToolResult(List.<Content>of(new TextContent("Nao foi possivel decodificar os dados da imagem.")), true, null, null);
                }

                Layer layer = doc.getLayers().get(index);
                Graphics2D g = layer.getImage().createGraphics();
                g.drawImage(imported, 0, 0, doc.getWidth(), doc.getHeight(), null);
                g.dispose();

                return new CallToolResult(List.<Content>of(new TextContent("Imagem importada com sucesso para a camada " + index + " (" + layer.getName() + ").")), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao importar imagem: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 4. Tool: composite_and_save_image ---
        Tool compositeAndSave = Tool.builder()
            .name("composite_and_save_image")
            .description("Mixa todas as camadas visiveis do documento e salva em um arquivo PNG no projeto")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "docId", Map.of("type", "string", "description", "Identificador do documento"),
                    "outputPath", Map.of("type", "string", "description", "Caminho relativo para salvar a imagem final (ex: assets/sprites/hero.png)")
                ),
                "required", List.of("docId", "outputPath")
            ))
            .build();
        server.addTool(new SyncToolSpecification(compositeAndSave, (exchange, args) -> {
            try {
                String docId = (String) args.arguments().get("docId");
                String out = (String) args.arguments().get("outputPath");

                ImageDocument doc = activeDocuments.get(docId);
                if (doc == null) {
                    return new CallToolResult(List.<Content>of(new TextContent("Documento nao encontrado.")), true, null, null);
                }

                BufferedImage finalImage = doc.composite();
                File destFile = new File(projectFolder, out);
                destFile.getParentFile().mkdirs();

                boolean written = ImageIO.write(finalImage, "PNG", destFile);
                if (written) {
                    activeDocuments.remove(docId);
                    return new CallToolResult(List.<Content>of(new TextContent("Imagem renderizada com sucesso e salva em " + out)), false, null, null);
                } else {
                    return new CallToolResult(List.<Content>of(new TextContent("Falha ao salvar a imagem (formato PNG invalido).")), true, null, null);
                }
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao salvar imagem final: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 5. Tool: save_flat_image_asset ---
        Tool saveFlatAsset = Tool.builder()
            .name("save_flat_image_asset")
            .description("Salva uma imagem diretamente no diretorio do projeto a partir de dados Base64")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "outputPath", Map.of("type", "string", "description", "Caminho relativo de destino (ex: assets/sprites/block.png)"),
                    "base64Data", Map.of("type", "string", "description", "Dados de imagem em Base64")
                ),
                "required", List.of("outputPath", "base64Data")
            ))
            .build();
        server.addTool(new SyncToolSpecification(saveFlatAsset, (exchange, args) -> {
            try {
                String out = (String) args.arguments().get("outputPath");
                String base64 = (String) args.arguments().get("base64Data");

                byte[] bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
                BufferedImage img;
                try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
                    img = ImageIO.read(bais);
                }

                if (img == null) {
                    return new CallToolResult(List.<Content>of(new TextContent("Falha ao decodificar imagem.")), true, null, null);
                }

                File destFile = new File(projectFolder, out);
                destFile.getParentFile().mkdirs();
                ImageIO.write(img, "PNG", destFile);

                return new CallToolResult(List.<Content>of(new TextContent("Asset de imagem criado com sucesso em " + out)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao criar asset: " + e.getMessage())), true, null, null);
            }
        }));

        // --- 6. Tool: remove_sprite_background ---
        Tool removeBg = Tool.builder()
            .name("remove_sprite_background")
            .description("Remove cor solida ou padrao quadriculado (checkerboard) de fundo de uma imagem. targetColorHex pode ser 'auto' (detecta cantos/bordas), uma cor ex: '#ffffff' ou lista ex: '#ffffff,#cccccc'.")
            .inputSchema(Map.of(
                "type", (Object) "object",
                "properties", Map.of(
                    "imagePath", Map.of("type", "string", "description", "Caminho relativo para a imagem (ex: assets/sprites/hero.png)"),
                    "targetColorHex", Map.of("type", "string", "description", "Cor hex (ex: #ffffff), lista de cores (ex: #ffffff,#cccccc) ou 'auto' para auto-detectar fundo pelas bordas"),
                    "tolerance", Map.of("type", "integer", "description", "Tolerancia de cor (0 a 255, padrao 20)")
                ),
                "required", List.of("imagePath", "targetColorHex")
            ))
            .build();
        server.addTool(new SyncToolSpecification(removeBg, (exchange, args) -> {
            try {
                String imgPath = (String) args.arguments().get("imagePath").toString();
                String targetHex = (String) args.arguments().get("targetColorHex").toString();
                int tolerance = args.arguments().containsKey("tolerance") ? ((Number) args.arguments().get("tolerance")).intValue() : 20;

                File imgFile = new File(projectFolder, imgPath);
                if (!imgFile.exists()) {
                    return new CallToolResult(List.<Content>of(new TextContent("Arquivo nao encontrado: " + imgPath)), true, null, null);
                }

                BufferedImage src = ImageIO.read(imgFile);
                if (src == null) {
                    return new CallToolResult(List.<Content>of(new TextContent("Nao foi possivel ler a imagem.")), true, null, null);
                }

                int w = src.getWidth();
                int h = src.getHeight();
                List<java.awt.Color> colorsToRemove = new ArrayList<>();

                if ("auto".equalsIgnoreCase(targetHex.trim())) {
                    // Coleta cores solidas das bordas da imagem (top, bottom, left, right)
                    for (int x = 0; x < w; x++) {
                        collectSolidColor(src.getRGB(x, 0), colorsToRemove);
                        collectSolidColor(src.getRGB(x, h - 1), colorsToRemove);
                    }
                    for (int y = 0; y < h; y++) {
                        collectSolidColor(src.getRGB(0, y), colorsToRemove);
                        collectSolidColor(src.getRGB(w - 1, y), colorsToRemove);
                    }
                } else {
                    // Parse de uma ou mais cores separadas por virgula
                    String[] parts = targetHex.split(",");
                    for (String part : parts) {
                        String hex = part.trim();
                        if (hex.startsWith("#")) hex = hex.substring(1);
                        if (hex.length() != 6 && hex.length() != 3) {
                            return new CallToolResult(List.<Content>of(new TextContent("Erro: cor hex invalida: " + part)), true, null, null);
                        }
                        int tr, tg, tb;
                        if (hex.length() == 6) {
                            tr = Integer.parseInt(hex.substring(0, 2), 16);
                            tg = Integer.parseInt(hex.substring(2, 4), 16);
                            tb = Integer.parseInt(hex.substring(4, 6), 16);
                        } else {
                            tr = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                            tg = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                            tb = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                        }
                        colorsToRemove.add(new java.awt.Color(tr, tg, tb));
                    }
                }

                BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                int removedCount = 0;

                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int rgb = src.getRGB(x, y);
                        int a = (rgb >> 24) & 0xff;
                        if (a < 50) {
                            dst.setRGB(x, y, rgb); // Mantem transparente
                            continue;
                        }

                        int r = (rgb >> 16) & 0xff;
                        int g = (rgb >> 8) & 0xff;
                        int b = rgb & 0xff;

                        boolean matches = false;
                        for (java.awt.Color tc : colorsToRemove) {
                            double dist = Math.sqrt((r - tc.getRed()) * (r - tc.getRed()) +
                                                    (g - tc.getGreen()) * (g - tc.getGreen()) +
                                                    (b - tc.getBlue()) * (b - tc.getBlue()));
                            if (dist <= tolerance) {
                                matches = true;
                                break;
                            }
                        }

                        if (matches) {
                            dst.setRGB(x, y, 0x00000000);
                            removedCount++;
                        } else {
                            dst.setRGB(x, y, rgb);
                        }
                    }
                }

                ImageIO.write(dst, "PNG", imgFile);
                return new CallToolResult(List.<Content>of(new TextContent("Processamento concluido. Removidos " + removedCount + " pixels de fundo de " + imgPath)), false, null, null);
            } catch (Exception e) {
                return new CallToolResult(List.<Content>of(new TextContent("Erro ao processar imagem: " + e.getMessage())), true, null, null);
            }
        }));
    }

    private static void collectSolidColor(int rgb, List<java.awt.Color> list) {
        int a = (rgb >> 24) & 0xff;
        if (a < 50) return;
        java.awt.Color c = new java.awt.Color(rgb);
        for (java.awt.Color existing : list) {
            double dist = Math.sqrt(
                (c.getRed() - existing.getRed()) * (c.getRed() - existing.getRed()) +
                (c.getGreen() - existing.getGreen()) * (c.getGreen() - existing.getGreen()) +
                (c.getBlue() - existing.getBlue()) * (c.getBlue() - existing.getBlue())
            );
            if (dist < 5.0) return; // Cor ja representada
        }
        list.add(c);
    }
}
