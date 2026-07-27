package com.ignis.core;

import java.util.List;
import org.json.JSONObject;

/**
 * Suíte de testes unitários para a validação do TilemapRendererComponent e do algoritmo de Greedy Meshing.
 */
public class TilemapRendererComponentTest {

    public static void main(String[] args) {
        IgnisLogger.info("=== Testando TilemapRendererComponent e Greedy Meshing ===");

        try {
            Game game = new Game();

            // 1. Criação e Configuração da Grade de Tiles
            GameObject tilemapEntity = new GameObject("MainTilemap", game, 0, 0, 320, 240);
            TilemapRendererComponent tilemap = new TilemapRendererComponent("assets/tilesets/dungeon.png", 32, 32, 10, 10);
            tilemapEntity.addComponent(tilemap);
            game.addEntity(tilemapEntity);

            assert tilemap.getCols() == 10 : "Quantidade de colunas incorreta";
            assert tilemap.getRows() == 10 : "Quantidade de linhas incorreta";

            // 2. Manipulação de Tiles por Camada
            tilemap.setTile(0, 0, 0, 1);
            assert tilemap.getTile(0, 0, 0) == 1 : "Falha ao definir tile no ponto (0,0)";

            // Preenche um bloco 2x2 sólido na camada 0: (0,0), (1,0), (0,1), (1,1)
            tilemap.fillTiles(0, 0, 0, 1, 1, 5);

            // Preenche um bloco 1x2 sólido separado na mesma camada: (3,0), (3,1)
            tilemap.fillTiles(0, 3, 0, 3, 1, 5);

            // 3. Teste do Algoritmo de Greedy Meshing (Fusão de Colisores Compostos)
            List<ColliderComponent> greedyColliders = tilemap.generateGreedyColliders(0);

            IgnisLogger.info("Quantidade de colisores compostos gerados por Greedy Meshing: " + greedyColliders.size());

            // Em vez de 6 colisores individuais para os 6 tiles, o algoritmo deve gerar exatamente 2 caixas consolidadas!
            assert greedyColliders.size() == 2 : "Greedy Meshing deveria ter fundido os 6 tiles sólidos em exatamente 2 colisores retangulares consolidados!";

            ColliderComponent box1 = greedyColliders.get(0); // Bloco 2x2 (largura 64, altura 64)
            assert Double.compare(box1.getWidth(), 64.0) == 0 : "Largura do bloco 2x2 incorreta";
            assert Double.compare(box1.getHeight(), 64.0) == 0 : "Altura do bloco 2x2 incorreta";

            ColliderComponent box2 = greedyColliders.get(1); // Bloco 1x2 (largura 32, altura 64)
            assert Double.compare(box2.getWidth(), 32.0) == 0 : "Largura do bloco 1x2 incorreta";
            assert Double.compare(box2.getHeight(), 64.0) == 0 : "Altura do bloco 1x2 incorreta";

            // 4. Teste de Retrocompatibilidade do TilemapObject
            TilemapObject tmObject = new TilemapObject();
            tmObject.setTile(0, 2, 2, 8);
            assert tmObject.getTile(0, 2, 2) == 8 : "TilemapObject não repassou chamada ao TilemapRendererComponent interno corretamente";

            // 5. Teste de Serialização JSON (@Serialize e layers)
            JSONObject json = tilemap.saveProperties();
            assert json.optInt("cols") == 10 : "Falha ao serializar colunas";
            assert json.optJSONArray("layers") != null : "Falha ao serializar vetor de camadas JSON";

            TilemapRendererComponent loadedTilemap = new TilemapRendererComponent();
            loadedTilemap.loadProperties(json, null);
            assert loadedTilemap.getCols() == 10 : "Falha ao desserializar colunas";
            assert loadedTilemap.getTile(0, 0, 0) == 5 : "Falha ao desserializar tiles gravados na camada 0";

            IgnisLogger.info("=== Todos os testes do TilemapRendererComponent e Greedy Meshing passaram com sucesso! ===");

        } catch (Exception e) {
            IgnisLogger.error("Falha no teste do TilemapRendererComponent:", e);
            System.exit(1);
        }
    }
}
