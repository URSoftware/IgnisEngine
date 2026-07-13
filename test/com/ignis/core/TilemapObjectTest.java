package com.ignis.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tilemap (Fase C): modelo de dados (set/get/fill de tiles, limites, camadas) e
 * round-trip da grade multi-camada pela cena. Sem GUI — exercita a estrutura,
 * nao o render.
 */
class TilemapObjectTest {

    @Test
    void setEGetTileNaMesmaCamada() {
        TilemapObject tm = new TilemapObject();
        tm.configure("assets/tilesets/t.png", 16, 16, 10, 8);
        tm.setTile(0, 3, 4, 7);
        assertEquals(7, tm.getTile(0, 3, 4));
        assertEquals(TilemapObject.EMPTY, tm.getTile(0, 0, 0), "celula nao pintada e vazia");
    }

    @Test
    void foraDosLimitesEIgnorado() {
        TilemapObject tm = new TilemapObject();
        tm.configure("assets/tilesets/t.png", 16, 16, 5, 5);
        tm.setTile(0, -1, 0, 3);      // coluna negativa
        tm.setTile(0, 0, 99, 3);      // linha fora
        tm.setTile(0, 100, 100, 3);   // ambos fora
        assertEquals(TilemapObject.EMPTY, tm.getTile(0, 100, 100), "fora dos limites nao grava");
        assertEquals(TilemapObject.EMPTY, tm.getTile(0, 0, 0), "nada vazou para dentro da grade");
    }

    @Test
    void fillTilesPintaRetangulo() {
        TilemapObject tm = new TilemapObject();
        tm.configure("assets/tilesets/t.png", 16, 16, 10, 10);
        tm.fillTiles(0, 2, 2, 4, 5, 9);
        assertEquals(9, tm.getTile(0, 2, 2));
        assertEquals(9, tm.getTile(0, 4, 5));
        assertEquals(9, tm.getTile(0, 3, 4));
        assertEquals(TilemapObject.EMPTY, tm.getTile(0, 1, 1), "fora do retangulo permanece vazio");
        assertEquals(TilemapObject.EMPTY, tm.getTile(0, 5, 5), "fora do retangulo permanece vazio");
    }

    @Test
    void camadasSaoIndependentes() {
        TilemapObject tm = new TilemapObject();
        tm.configure("assets/tilesets/t.png", 16, 16, 6, 6);
        int layer1 = tm.addLayer();
        assertEquals(2, tm.getLayerCount(), "configure cria 1 camada, addLayer cria a 2a");
        tm.setTile(0, 1, 1, 3);
        tm.setTile(layer1, 1, 1, 8);
        assertEquals(3, tm.getTile(0, 1, 1));
        assertEquals(8, tm.getTile(layer1, 1, 1), "camadas nao compartilham celulas");
    }

    @Test
    void gradeMultiCamadaSobreviveAoRoundTrip() {
        Scene scene = new Scene("T");
        TilemapObject tm = new TilemapObject();
        tm.setName("Mapa");
        tm.configure("assets/tilesets/dungeon.png", 24, 24, 12, 9);
        tm.addLayer();
        tm.setTile(0, 0, 0, 5);
        tm.setTile(0, 11, 8, 2);
        tm.fillTiles(1, 3, 3, 6, 6, 4);
        scene.addEntity(tm);

        Scene loaded = Scene.fromJSON(scene.toJSON(), null);
        GameObject back = loaded.findEntityByName("Mapa");
        assertNotNull(back);
        assertInstanceOf(TilemapObject.class, back);
        TilemapObject b = (TilemapObject) back;
        assertEquals("assets/tilesets/dungeon.png", b.getTilesetPath());
        assertEquals(24, b.getTileW());
        assertEquals(12, b.getCols());
        assertEquals(9, b.getRows());
        assertEquals(2, b.getLayerCount());
        assertEquals(5, b.getTile(0, 0, 0));
        assertEquals(2, b.getTile(0, 11, 8));
        assertEquals(4, b.getTile(1, 4, 4), "tiles da 2a camada sobrevivem");
        assertEquals(TilemapObject.EMPTY, b.getTile(1, 0, 0));
    }

    @Test
    void mapeamentoMundoParaCelula() {
        // Nucleo da ferramenta de pintura: converter coordenada de mundo em (col,row).
        // Origem (x,y) e o canto superior-esquerdo; linhas crescem para baixo (Y do
        // mundo para cima), entao row = (y - worldY)/tileH.
        TilemapObject tm = new TilemapObject();
        tm.configure("assets/tilesets/t.png", 32, 32, 8, 8);
        tm.setX(100);
        tm.setY(200);
        // Coluna: (worldX - 100)/32.
        assertEquals(0, tm.cellColAtWorld(100));
        assertEquals(0, tm.cellColAtWorld(131));
        assertEquals(1, tm.cellColAtWorld(132));
        assertEquals(3, tm.cellColAtWorld(100 + 3 * 32 + 5));
        // Linha: (200 - worldY)/32. worldY=200 -> row 0; worldY logo abaixo -> row 1.
        assertEquals(0, tm.cellRowAtWorld(200));
        assertEquals(0, tm.cellRowAtWorld(169));  // 200-169=31 -> row 0
        assertEquals(1, tm.cellRowAtWorld(168));  // 200-168=32 -> row 1
        assertEquals(2, tm.cellRowAtWorld(200 - 2 * 32 - 5));
    }

    @Test
    void tilemapNaoECullablePorAabb() {
        assertFalse(new TilemapObject().isCullable(),
                "tilemap faz culling proprio por tile — nao deve ser cortado pelo AABB");
    }
}
