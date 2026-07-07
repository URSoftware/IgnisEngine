package com.ignis.core.ui;

import com.ignis.core.IgnisLogger;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.json.JSONObject;

/**
 * UIImage - Componente para exibição de imagens na interface.
 * 
 * Suporta:
 * - Carregamento de imagens (PNG, JPG, GIF)
 * - Modos de escala (stretch, fit, fill, none)
 * - Tint de cor
 * - Transparência
 * 
 * Exemplo:
 * ```java
 * UIImage logo = new UIImage("assets/ui/logo.png", 100, 50);
 * logo.setScaleMode(UIImage.ScaleMode.FIT);
 * logo.setTint(new Color(255, 200, 200)); // Leve vermelho
 * canvas.addChild(logo);
 * ```
 */
public class UIImage extends UIComponent {

    // ==================== ENUMS ====================
    
    public enum ScaleMode {
        /** Estica a imagem para preencher */
        STRETCH,
        /** Mantém proporção, cabe inteiro */
        FIT,
        /** Mantém proporção, preenche tudo (pode cortar) */
        FILL,
        /** Tamanho original */
        NONE,
        /** Repete a imagem (tile) */
        TILE
    }
    
    // ==================== CAMPOS ====================
    
    private String imagePath;
    private transient BufferedImage image;
    private ScaleMode scaleMode = ScaleMode.FIT;
    
    /** Cor de tint (multiplica com a imagem) */
    private Color tint = Color.WHITE;
    
    /** Opacidade (0.0 a 1.0) */
    private float opacity = 1.0f;
    
    /** Se deve preservar a proporção original */
    private boolean preserveAspect = true;
    
    /** Flip horizontal/vertical */
    private boolean flipX = false;
    private boolean flipY = false;
    
    // ==================== CONSTRUTORES ====================
    
    public UIImage() {
        super("Image");
        initDefaults();
    }
    
    public UIImage(String imagePath) {
        super("Image");
        this.imagePath = imagePath;
        initDefaults();
        loadImage();
    }
    
    public UIImage(String imagePath, double x, double y) {
        super("Image", x, y, 100, 100);
        this.imagePath = imagePath;
        initDefaults();
        loadImage();
    }
    
    public UIImage(String imagePath, double x, double y, double width, double height) {
        super("Image", x, y, width, height);
        this.imagePath = imagePath;
        initDefaults();
        loadImage();
    }
    
    private void initDefaults() {
        this.backgroundColor = new Color(0, 0, 0, 0); // Transparente
        this.interactive = false;
    }
    
    // ==================== CARREGAMENTO ====================
    
    /**
     * Carrega a imagem do caminho especificado.
     */
    public void loadImage() {
        if (imagePath == null || imagePath.isEmpty()) {
            image = null;
            return;
        }
        
        try {
            File file = new File(imagePath);
            if (file.exists()) {
                image = ImageIO.read(file);
                
                // Ajustar tamanho se scaleMode == NONE
                if (scaleMode == ScaleMode.NONE && image != null) {
                    this.width = image.getWidth();
                    this.height = image.getHeight();
                }
            } else {
                // Tentar carregar como recurso
                var stream = getClass().getResourceAsStream(imagePath);
                if (stream != null) {
                    image = ImageIO.read(stream);
                    stream.close();
                }
            }
        } catch (Exception e) {
            IgnisLogger.error("[UIImage] Erro ao carregar imagem: " + e.getMessage());
            image = null;
        }
    }
    
    /**
     * Define a imagem diretamente.
     * @param image BufferedImage a usar
     */
    public void setImage(BufferedImage image) {
        this.image = image;
        if (scaleMode == ScaleMode.NONE && image != null) {
            this.width = image.getWidth();
            this.height = image.getHeight();
        }
    }
    
    // ==================== RENDERIZAÇÃO ====================
    
