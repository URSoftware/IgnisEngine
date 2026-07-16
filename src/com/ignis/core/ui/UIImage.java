package com.ignis.core.ui;

import com.ignis.core.AssetResolver;
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
        TILE,
        /**
         * Nine-slice (9 fatias): os quatro cantos mantêm o tamanho original, as
         * bordas esticam num eixo e o miolo estica nos dois. Ideal para painéis
         * e botões com skin que não devem distorcer os cantos. Ver Fase D 3.10.
         */
        NINE_SLICE
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

    /**
     * Margens de corte do nine-slice (em px da imagem original), a partir de cada
     * borda. Só têm efeito quando {@code scaleMode == NINE_SLICE}. Ver Fase D 3.10.
     */
    private int sliceLeft = 0;
    private int sliceRight = 0;
    private int sliceTop = 0;
    private int sliceBottom = 0;
    
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
            File file = AssetResolver.resolve(imagePath);
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

            case NINE_SLICE:
                renderNineSlice(g, image, (int) width, (int) height,
                        sliceLeft, sliceRight, sliceTop, sliceBottom);
                if (!tint.equals(Color.WHITE)) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.3f));
                    g.setColor(tint);
                    g.fillRect(0, 0, (int) width, (int) height);
                }
                g.setComposite(oldComposite);
                drawBorder(g);
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
    
    /**
     * Desenha {@code img} no retângulo {@code dw x dh} (origem 0,0) em nove fatias:
     * cantos em tamanho fixo, bordas esticadas num eixo, miolo esticado nos dois.
     * As margens de origem são limitadas ao tamanho da imagem e as de destino são
     * reduzidas proporcionalmente quando o destino é menor que a soma dos cantos
     * (evita sobreposição/inversão das fatias, como o layout do CSS/Unity). Método
     * estático e sem estado para ser exercitável por teste de saída de render.
     */
    static void renderNineSlice(Graphics2D g, BufferedImage img, int dw, int dh,
                                int left, int right, int top, int bottom) {
        if (img == null || dw <= 0 || dh <= 0) return;
        int iw = img.getWidth();
        int ih = img.getHeight();

        // Margens de origem: cada par (esq+dir, topo+base) é limitado a deixar pelo
        // menos 1px de miolo esticável. Quando estouram, encolhem proporcionalmente
        // (evita cantos sobrepostos e o miolo degenerado que deixaria buracos no destino).
        int[] hor = clampPair(left, right, iw);
        int sl = hor[0];
        int sr = hor[1];
        int[] ver = clampPair(top, bottom, ih);
        int st = ver[0];
        int sb = ver[1];

        // Margens de destino: partem das de origem, mas encolhem proporcionalmente
        // se o destino é estreito/baixo demais para caber os dois cantos.
        int dl = sl;
        int dr = sr;
        if (dl + dr > dw && dl + dr > 0) {
            double f = (double) dw / (dl + dr);
            dl = (int) Math.floor(dl * f);
            dr = dw - dl;
        }
        int dt = st;
        int db = sb;
        if (dt + db > dh && dt + db > 0) {
            double f = (double) dh / (dt + db);
            dt = (int) Math.floor(dt * f);
            db = dh - dt;
        }

        // Fronteiras das três faixas em origem (sx) e destino (dx).
        int sx0 = 0, sx1 = sl, sx2 = iw - sr, sx3 = iw;
        int dx0 = 0, dx1 = dl, dx2 = dw - dr, dx3 = dw;
        int sy0 = 0, sy1 = st, sy2 = ih - sb, sy3 = ih;
        int dy0 = 0, dy1 = dt, dy2 = dh - db, dy3 = dh;

        int[] sxs = {sx0, sx1, sx2, sx3};
        int[] dxs = {dx0, dx1, dx2, dx3};
        int[] sys = {sy0, sy1, sy2, sy3};
        int[] dys = {dy0, dy1, dy2, dy3};

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int sxa = sxs[col], sxb = sxs[col + 1];
                int sya = sys[row], syb = sys[row + 1];
                int dxa = dxs[col], dxb = dxs[col + 1];
                int dya = dys[row], dyb = dys[row + 1];
                // Pula fatias degeneradas (origem ou destino de largura/altura zero).
                if (sxb <= sxa || syb <= sya || dxb <= dxa || dyb <= dya) continue;
                g.drawImage(img, dxa, dya, dxb, dyb, sxa, sya, sxb, syb, null);
            }
        }
    }

    /**
     * Limita um par de margens (dois lados opostos) ao tamanho {@code dim} da
     * imagem, garantindo pelo menos 1px de miolo entre elas. Se a soma estoura,
     * as duas encolhem proporcionalmente para {@code dim - 1}.
     */
    private static int[] clampPair(int a, int b, int dim) {
        int x = Math.max(0, Math.min(a, dim));
        int y = Math.max(0, Math.min(b, dim));
        int budget = Math.max(0, dim - 1); // deixa >=1px de miolo
        if (x + y > budget && x + y > 0) {
            int nx = (int) Math.floor((double) budget * x / (x + y));
            y = budget - nx;
            x = nx;
        }
        return new int[] {x, y};
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

    public int getSliceLeft() { return sliceLeft; }
    public int getSliceRight() { return sliceRight; }
    public int getSliceTop() { return sliceTop; }
    public int getSliceBottom() { return sliceBottom; }
    public void setSliceLeft(int v) { this.sliceLeft = Math.max(0, v); }
    public void setSliceRight(int v) { this.sliceRight = Math.max(0, v); }
    public void setSliceTop(int v) { this.sliceTop = Math.max(0, v); }
    public void setSliceBottom(int v) { this.sliceBottom = Math.max(0, v); }

    /** Define as quatro margens do nine-slice de uma vez (px da imagem original). */
    public void setSlices(int left, int right, int top, int bottom) {
        setSliceLeft(left);
        setSliceRight(right);
        setSliceTop(top);
        setSliceBottom(bottom);
    }

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
        json.put("sliceLeft", sliceLeft);
        json.put("sliceRight", sliceRight);
        json.put("sliceTop", sliceTop);
        json.put("sliceBottom", sliceBottom);
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
        img.sliceLeft = json.optInt("sliceLeft", 0);
        img.sliceRight = json.optInt("sliceRight", 0);
        img.sliceTop = json.optInt("sliceTop", 0);
        img.sliceBottom = json.optInt("sliceBottom", 0);
        if (img.imagePath != null && !img.imagePath.isEmpty()) {
            img.loadImage();
        }
        return img;
    }
}
