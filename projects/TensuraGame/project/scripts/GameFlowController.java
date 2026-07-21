import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

/**
 * Entrada principal do jogo: menu -> roteamento de modo (contrato da fatia
 * Caverna do Selo). A arena antiga (TensuraGameController) so roda como
 * HordeEncounter de depuracao, atras da tecla F9 — nunca no fluxo padrao.
 *
 * <p>Passo 1 do plano de engenharia (ver vault, tensura-game-plano-engenharia-
 * -software.md): a exploracao (mapa, jogador, interacao, dialogo) e as duas
 * cutscenes (despertar e encontro com Veldora) foram extraidas para
 * ExplorationDirector.java e CutsceneDirector.java, cada uma anexada ao seu
 * proprio GameObject. GameFlowController encolheu para menu + roteamento de
 * FlowState; nao volta a crescer como god-class.</p>
 *
 * <p>Scripts do Ignis sao compilados isoladamente — nenhum enxerga a classe
 * de outro arquivo, mesmo ja compilada (com.ignis.core.ScriptManager). Por
 * isso a coordenacao com os dois diretores usa sceneDispatcher (protected em
 * IgnisScript), nunca referencia direta. Cada sinal enviado/recebido aqui tem
 * o par exato em ExplorationDirector.java ou CutsceneDirector.java — mudar
 * uma string de sinal sem mudar a outra ponta quebra a coordenacao
 * silenciosamente (o compilador nao acusa, sao strings).</p>
 */
public final class GameFlowController extends IgnisScript {

    private static final String MAP_DATA = "data/cave-seal-map.json";
    // Reaproveita o objeto/controller ja existente da arena em vez de duplicar —
    // TensuraGameController continua intocado, so deixa de ser o script padrao.
    private static final String HORDE_DEBUG_OBJECT = "TensuraGameDirector";
    private static final String[] HORDE_BACKGROUND_OBJECTS = { "JuraForestFloor", "MagiculeMotes" };
    // Objetos estaticos da cena da Caverna (tilemaps + props) — ficam sempre na cena
    // (nao ha troca real de cena, ver nota abaixo), mas so devem aparecer durante a
    // EXPLORACAO. Sem isto eles vazavam atras/abaixo do menu (revisao visual do Codex).
    private static final String[] CAVE_SCENE_OBJECTS = {
            "CaveBackdrop", "CaveTilemapAwakening", "CaveTilemapGallery",
            "CaveCrystalCyanProp", "MagiculeClusterProp", "SealFragmentProp", "CaveExitGlowProp"
    };
    private static final String EXPLORATION_DIRECTOR_OBJECT = "ExplorationDirector";
    private static final String CUTSCENE_DIRECTOR_OBJECT = "CutsceneDirector";
    private static final String GOBLIN_CONTACT_DIRECTOR_OBJECT = "GoblinContactDirector";
    // Fundo da Floresta de Jura (reaproveita o objeto do Horde debug como chao da
    // floresta) e o NPC goblin conversavel — ambos assets/posicao do Codex. Este
    // fluxo so alterna a VISIBILIDADE deles nas transicoes; nao os posiciona.
    // MagiculeMotes NAO entra aqui: continua exclusivo do Horde debug.
    private static final String FOREST_BACKDROP_OBJECT = "JuraForestFloor";
    private static final String GOBLIN_NPC_OBJECT = "GoblinScoutNPC";

    // O motor JA suporta cenas de verdade (IgnisScript.loadScene, ver SceneHost/
    // SceneTools no MCP) — esta fatia ainda nao foi migrada para usa-las e continua
    // com as areas no MESMO mundo, por deslocamento manual. O
    // dominio permanece so em coordenadas locais-a-area (contrato "dominio sem
    // paths/mundo") e este mapa de deslocamento e a UNICA peca que sabe onde cada
    // area foi desenhada. Migrar para cave_awakening/cave_gallery como cenas
    // separadas (com copy_object_to_scene) e trabalho futuro, nao deste checkpoint.
    //
    // Duplicado (de proposito) em ExplorationDirector.java: e dado estatico de
    // layout de nivel, nao logica — se uma area nova entrar no mapa, os dois
    // lugares precisam mudar juntos.
    private static final Map<String, double[]> AREA_OFFSETS = Map.of(
            "cave_awakening", new double[] {0, 0},
            "cave_gallery", new double[] {900, 0});