    @Override
    public void render(Graphics2D g) {
        // Desenhar fundo
        drawBackground(g);
        
        if (image == null) {
            // Desenhar placeholder
            g.setColor(new Color(100, 100, 100));
            g.fillRect(0, 0, (int) width, (int) height);
            g.setColor(Color.WHITE);
            g.drawString("No Image", 10, (int) (height / 2 + 5));
            return;
        }
        
        // Aplicar opacidade
        Composite oldComposite = g.getComposite();
        if (opacity < 1.0f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        }
        
        // Calcular dimensões baseado no modo de escala
        int imgW = image.getWidth();
        int imgH = image.getHeight();
        int drawX = 0, drawY = 0;
        int drawW = (int) width, drawH = (int) height;
        
        switch (scaleMode) {
            case STRETCH:
                // Estica para preencher completamente
                drawW = (int) width;
                drawH = (int) height;
                break;
                
            case FIT:
                // Mantém proporção, cabe inteiro
                double scaleX = width / imgW;
                double scaleY = height / imgH;
                double scale = Math.min(scaleX, scaleY);
                drawW = (int) (imgW * scale);
                drawH = (int) (imgH * scale);
                drawX = (int) ((width - drawW) / 2);
                drawY = (int) ((height - drawH) / 2);
                break;
                
            case FILL:
                // Mantém proporção, preenche tudo
                double fillScaleX = width / imgW;
                double fillScaleY = height / imgH;
                double fillScale = Math.max(fillScaleX, fillScaleY);
                drawW = (int) (imgW * fillScale);
                drawH = (int) (imgH * fillScale);
                drawX = (int) ((width - drawW) / 2);
                drawY = (int) ((height - drawH) / 2);
                break;
                
            case NONE:
                // Tamanho original, centralizado
                drawW = imgW;
                drawH = imgH;
                drawX = (int) ((width - drawW) / 2);
                drawY = (int) ((height - drawH) / 2);
                break;
                
            case TILE:
                // Repetir imagem
                for (int tx = 0; tx < width; tx += imgW) {
                    for (int ty = 0; ty < height; ty += imgH) {
                        g.drawImage(image, tx, ty, imgW, imgH, null);
                    }
                }
                g.setComposite(oldComposite);
                return;
        }
        
        // Aplicar flip se necessário
        int srcX1 = 0, srcY1 = 0, srcX2 = imgW, srcY2 = imgH;
        if (flipX) {
            srcX1 = imgW;
            srcX2 = 0;
        }
        if (flipY) {
            srcY1 = imgH;
            srcY2 = 0;
        }
        
        // Desenhar imagem
        g.drawImage(image, drawX, drawY, drawX + drawW, drawY + drawH,
                    srcX1, srcY1, srcX2, srcY2, null);
        
        // Aplicar tint se não for branco
        if (!tint.equals(Color.WHITE)) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.3f));
            g.setColor(tint);
            g.fillRect(drawX, drawY, drawW, drawH);
        }
        
        // Restaurar composite
        g.setComposite(oldComposite);
        
        // Desenhar borda
        drawBorder(g);
    }
    
    // ==================== GETTERS E SETTERS ====================
    
    public String getImagePath() { return imagePath; }
    public void setImagePath(String path) { 
        this.imagePath = path; 
        loadImage();
    }
    
    public BufferedImage getImage() { return image; }
    
    public ScaleMode getScaleMode() { return scaleMode; }
    public void setScaleMode(ScaleMode mode) { this.scaleMode = mode; }
    
    public Color getTint() { return tint; }
    public void setTint(Color tint) { this.tint = tint; }
    
    public float getOpacity() { return opacity; }
    public void setOpacity(float opacity) { 
        this.opacity = Math.max(0, Math.min(1, opacity)); 
    }
    
    public boolean isPreserveAspect() { return preserveAspect; }
    public void setPreserveAspect(boolean preserve) { this.preserveAspect = preserve; }
    
    public boolean isFlipX() { return flipX; }
    public void setFlipX(boolean flip) { this.flipX = flip; }
    
    public boolean isFlipY() { return flipY; }
    public void setFlipY(boolean flip) { this.flipY = flip; }
    
    @Override
    public String getType() {
        return "Image";
    }
    
    // ==================== SERIALIZAÇÃO ====================
    
    @Override
    public JSONObject toJSON() {
        JSONObject json = super.toJSON();
        json.put("imagePath", imagePath != null ? imagePath : "");
        json.put("scaleMode", scaleMode.name());
        json.put("tint", colorToHex(tint));
        json.put("opacity", opacity);
        json.put("preserveAspect", preserveAspect);
        json.put("flipX", flipX);
        json.put("flipY", flipY);
        return json;
    }
    
    public static UIImage fromJSON(JSONObject json) {
        UIImage img = new UIImage();
        img.loadFromJSON(json);
        img.imagePath = json.optString("imagePath", null);
        try {
            img.scaleMode = ScaleMode.valueOf(json.optString("scaleMode", "FIT"));
        } catch (Exception e) { img.scaleMode = ScaleMode.FIT; }
        img.tint = hexToColor(json.optString("tint", "#FFFFFF"));
        img.opacity = (float) json.optDouble("opacity", 1.0);
        img.preserveAspect = json.optBoolean("preserveAspect", true);
        img.flipX = json.optBoolean("flipX", false);
        img.flipY = json.optBoolean("flipY", false);
        if (img.imagePath != null && !img.imagePath.isEmpty()) {
            img.loadImage();
        }
        return img;
    }
}
