package com.ignis.core;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * Renderizacao dos <b>overlays de edicao</b> desenhados sobre a cena: gizmos de
 * mover/rotacionar/escalar, o gizmo de collider e o retangulo de captura (frustum)
 * das cameras. Primeiro passo da decomposicao do {@link Game} (Fase F).
 *
 * <p>Este grupo e coeso e puramente visual: e chamado so do passe de render, le o
 * estado de selecao/ferramenta/arrasto do {@link Game} e nao muda nada. Vive no
 * mesmo pacote, entao le o estado de arrasto do gizmo (package-private) sem que o
 * {@code Game} precise expor API publica nova.</p>
 */
final class EditorGizmoRenderer {

    private final Game game;

    EditorGizmoRenderer(Game game) {
        this.game = game;
    }

    /**
     * Desenha o contorno da hitbox e as 8 alcas de redimensionamento do collider do
     * objeto selecionado (item 8b). Chamado em espaco de mundo (transform de camera
     * aplicada) pelo pipeline de render do editor.
     */
    void renderColliderGizmo(Graphics2D g2d, ColliderComponent cc) {
        double[] b = cc.getWorldBounds();
        if (b == null) return;
        double minX = b[0], minY = b[1], w = b[2], h = b[3];
        double wpp = game.editorWorldPerPixel();

        Color c = cc.isTrigger() ? new Color(80, 220, 120) : new Color(0, 200, 255);
        g2d.setColor(c);
        float dash = (float) (5.0 * wpp);
        g2d.setStroke(new BasicStroke((float) Math.max(1.0, 1.5 * wpp),
                BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f,
                new float[] { dash, dash }, 0f));
        if ("Sphere".equalsIgnoreCase(cc.getShape())) {
            g2d.drawOval((int) minX, (int) minY, (int) w, (int) h);
        } else {
            g2d.drawRect((int) minX, (int) minY, (int) w, (int) h);
        }

        double hs = 5.0 * wpp; // meia-aresta das alcas (tamanho ~constante em tela)
        g2d.setStroke(new BasicStroke((float) Math.max(1.0, wpp)));
        for (int i = 0; i < 8; i++) {
            double[] p = game.colliderHandlePoint(b, i);
            g2d.setColor(Color.WHITE);
            g2d.fillRect((int) (p[0] - hs), (int) (p[1] - hs), (int) (hs * 2), (int) (hs * 2));
            g2d.setColor(c);
            g2d.drawRect((int) (p[0] - hs), (int) (p[1] - hs), (int) (hs * 2), (int) (hs * 2));
        }
    }

    /**
     * Desenha o retangulo de captura (frustum 2D) de cada camera da cena em espaco de
     * mundo, com uma cruz no centro (posicao da camera) e o nome. A camera ativa recebe
     * destaque (amarelo preenchido); as demais ficam tracejadas em cinza. {@code designW/H}
     * e a resolucao de referencia usada para o tamanho da captura em zoom 1.
     */
    void renderCameraBounds(Graphics2D g2d, int designW, int designH, GameObject selected,
                                    AffineTransform screenTransform) {
        if (game.getCameras() == null || game.getCameras().isEmpty()) return;
        double wpp = game.editorWorldPerPixel();
        Camera active = game.getActiveCamera();
        java.awt.Font baseFont = g2d.getFont();
        AffineTransform worldTransform = g2d.getTransform();
        for (Camera cam : game.getCameras()) {
            if (cam == null || !cam.isVisible()) continue;
            double[] r = cam.getFrustumWorldRect(designW, designH);
            boolean isActive = (cam == active) || cam.isActiveCamera();
            boolean isSel = (cam == selected);
            Color col = isActive ? new Color(255, 210, 40) : new Color(165, 165, 175);

            float sw = (float) Math.max(1.0, (isActive ? 2.0 : 1.5) * wpp);
            if (isSel) {
                g2d.setStroke(new BasicStroke(sw));
            } else {
                float d = (float) (6.0 * wpp);
                g2d.setStroke(new BasicStroke(sw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[] { d, d }, 0f));
            }
            g2d.setColor(col);
            g2d.drawRect((int) r[0], (int) r[1], (int) r[2], (int) r[3]);
            if (isActive) {
                g2d.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 22));
                g2d.fillRect((int) r[0], (int) r[1], (int) r[2], (int) r[3]);
            }