    // Sinais enviados para os diretores (ver o par exato em cada arquivo).
    private static final String SIGNAL_ENTER_AWAKENING_CUTSCENE = "TENSURA_ENTER_AWAKENING_CUTSCENE";
    private static final String SIGNAL_ENTER_EXPLORATION = "TENSURA_ENTER_EXPLORATION";
    private static final String SIGNAL_ENTER_VELDORA_ENCOUNTER = "TENSURA_ENTER_VELDORA_ENCOUNTER";
    private static final String SIGNAL_EXPLORATION_ACTIVATE = "TENSURA_EXPLORATION_ACTIVATE";
    private static final String SIGNAL_ENTER_GOBLIN_CONTACT = "TENSURA_ENTER_GOBLIN_CONTACT";
    private static final String SIGNAL_ENTER_FOREST_EXPLORATION = "TENSURA_ENTER_FOREST_EXPLORATION";
    // Sinais recebidos dos diretores.
    private static final String SIGNAL_AWAKENING_CUTSCENE_COMPLETE = "TENSURA_AWAKENING_CUTSCENE_COMPLETE";
    private static final String SIGNAL_REQUEST_VELDORA_ENCOUNTER = "TENSURA_REQUEST_VELDORA_ENCOUNTER";
    private static final String SIGNAL_VELDORA_ENCOUNTER_COMPLETE = "TENSURA_VELDORA_ENCOUNTER_COMPLETE";
    private static final String SIGNAL_REQUEST_GOBLIN_CONTACT = "TENSURA_REQUEST_GOBLIN_CONTACT";
    private static final String SIGNAL_GOBLIN_CONTACT_COMPLETE = "TENSURA_GOBLIN_CONTACT_COMPLETE";

    private JSONObject mapRoot;
    private GameObject player;
    private FlowState state = FlowState.MENU;
    private boolean veldoraEncounterCompleted;
    private boolean goblinContactCompleted;

    private UIPanel menuBackground;
    private UILabel menuTitle;
    private UILabel menuSubtitle;
    private UIButton startButton;
    private UILabel objectiveLabel;
    private UILabel debugHintLabel;

    @Override
    public void start() {
        setCameraZoom(1.6);
        setMusicVolume(0.28f);
        setSfxVolume(0.6f);
        playMusic("assets/music/cave_seal_ambience.wav", true);

        mapRoot = readJson(MAP_DATA);

        // Rimuru e um ator persistente da cena para permanecer visivel/editavel na
        // Hierarchy. O fluxo adota essa unica instancia e controla sua aparicao;
        // nao deixa um sprite estatico para tras quando a exploracao comeca.
        player = findObject("Rimuru");
        if (player != null) {
            player.setVisible(false);
            player.setOpacity(1);
        }

        disableHordeDebug();
        showMainMenu();

        onSceneSignal(SIGNAL_AWAKENING_CUTSCENE_COMPLETE, payload -> completeAwakening());
        onSceneSignal(SIGNAL_REQUEST_VELDORA_ENCOUNTER, payload -> beginVeldoraEncounter());
        onSceneSignal(SIGNAL_VELDORA_ENCOUNTER_COMPLETE, payload -> completeVeldoraEncounter());
        onSceneSignal(SIGNAL_REQUEST_GOBLIN_CONTACT, payload -> beginGoblinContact());
        onSceneSignal(SIGNAL_GOBLIN_CONTACT_COMPLETE, payload -> completeGoblinContact());

        log("TensuraGame pronto: Caverna do Selo na tela inicial.");
    }

    @Override
    public void tick() {
        if (state == FlowState.MENU) {
            layoutMenu();
            if (isKeyJustPressed("ENTER")) beginAwakening();
            if (isKeyJustPressed("F9")) activateHordeDebug();
            return;
        }
        if (state != FlowState.EXPLORATION) return;
        // CUTSCENE e VELDORA_ENCOUNTER nao precisam de nada aqui: CutsceneDirector
        // tem seu proprio tick() e le input diretamente, ja que e um IgnisScript
        // independente anexado ao proprio objeto.
        layoutHud();
        if (isKeyJustPressed("F9")) activateHordeDebug();
    }

