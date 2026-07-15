package com.ignis.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * Desenhadores-folha dos overlays da cena (Fase F — decomposicao do {@link Game}):
 * grade do editor, overlay do World (limites + barreiras), grade de pintura de
 * barreiras, o passe de iluminacao 2D e os alertas do editor.
 *
 * <p>Sao chamados pelo orquestrador {@code Game.renderWorldTo}; cada um le o estado
 * necessario do {@link Game} (camera, World, luz ambiente) e desenha, sem mudar
 * nada de gameplay. O buffer da mascara de luz e um cache puramente de render e
 * por isso vive aqui, nao no {@code Game}.</p>
 */
final class SceneOverlayRenderer {

    private final Game game;

    // Buffer da mascara de iluminacao, reaproveitado entre frames (Fase D 3.11).
    private transient BufferedImage lightMaskBuffer = null;

    SceneOverlayRenderer(Game game) {
        this.game = game;
    }

    /**
     * Draws the editor grid in game.getWorld() space.
     * The grid adapts to the camera zoom level for better visibility.
     */
    void drawGrid(Graphics2D g2d) {
        Camera cam = game.getViewCamera();
        if (cam == null) return;
        
        double zoom = cam.getZoom();
        
        // Adapt grid size based on zoom level for better visibility
        int effectiveGridSize = game.gridSize;
        if (zoom < 0.25) {
            effectiveGridSize = game.gridSize * 8;
        } else if (zoom < 0.5) {
            effectiveGridSize = game.gridSize * 4;
        } else if (zoom < 1.0) {
            effectiveGridSize = game.gridSize * 2;
        }
        
        // Get visible game.getWorld() bounds
        double[] bounds = cam.getVisibleWorldBounds();
        double minX = bounds[0];
        double minY = bounds[1];
        double maxX = bounds[2];
        double maxY = bounds[3];
        
        // Extend bounds slightly to avoid edge artifacts
        minX -= effectiveGridSize;
        minY -= effectiveGridSize;
        maxX += effectiveGridSize;
        maxY += effectiveGridSize;
        
        // Snap to grid
        int startX = (int)(Math.floor(minX / effectiveGridSize) * effectiveGridSize);
        int startY = (int)(Math.floor(minY / effectiveGridSize) * effectiveGridSize);
        int endX = (int)(Math.ceil(maxX / effectiveGridSize) * effectiveGridSize);
        int endY = (int)(Math.ceil(maxY / effectiveGridSize) * effectiveGridSize);
        
        // Set grid appearance
        g2d.setColor(game.gridColor);
        g2d.setStroke(new BasicStroke(1.0f / (float)zoom)); // Thin lines that stay consistent
        
        // Draw vertical lines
        for (int x = startX; x <= endX; x += effectiveGridSize) {
            g2d.drawLine(x, startY, x, endY);
        }
        
        // Draw horizontal lines
        for (int y = startY; y <= endY; y += effectiveGridSize) {
            g2d.drawLine(startX, y, endX, y);
        }
        
        // Draw major grid lines (every 4 cells) with slightly more opacity
        g2d.setColor(new Color(game.gridColor.getRed(), game.gridColor.getGreen(), game.gridColor.getBlue(), 
                              Math.min(255, game.gridColor.getAlpha() * 2)));
        g2d.setStroke(new BasicStroke(1.5f / (float)zoom));
        
        int majorGridSize = effectiveGridSize * 4;
        int majorStartX = (int)(Math.floor(minX / majorGridSize) * majorGridSize);
        int majorStartY = (int)(Math.floor(minY / majorGridSize) * majorGridSize);
        
        for (int x = majorStartX; x <= endX; x += majorGridSize) {
            g2d.drawLine(x, startY, x, endY);
        }
        
        for (int y = majorStartY; y <= endY; y += majorGridSize) {
            g2d.drawLine(startX, y, endX, y);
        }
    }