            // Cruz no centro = posicao da camera.
            double cx = cam.getX(), cy = cam.getY();
            double cs = 8.0 * wpp;
            g2d.setColor(col);
            g2d.setStroke(new BasicStroke((float) Math.max(1.0, wpp)));
            g2d.drawLine((int) (cx - cs), (int) cy, (int) (cx + cs), (int) cy);
            g2d.drawLine((int) cx, (int) (cy - cs), (int) cx, (int) (cy + cs));

            // Rotulo em ESPACO DE TELA: a transform da camera inverte o eixo Y, o que
            // espelharia o texto se desenhado em espaco de mundo. O canto superior-
            // esquerdo do frustum na tela corresponde ao mundo (minX, maxY) por causa da
            // inversao de Y.
            String label = (cam.getName() != null ? cam.getName() : "Camera") + (isActive ? " (ativa)" : "");
            Point2D.Double topLeft = game.worldToScreen(r[0], r[1] + r[3]);
            g2d.setTransform(screenTransform != null ? screenTransform : new AffineTransform());
            g2d.setFont(baseFont.deriveFont(11f));
            g2d.setColor(col);
            g2d.drawString(label, (float) (topLeft.x + 4.0), (float) (topLeft.y + 13.0));
            g2d.setTransform(worldTransform);
        }
        g2d.setFont(baseFont);
    }

    /**
     * Draws text that appears correctly despite the inverted Y-axis.
     * Flips the text vertically before drawing so it appears right-side up.
     */
    private void drawWorldText(Graphics2D g2d, String text, double worldX, double worldY) {
        AffineTransform oldTransform = g2d.getTransform();
        // Move to text position, flip Y to make text appear correctly
        g2d.translate(worldX, worldY);
        g2d.scale(1, -1);
        g2d.drawString(text, 0, 0);
        g2d.setTransform(oldTransform);
    }

    /**
     * Renders the appropriate gizmo based on current tool
     */
    void renderGizmo(Graphics2D g2d) {
        if (game.getSelectedObject() == null)
            return;

        switch (game.getCurrentTool()) {
            case MOVE:
                renderMoveGizmo(g2d);
                break;
            case ROTATE:
                renderRotateGizmo(g2d);
                break;
            case SCALE:
                renderScaleGizmo(g2d);
                break;
        }
    }

    /**
     * Renders the move gizmo (X and Y arrows)
     */
    private void renderMoveGizmo(Graphics2D g2d) {
        int centerX = (int) game.getSelectedObject().getX() + game.getSelectedObject().getWidth() / 2;
        int centerY = (int) game.getSelectedObject().getY() + game.getSelectedObject().getHeight() / 2;
        
        // Get scaled gizmo dimensions
        int gizmoSize = game.getScaledGizmoSize();
        int arrowSize = game.getScaledGizmoArrowSize();

        double zoom = (game.getViewCamera() != null) ? game.getViewCamera().getZoom() : 1.0;
        int centerSize = (int)(10 / zoom);
        centerSize = Math.max(4, centerSize); // Minimum visible size

        // Determine colors based on drag or hover
        boolean xActive = (game.currentDragMode == Game.GizmoDragMode.AXIS_X || game.hoveredGizmoMode == Game.GizmoDragMode.AXIS_X);
        boolean yActive = (game.currentDragMode == Game.GizmoDragMode.AXIS_Y || game.hoveredGizmoMode == Game.GizmoDragMode.AXIS_Y);
        boolean cActive = (game.currentDragMode == Game.GizmoDragMode.CENTER || game.hoveredGizmoMode == Game.GizmoDragMode.CENTER);

        Color xColor = xActive ? new Color(255, 80, 80) : new Color(220, 40, 40);
        Color yColor = yActive ? new Color(80, 255, 80) : new Color(40, 200, 40);
        Color cColor = cActive ? new Color(255, 255, 150) : new Color(220, 220, 50);

        // --- 1. Draw Black Outline Background ---
        g2d.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(Color.BLACK);
        
        // X outline
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        Polygon arrowXOutline = new Polygon();
        arrowXOutline.addPoint(centerX + gizmoSize + arrowSize + 2, centerY);
        arrowXOutline.addPoint(centerX + gizmoSize - 3, centerY - arrowSize / 2 - 2);
        arrowXOutline.addPoint(centerX + gizmoSize - 3, centerY + arrowSize / 2 + 2);
        g2d.fillPolygon(arrowXOutline);

        // Y outline
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        Polygon arrowYOutline = new Polygon();
        arrowYOutline.addPoint(centerX, centerY + gizmoSize + arrowSize + 2);
        arrowYOutline.addPoint(centerX - arrowSize / 2 - 2, centerY + gizmoSize - 3);
        arrowYOutline.addPoint(centerX + arrowSize / 2 + 2, centerY + gizmoSize - 3);
        g2d.fillPolygon(arrowYOutline);

        // Center square outline
        g2d.fillRect(centerX - centerSize / 2 - 2, centerY - centerSize / 2 - 2, centerSize + 4, centerSize + 4);

        // --- 2. Draw Colored Foreground ---
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // X Foreground
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        Polygon arrowX = new Polygon();
        arrowX.addPoint(centerX + gizmoSize + arrowSize, centerY);
        arrowX.addPoint(centerX + gizmoSize - 2, centerY - arrowSize / 2);
        arrowX.addPoint(centerX + gizmoSize - 2, centerY + arrowSize / 2);
        g2d.fillPolygon(arrowX);

        // Y Foreground
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        Polygon arrowY = new Polygon();
        arrowY.addPoint(centerX, centerY + gizmoSize + arrowSize);
        arrowY.addPoint(centerX - arrowSize / 2, centerY + gizmoSize - 2);
        arrowY.addPoint(centerX + arrowSize / 2, centerY + gizmoSize - 2);
        g2d.fillPolygon(arrowY);

        // Center Foreground
        g2d.setColor(cColor);
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // Labels
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, "X", centerX + gizmoSize + arrowSize + 4, centerY - 4);
        drawWorldText(g2d, "Y", centerX - 4, centerY + gizmoSize + arrowSize + 4);
    }

    /**
     * Renders the rotate gizmo (circle)
     */
    private void renderRotateGizmo(Graphics2D g2d) {
        int centerX = (int) game.getSelectedObject().getX() + game.getSelectedObject().getWidth() / 2;
        int centerY = (int) game.getSelectedObject().getY() + game.getSelectedObject().getHeight() / 2;
        
        // Get scaled radius
        int rotateRadius = game.getScaledRotateGizmoRadius();

        boolean rActive = (game.currentDragMode == Game.GizmoDragMode.ROTATE || game.hoveredGizmoMode == Game.GizmoDragMode.ROTATE);
        Color circleColor = rActive ? new Color(100, 200, 255) : new Color(50, 150, 220);

        // 1. Black outline circle
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(centerX - rotateRadius, centerY - rotateRadius, rotateRadius * 2, rotateRadius * 2);

        // 2. Colored circle
        g2d.setColor(circleColor);
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.drawOval(centerX - rotateRadius, centerY - rotateRadius, rotateRadius * 2, rotateRadius * 2);

        // Rotation indicator line (shows current rotation)
        double radians = Math.toRadians(game.getSelectedObject().getRotation());
        int indicatorX = centerX + (int) (Math.cos(radians) * rotateRadius);
        int indicatorY = centerY + (int) (Math.sin(radians) * rotateRadius);
        
        // Indicator outline
        g2d.setColor(Color.BLACK);
        g2d.setStroke(new BasicStroke(4));
        g2d.drawLine(centerX, centerY, indicatorX, indicatorY);
        // Indicator line
        g2d.setColor(new Color(255, 150, 50));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(centerX, centerY, indicatorX, indicatorY);

        // Center point
        int centerPointSize = (int)(5 / (game.getViewCamera() != null ? game.getViewCamera().getZoom() : 1.0));
        centerPointSize = Math.max(3, centerPointSize);
        g2d.setColor(Color.BLACK);
        g2d.fillOval(centerX - centerPointSize - 1, centerY - centerPointSize - 1, (centerPointSize + 1) * 2, (centerPointSize + 1) * 2);
        g2d.setColor(circleColor);
        g2d.fillOval(centerX - centerPointSize, centerY - centerPointSize, centerPointSize * 2, centerPointSize * 2);

        // Rotation value label
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, String.format("%.1f\u00B0", game.getSelectedObject().getRotation()),
                centerX + rotateRadius + 5, centerY + 5);
    }

    /**
     * Renders the scale gizmo (arrows with square ends like move gizmo)
     */
    private void renderScaleGizmo(Graphics2D g2d) {
        int centerX = (int) game.getSelectedObject().getX() + game.getSelectedObject().getWidth() / 2;
        int centerY = (int) game.getSelectedObject().getY() + game.getSelectedObject().getHeight() / 2;
        int objW = game.getSelectedObject().getWidth();
        int objH = game.getSelectedObject().getHeight();
        
        // Get scaled gizmo dimensions
        int gizmoSize = game.getScaledGizmoSize();
        double zoom = (game.getViewCamera() != null) ? game.getViewCamera().getZoom() : 1.0;
        int squareSize = (int)(20 / zoom);
        squareSize = Math.max(4, squareSize);
        int centerSize = (int)(12 / zoom);
        centerSize = Math.max(6, centerSize);

        boolean xActive = (game.currentDragMode == Game.GizmoDragMode.SCALE_X || game.hoveredGizmoMode == Game.GizmoDragMode.SCALE_X);
        boolean yActive = (game.currentDragMode == Game.GizmoDragMode.SCALE_Y || game.hoveredGizmoMode == Game.GizmoDragMode.SCALE_Y);
        boolean uActive = (game.currentDragMode == Game.GizmoDragMode.SCALE_UNIFORM || game.hoveredGizmoMode == Game.GizmoDragMode.SCALE_UNIFORM);

        Color xColor = xActive ? new Color(255, 80, 80) : new Color(220, 40, 40);
        Color yColor = yActive ? new Color(80, 255, 80) : new Color(40, 200, 40);
        Color uColor = uActive ? new Color(255, 255, 150) : new Color(255, 220, 50);

        // --- 1. Draw Black Outline Background ---
        g2d.setStroke(new BasicStroke(6, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.setColor(Color.BLACK);
        
        // X outline
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        g2d.fillRect(centerX + gizmoSize - squareSize / 2 - 2, centerY - squareSize / 2 - 2, squareSize + 4, squareSize + 4);
        
        // Y outline
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        g2d.fillRect(centerX - squareSize / 2 - 2, centerY + gizmoSize - squareSize / 2 - 2, squareSize + 4, squareSize + 4);

        // Center outline
        g2d.fillRect(centerX - centerSize / 2 - 2, centerY - centerSize / 2 - 2, centerSize + 4, centerSize + 4);

        // --- 2. Draw Colored Foreground ---
        g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // X Foreground
        g2d.setColor(xColor);
        g2d.drawLine(centerX, centerY, centerX + gizmoSize, centerY);
        g2d.fillRect(centerX + gizmoSize - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX + gizmoSize - squareSize / 2, centerY - squareSize / 2, squareSize, squareSize);

        // Y Foreground
        g2d.setColor(yColor);
        g2d.drawLine(centerX, centerY, centerX, centerY + gizmoSize);
        g2d.fillRect(centerX - squareSize / 2, centerY + gizmoSize - squareSize / 2, squareSize, squareSize);
        g2d.setColor(Color.WHITE);
        g2d.drawRect(centerX - squareSize / 2, centerY + gizmoSize - squareSize / 2, squareSize, squareSize);

        // Center Foreground
        g2d.setColor(uColor);
        g2d.fillRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);
        g2d.setColor(Color.BLACK);
        g2d.drawRect(centerX - centerSize / 2, centerY - centerSize / 2, centerSize, centerSize);

        // Labels
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Dialog", Font.BOLD, 12));
        drawWorldText(g2d, "X", centerX + gizmoSize + squareSize + 2, centerY - 4);
        drawWorldText(g2d, "Y", centerX - 4, centerY + gizmoSize + squareSize + 4);

        // Size label
        g2d.setFont(new Font("Dialog", Font.PLAIN, 11));
        drawWorldText(g2d, objW + " x " + objH, centerX + gizmoSize + squareSize + 2, centerY - 20);
    }
}