    // ==================== Menu ====================

    private void showMainMenu() {
        clearUI();
        state = FlowState.MENU;
        setCaveSceneObjectsVisible(false);
        // O fundo da floresta e o NPC goblin nunca vazam para o menu (revisao do Codex).
        setForestBackdropVisible(false);
        setGoblinNpcVisible(false);
        if (player != null) player.setVisible(false);
        menuBackground = createPanel(0, 0, 960, 540);
        setUIColors(menuBackground, new Color(4, 12, 18, 230), null, new Color(60, 140, 165));
        menuTitle = createLabel("TENSURA GAME", 0, 0, 620, 60);
        menuTitle.setAlignment(UILabel.Alignment.CENTER);
        menuTitle.setFont("SansSerif", Font.BOLD, 38);
        menuTitle.setTextColor(new Color(216, 250, 255));
        menuTitle.setOutline(true, new Color(9, 47, 61), 2);
        menuSubtitle = createLabel("A CAVERNA DO SELO", 0, 0, 460, 32);
        menuSubtitle.setAlignment(UILabel.Alignment.CENTER);
        menuSubtitle.setFont("SansSerif", Font.BOLD, 16);
        menuSubtitle.setTextColor(new Color(255, 222, 137));
        startButton = createButton("DESPERTAR", 0, 0, 240, 58);
        startButton.setColorScheme(new Color(35, 147, 168), new Color(50, 177, 198), new Color(24, 112, 132));
        startButton.setBorderColor(new Color(190, 244, 249));
        startButton.setBorderWidth(2);
        startButton.setOnClick(this::beginAwakening);
        layoutMenu();
    }

    private void layoutMenu() {
        double width = Math.max(480, getGame().getWidth());
        double height = Math.max(360, getGame().getHeight());
        menuBackground.setSize(width, height);
        menuTitle.setSize(Math.min(620, width - 40), 60);
        menuTitle.setPosition((width - menuTitle.getWidth()) / 2.0, Math.max(28, height * 0.30));
        menuSubtitle.setPosition((width - 460) / 2.0, Math.max(96, height * 0.42));
        startButton.setPosition((width - 240) / 2.0, Math.min(height - 82, height * 0.60));
    }

    // ==================== Roteamento de modo ====================

    private void beginAwakening() {
        if (state != FlowState.MENU) return;
        String startAreaId = mapRoot.getString("startArea");
        JSONObject startAreaJson = findAreaJson(mapRoot, startAreaId);
        double spawnX = startAreaJson.getDouble("spawnX");
        double spawnY = startAreaJson.getDouble("spawnY");
        double[] startOffset = AREA_OFFSETS.getOrDefault(startAreaId, new double[] {0, 0});
        double worldSpawnX = startOffset[0] + spawnX;
        double worldSpawnY = startOffset[1] + spawnY;

        clearUI();
        setCaveSceneObjectsVisible(true);
        // A caverna nunca mostra o chao da floresta nem o goblin (revisao do Codex).
        setForestBackdropVisible(false);
        setGoblinNpcVisible(false);
        if (player == null) {
            player = findObject("Rimuru");
        }
        if (player == null) {
            player = getGame().instantiatePrefab("RuntimeVisual", worldSpawnX, worldSpawnY);
            if (player == null) {
                player = new GameObject("Rimuru", getGame(), worldSpawnX, worldSpawnY, 28, 28);
                getGame().addEntity(player);
            }
        }
        player.setName("Rimuru");
        player.setTag("tensura_player");
        player.setWidth(28);
        player.setHeight(28);
        player.setVisible(true);
        player.setZIndex(30);
        player.setX(worldSpawnX - player.getWidth() / 2.0);
        player.setY(worldSpawnY - player.getHeight() / 2.0);
        sceneDispatcher.enqueue(SIGNAL_ENTER_AWAKENING_CUTSCENE, null);
        state = FlowState.CUTSCENE;
    }