    /**
     * Desenha a grade de celulas do World sobre a area visivel, para orientar a
     * pintura de barreiras (ferramenta WORLD_PAINT). Linhas tenues; as celulas ja
     * bloqueadas continuam sendo desenhadas por {@link #drawWorldOverlay}.
     */
    void drawWorldPaintGrid(Graphics2D g2d, int width, int height) {
        if (game.getWorld() == null) return;
        int cs = game.getWorld().getCellSize();
        if (cs <= 0) return;
        Camera cam = game.getViewCamera();
        double[] vis = (cam != null) ? cam.getVisibleWorldBounds() : new double[] { 0, 0, width, height };
        int c0 = game.getWorld().cellCol(vis[0]) - 1, c1 = game.getWorld().cellCol(vis[2]) + 1;
        int r0 = game.getWorld().cellRow(vis[1]) - 1, r1 = game.getWorld().cellRow(vis[3]) + 1;
        // Limite de seguranca para nao desenhar milhares de linhas em zoom-out extremo.
        if ((long) (c1 - c0) * (r1 - r0) > 20000) return;
        g2d.setColor(new Color(255, 255, 255, 40));
        g2d.setStroke(new BasicStroke((float) Math.max(0.5, game.editorWorldPerPixel())));
        for (int c = c0; c <= c1; c++) {
            int x = c * cs;
            g2d.drawLine(x, r0 * cs, x, r1 * cs);
        }
        for (int r = r0; r <= r1; r++) {
            int y = r * cs;
            g2d.drawLine(c0 * cs, y, c1 * cs, y);
        }
    }

    void drawWorldOverlay(Graphics2D g2d) {
        if (game.getWorld() == null) return;
        // Barreiras: so as celulas dentro do retangulo visivel (culling barato).
        if (game.getWorld().getBlockedCount() > 0) {
            double[] vis = null;
            Camera cam = game.getViewCamera();
            if (cam != null) vis = cam.getVisibleWorldBounds();
            int cs = game.getWorld().getCellSize();
            int c0, c1, r0, r1;
            if (vis != null) {
                c0 = game.getWorld().cellCol(vis[0]); c1 = game.getWorld().cellCol(vis[2]);
                r0 = game.getWorld().cellRow(vis[1]); r1 = game.getWorld().cellRow(vis[3]);
            } else {
                c0 = r0 = -200; c1 = r1 = 200; // fallback limitado
            }
            g2d.setColor(new Color(220, 60, 60, 90));
            java.awt.Color border = new Color(220, 60, 60, 160);
            for (int c = c0; c <= c1; c++) {
                for (int r = r0; r <= r1; r++) {
                    if (!game.getWorld().isCellBlocked(c, r)) continue;
                    int cx = c * cs, cy = r * cs;
                    g2d.setColor(new Color(220, 60, 60, 90));
                    g2d.fillRect(cx, cy, cs, cs);
                    g2d.setColor(border);
                    g2d.drawRect(cx, cy, cs, cs);
                }
            }
        }
        // Limites do mapa: contorno azul-claro.
        if (game.getWorld().hasBounds()) {
            g2d.setColor(new Color(80, 170, 255, 220));
            g2d.setStroke(new java.awt.BasicStroke(2f));
            int bx = (int) game.getWorld().getMinX(), by = (int) game.getWorld().getMinY();
            int bw = (int) (game.getWorld().getMaxX() - game.getWorld().getMinX());
            int bh = (int) (game.getWorld().getMaxY() - game.getWorld().getMinY());
            g2d.drawRect(bx, by, bw, bh);
        }
    }

