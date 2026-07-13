package com.ignis.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regiao de spritesheet/atlas (Fase C): Texture2D recorta a sub-imagem certa a
 * partir do sufixo no caminho ({@code #x,y,w,h} e {@code @col,row,tw,th}), e o
 * AssetResolver compartilha a decodificacao do arquivo-base entre as regioes.
 */
class SpriteRegionTest {

    private File dir;
    private File sheet;

    @BeforeEach
    void setup() throws Exception {
        dir = Files.createTempDirectory("ignis-atlas").toFile();
        AssetResolver.setProjectFolder(dir);
        // Spritesheet 4x2 de tiles 16x16: cada celula com uma cor distinta,
        // determinada por (col,row) para verificarmos o recorte por posicao.
        int tile = 16, cols = 4, rows = 2;
        BufferedImage img = new BufferedImage(cols * tile, rows * tile, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g.setColor(new Color(c * 60, r * 120, 30 + c + r));
                g.fillRect(c * tile, r * tile, tile, tile);
            }
        }
        g.dispose();
        File sub = new File(dir, "assets/sprites");
        sub.mkdirs();
        sheet = new File(sub, "atlas.png");
        ImageIO.write(img, "png", sheet);
    }

    @AfterEach
    void teardown() {
        AssetResolver.setProjectFolder(null);
        try {
            sheet.delete();
            new File(dir, "assets/sprites").delete();
            new File(dir, "assets").delete();
            dir.delete();
        } catch (Exception ignore) {
            // limpeza best-effort
        }
    }

    @Test
    void regiaoPorRetanguloRecortaSubImagem() {
        Texture2D tex = new Texture2D("assets/sprites/atlas.png#16,0,16,16");
        assertNotNull(tex.getImage(), "regiao deve carregar");
        assertEquals(16, tex.getImage().getWidth());
        assertEquals(16, tex.getImage().getHeight());
        // Celula (col=1,row=0) => cor (60, 0, 31)
        assertEquals(new Color(60, 0, 31).getRGB(), tex.getImage().getRGB(0, 0));
    }

    @Test
    void regiaoPorGradeEquivaleAoRetangulo() {
        Texture2D grid = new Texture2D("assets/sprites/atlas.png@2,1,16,16");
        assertNotNull(grid.getImage());
        assertEquals(16, grid.getImage().getWidth());
        // Celula (col=2,row=1) => cor (120, 120, 33)
        assertEquals(new Color(120, 120, 33).getRGB(), grid.getImage().getRGB(0, 0));
    }

    @Test
    void caminhoComRegiaoSobreviveNoGetPath() {
        String p = "assets/sprites/atlas.png#0,0,16,16";
        assertEquals(p, new Texture2D(p).getPath(),
                "o caminho com regiao deve ser preservado (round-trip via serializacao do sprite)");
    }

    @Test
    void imagemInteiraSemSufixoContinuaFuncionando() {
        Texture2D full = new Texture2D("assets/sprites/atlas.png");
        assertNotNull(full.getImage());
        assertEquals(64, full.getImage().getWidth());
        assertEquals(32, full.getImage().getHeight());
    }

    @Test
    void regiaoInvalidaNaoQuebraECaiParaImagemInteira() {
        Texture2D bad = new Texture2D("assets/sprites/atlas.png#lixo");
        assertNotNull(bad.getImage(), "sufixo invalido deve cair para a imagem inteira");
        assertEquals(64, bad.getImage().getWidth());
    }
}