    private void completeAwakening() {
        clearUI();
        setupExplorationHud("Explore a Caverna do Selo. [E] interagir");
        sceneDispatcher.enqueue(SIGNAL_ENTER_EXPLORATION, null);
        // So entra em EXPLORATION depois que TUDO acima deu certo — se algo no meio
        // lancar excecao, o tick() continua fora do ramo de exploracao em vez de
        // rodar sobre um estado que nunca terminou de montar (era o NPE que
        // derrubava o loop do jogo: "this.simulation is null").
        state = FlowState.EXPLORATION;
    }

    private void setupExplorationHud(String objective) {
        objectiveLabel = createLabel(objective, 0, 20, 620, 26);
        objectiveLabel.setFont("SansSerif", Font.BOLD, 14);
        objectiveLabel.setTextColor(new Color(255, 230, 147));
        objectiveLabel.setOutline(true, new Color(22, 14, 9), 1);
        debugHintLabel = createLabel("F9: HordeEncounter (debug)", 0, 0, 240, 20);
        debugHintLabel.setFont("SansSerif", Font.PLAIN, 11);
        debugHintLabel.setTextColor(new Color(140, 170, 180));
        layoutHud();
    }

    private void layoutHud() {
        double width = Math.max(480, getGame().getWidth());
        objectiveLabel.setPosition((width - objectiveLabel.getWidth()) / 2.0, 18);
        debugHintLabel.setPosition(width - 250, 18);
    }

    private void beginVeldoraEncounter() {
        if (state != FlowState.EXPLORATION || veldoraEncounterCompleted) return;
        clearUI();
        double[] offset = AREA_OFFSETS.getOrDefault("cave_gallery", new double[] {0, 0});
        sceneDispatcher.enqueue(SIGNAL_ENTER_VELDORA_ENCOUNTER, new double[] {offset[0] + 176, offset[1] + 80});
        state = FlowState.VELDORA_ENCOUNTER;
    }

    private void completeVeldoraEncounter() {
        if (veldoraEncounterCompleted) return;
        veldoraEncounterCompleted = true;
        clearUI();
        setupExplorationHud("Veldora esta com voce. Encontre a saida da caverna.");
        sceneDispatcher.enqueue(SIGNAL_EXPLORATION_ACTIVATE, null);
        state = FlowState.EXPLORATION;
    }

    // ==================== Saida da caverna + contato goblin ====================

    private void beginGoblinContact() {
        // So depois do Veldora e uma vez so: a guarda espelha beginVeldoraEncounter.
        if (state != FlowState.EXPLORATION || !veldoraEncounterCompleted || goblinContactCompleted) return;
        clearUI();
        // Antes do beat forest_threshold: a Caverna some e o chao da floresta entra,
        // para a cutscene NAO acontecer sobre a caverna (revisao do Codex). O NPC
        // goblin so aparece na exploracao final; durante a cutscene o grupo e um
        // visual de runtime do proprio GoblinContactDirector.
        setCaveSceneObjectsVisible(false);
        setForestBackdropVisible(true);
        setGoblinNpcVisible(false);
        sceneDispatcher.enqueue(SIGNAL_ENTER_GOBLIN_CONTACT, null);
        state = FlowState.GOBLIN_CONTACT;
    }

    private void completeGoblinContact() {
        if (goblinContactCompleted) return;
        goblinContactCompleted = true;
        clearUI();
        // Rota da aldeia desbloqueada (goblin_village_route_unlocked): o jogo termina
        // em exploracao conversacional da floresta, NUNCA no HordeEncounter. A caverna
        // segue oculta, o chao da floresta permanece e o NPC goblin aparece para a
        // conversa. Layout/arte final do NPC e do chao sao do Codex.
        setCaveSceneObjectsVisible(false);
        setForestBackdropVisible(true);
        setGoblinNpcVisible(true);
        setupExplorationHud("Floresta de Jura: fale com o goblin para seguir ate a aldeia. [E]");
        sceneDispatcher.enqueue(SIGNAL_ENTER_FOREST_EXPLORATION, null);
        state = FlowState.EXPLORATION;
    }