    /**
     * Passe de iluminacao 2D (Fase D 3.11), desenhado em screen-space logo antes
     * da UI. No-op quando nao ha {@link #game.getAmbientLight()}. Compoe uma mascara de
     * escuridao com buracos suaves nas luzes (ver {@link LightObject#composeMask})
     * e a sobrepoe a cena; depois aplica um brilho colorido translucido por luz.
     *
     * @param g2d          alvo, ja no transform de tela (identidade de dispositivo)
     * @param width,height dimensoes em pixels do alvo
     * @param cam          camera ativa (para mapear mundo->tela), ou null
     * @param cameraApplied se a transform de camera estava aplicada as entidades
     */
    void renderLightingPass(Graphics2D g2d, int width, int height,
                                    Camera cam, boolean cameraApplied) {
        if (game.getAmbientLight() == null || game.getAmbientLight().getAlpha() == 0) return;

        java.util.List<LightObject> lights = new java.util.ArrayList<>();
        for (GameObject e : game.getEntities()) {
            if (e instanceof LightObject && e.isVisible()) {
                lights.add((LightObject) e);
            }
        }

        // Transform mundo->dispositivo: captura a mesma que a camera aplicou as
        // entidades (base do g2d = identidade nos dois pipelines). Null = mundo==tela.
        AffineTransform worldToDevice = null;
        if (cameraApplied && cam != null) {
            Graphics2D probe = (Graphics2D) g2d.create();
            cam.applyTransform(probe);
            worldToDevice = probe.getTransform();
            probe.dispose();
        }

        lightMaskBuffer = LightObject.composeMask(lightMaskBuffer, width, height,
                game.getAmbientLight(), lights, worldToDevice);
        if (lightMaskBuffer != null) {
            g2d.drawImage(lightMaskBuffer, 0, 0, null);
        }

        // Brilho colorido por luz (tinge a area iluminada), no espaco do mundo.
        if (!lights.isEmpty()) {
            Graphics2D gg = (Graphics2D) g2d.create();
            try {
                gg.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                        java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                if (worldToDevice != null) gg.transform(worldToDevice);
                for (LightObject light : lights) {
                    Color c = light.getLightColor();
                    if (c == null) continue;
                    double r = Math.max(1, light.getRadius());
                    float inten = (float) Math.max(0, Math.min(1, light.getIntensity()));
                    if (inten <= 0) continue;
                    double cx = light.getX();
                    double cy = light.getY();
                    int a0 = (int) (130 * inten); // forca do brilho no centro
                    java.awt.RadialGradientPaint glow = new java.awt.RadialGradientPaint(
                            new java.awt.geom.Point2D.Double(cx, cy), (float) r,
                            new float[] {0f, 1f},
                            new Color[] {
                                new Color(c.getRed(), c.getGreen(), c.getBlue(), a0),
                                new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)
                            });
                    gg.setPaint(glow);
                    gg.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
                }
            } finally {
                gg.dispose();
            }
        }
    }

    /**
     * Renderiza mensagens de alerta na tela do editor
     */
    void renderAlerts(Graphics2D g2d) {
        if (game.editorReference == null) return;
        
        try {
            // Use reflection para obter os alertas do editor
            Class<?> editorClass = game.editorReference.getClass();
            java.lang.reflect.Method getAlertsMethod = editorClass.getMethod("getActiveAlerts");
            @SuppressWarnings("unchecked")
            java.util.List<Object> alerts = (java.util.List<Object>) getAlertsMethod.invoke(game.editorReference);
            
            if (alerts == null || alerts.isEmpty()) return;
            
            // Configurar font e cores
            Font alertFont = new Font("Courier New", Font.BOLD, 14);
            g2d.setFont(alertFont);
            
            int x = 15;
            int y = 35;
            int lineHeight = 22;
            
            // Renderizar cada alerta
            for (int i = 0; i < alerts.size() && i < 5; i++) {
                Object alertObj = alerts.get(i);
                
                // Obter a mensagem do alerta via reflection
                Class<?> alertClass = alertObj.getClass();
                java.lang.reflect.Field messageField = alertClass.getDeclaredField("message");
                messageField.setAccessible(true);
                String message = (String) messageField.get(alertObj);
                
                // Calcular opacidade baseado na idade do alerta
                java.lang.reflect.Field createdTimeField = alertClass.getDeclaredField("createdTime");
                createdTimeField.setAccessible(true);
                long createdTime = createdTimeField.getLong(alertObj);
                long age = System.currentTimeMillis() - createdTime;
                
                // Fade out no último segundo
                float opacity = 1.0f;
                if (age > 2000) { // Último 1 segundo de 3 segundos totais
                    opacity = 1.0f - ((age - 2000) / 1000.0f);
                }
                
                // Definir cor com transparência
                int alpha = (int)(255 * opacity);
                g2d.setColor(new java.awt.Color(0, 200, 100, alpha));
                
                // Desenhar caixa de fundo
                java.awt.FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(message);
                int textHeight = fm.getHeight();
                
                g2d.fillRect(x - 5, y - textHeight + 5, textWidth + 10, textHeight + 4);
                
                // Desenhar texto
                g2d.setColor(new java.awt.Color(255, 255, 255, alpha));
                g2d.drawString(message, x, y);
                
                y += lineHeight;
            }
        } catch (Exception e) {
            // Silenciosamente ignorar erros ao renderizar alertas
        }
    }
}
