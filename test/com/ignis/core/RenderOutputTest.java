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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes de SAIDA de render (Fase C) sem GUI: desenha as entidades visuais num
 * {@link BufferedImage} e confere os pixels. Cobre o que os testes de modelo de
 * dados nao pegavam — orientacao (eixo Y invertido), mapeamento indice->regiao do
 * tileset e presenca do desenho. Usa o caminho {@code game == null} (sem camera),
 * evitando o {@code HeadlessException} do construtor de {@link Game}.
 */
class RenderOutputTest {

    private File dir;

    @BeforeEach
    void setup() throws Exception {
        dir = Files.createTempDirectory("ignis-render").toFile();
        AssetResolver.setProjectFolder(dir);
    }

    @AfterEach
    void teardown() {
        AssetResolver.setProjectFolder(null);
        AssetResolver.clearImageCache();
    }

    private static BufferedImage canvas(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    /** Escreve um tileset de 2 tiles 16x16 (esquerda vermelho, direita verde). */
    private File writeTwoTileTileset() throws Exception {
        BufferedImage ts = canvas(32, 16);
        Graphics2D g = ts.createGraphics();
        g.setColor(new Color(200, 0, 0));
        g.fillRect(0, 0, 16, 16);
        g.setColor(new Color(0, 160, 0));
        g.fillRect(16, 0, 16, 16);
        g.dispose();
        File sub = new File(dir, "assets/tilesets");
        sub.mkdirs();
        File f = new File(sub, "ts.png");
        ImageIO.write(ts, "png", f);
        return f;
    }

    @Test
    void tilemapDesenhaTilesCertosNasCelulasCertas() throws Exception {
        writeTwoTileTileset();
        TilemapObject tm = new TilemapObject();
        tm.configure("assets/tilesets/ts.png", 16, 16, 2, 1);
        tm.setTile(0, 0, 0, 0); // celula esquerda -> tile 0 (vermelho)
        tm.setTile(0, 1, 0, 1); // celula direita  -> tile 1 (verde)
        tm.setY(16); // origem no topo; linha 0 ocupa y [0..16] apos o flip

        BufferedImage out = canvas(32, 16);
        Graphics2D g = out.createGraphics();
        tm.render(g);
        g.dispose();

        assertEquals(new Color(200, 0, 0).getRGB(), out.getRGB(4, 8),
                "celula (0,0) deve mostrar o tile 0 (vermelho)");
        assertEquals(new Color(0, 160, 0).getRGB(), out.getRGB(20, 8),
                "celula (1,0) deve mostrar o tile 1 (verde)");
    }

    @Test
    void tilemapCelulaVaziaNaoDesenha() throws Exception {
        writeTwoTileTileset();
        TilemapObject tm = new TilemapObject();
        tm.configure("assets/tilesets/ts.png", 16, 16, 2, 1);
        tm.setTile(0, 0, 0, 0);       // so a celula esquerda
        // celula direita permanece EMPTY
        tm.setY(16);

        BufferedImage out = canvas(32, 16);
        Graphics2D g = out.createGraphics();
        tm.render(g);
        g.dispose();

        assertEquals(0, out.getRGB(20, 8) >>> 24,
                "celula vazia deve ficar transparente (alpha 0)");
    }

    @Test
    void backgroundLayerPreencheComCorSolida() {
        BackgroundLayer bg = new BackgroundLayer();
        bg.setColor(new Color(10, 20, 200));
        bg.setImagePath(null);
        bg.setX(0);
        bg.setY(0);
        bg.setWidth(20);
        bg.setHeight(20);

        BufferedImage out = canvas(20, 20);
        Graphics2D g = out.createGraphics();
        bg.render(g); // game == null -> preenche o retangulo com a cor
        g.dispose();

        assertEquals(new Color(10, 20, 200).getRGB(), out.getRGB(10, 10),
                "fundo solido deve preencher com a cor definida");
    }

    @Test
    void particleEmitterDesenhaAposBurst() {
        ParticleEmitter e = new ParticleEmitter();
        e.setX(20);
        e.setY(20);
        e.setWidth(0);
        e.setHeight(0);           // centro exatamente em (20,20)
        e.setSizeStart(12);
        e.setColorStart(new Color(255, 255, 0, 255));
        e.burst(1);               // 1 particula no centro, age 0

        BufferedImage out = canvas(40, 40);
        Graphics2D g = out.createGraphics();
        e.render(g);
        g.dispose();

        assertTrue((out.getRGB(20, 20) >>> 24) > 0,
                "apos burst, a particula deve deixar pixel visivel no centro do emissor");
    }
}