    private void setForestBackdropVisible(boolean visible) {
        GameObject backdrop = findObject(FOREST_BACKDROP_OBJECT);
        if (backdrop != null) backdrop.setVisible(visible);
    }

    private void setGoblinNpcVisible(boolean visible) {
        GameObject npc = findObject(GOBLIN_NPC_OBJECT);
        if (npc != null) npc.setVisible(visible);
    }

    // ==================== HordeEncounter (debug) ====================

    // O compilador de scripts do Ignis compila cada arquivo isoladamente contra as
    // classes ja compiladas (nao um lote multi-arquivo) — referenciar a classe
    // TensuraGameController diretamente aqui falha com "cannot find symbol" quando
    // ela ainda nao foi compilada nesta rodada. Localizar pelo NOME do script evita
    // qualquer dependencia de compilacao entre os dois arquivos.
    private static final String HORDE_SCRIPT_NAME = "TensuraGameController";

    private void disableHordeDebug() {
        setHordeBackgroundVisible(false);
        setHordeScriptEnabled(false);
    }

    private void activateHordeDebug() {
        GameObject hordeObject = findObject(HORDE_DEBUG_OBJECT);
        if (hordeObject == null) {
            log("HordeEncounter (debug) nao existe na cena.");
            return;
        }
        clearUI();
        setEnabled(false);
        // ExplorationDirector/CutsceneDirector sao scripts independentes agora —
        // desativar so este script (acima) nao os para mais; sem isto eles
        // continuariam ticando por baixo do HordeEncounter (antes da extracao,
        // eram chamados manualmente daqui, entao desativar-se bastava).
        setObjectScriptsEnabled(EXPLORATION_DIRECTOR_OBJECT, false);
        setObjectScriptsEnabled(CUTSCENE_DIRECTOR_OBJECT, false);
        setObjectScriptsEnabled(GOBLIN_CONTACT_DIRECTOR_OBJECT, false);
        setCaveSceneObjectsVisible(false);
        if (player != null) player.setVisible(false);
        setGoblinNpcVisible(false);
        setHordeBackgroundVisible(true);
        setHordeScriptEnabled(true);
        log("HordeEncounter (debug) ativado via F9.");
    }

    private void setObjectScriptsEnabled(String objectName, boolean enabled) {
        GameObject object = findObject(objectName);
        if (object == null) return;
        for (IgnisScript script : object.getScripts()) {
            script.setEnabled(enabled);
        }
    }

    private void setCaveSceneObjectsVisible(boolean visible) {
        for (String name : CAVE_SCENE_OBJECTS) {
            GameObject object = findObject(name);
            if (object != null) object.setVisible(visible);
        }
    }

    private void setHordeScriptEnabled(boolean enabled) {
        GameObject hordeObject = findObject(HORDE_DEBUG_OBJECT);
        if (hordeObject == null) return;
        for (IgnisScript script : hordeObject.getScripts()) {
            if (HORDE_SCRIPT_NAME.equals(script.getScriptName())) {
                script.setEnabled(enabled);
            }
        }
    }

    private void setHordeBackgroundVisible(boolean visible) {
        for (String name : HORDE_BACKGROUND_OBJECTS) {
            GameObject object = findObject(name);
            if (object != null) object.setVisible(visible);
        }
    }

    // ==================== Dados (sem paths no dominio; a leitura fica aqui) ====================

    private JSONObject findAreaJson(JSONObject root, String areaId) {
        JSONArray areasJson = root.getJSONArray("areas");
        for (int i = 0; i < areasJson.length(); i++) {
            JSONObject areaJson = areasJson.getJSONObject(i);
            if (areaJson.getString("id").equals(areaId)) return areaJson;
        }
        throw new IllegalStateException("Area inicial nao encontrada nos dados: " + areaId);
    }

    private JSONObject readJson(String relativePath) {
        File file = AssetResolver.resolve(relativePath);
        try {
            return new JSONObject(Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar " + relativePath, exception);
        }
    }

    private enum FlowState {
        MENU,
        CUTSCENE,
        VELDORA_ENCOUNTER,
        GOBLIN_CONTACT,
        EXPLORATION
    }
}
