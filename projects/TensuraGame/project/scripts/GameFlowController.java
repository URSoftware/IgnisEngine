import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.Camera;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.rimurusurvivors.domain.CutsceneCue;
import com.rimurusurvivors.domain.CutsceneTimeline;
import com.rimurusurvivors.domain.DialogueLine;
import com.rimurusurvivors.domain.DialogueScript;
import com.rimurusurvivors.domain.ExplorationArea;
import com.rimurusurvivors.domain.ExplorationEventType;
import com.rimurusurvivors.domain.ExplorationMap;
import com.rimurusurvivors.domain.ExplorationSimulation;
import com.rimurusurvivors.domain.ExplorationSnapshot;
import com.rimurusurvivors.domain.Interactable;
import com.rimurusurvivors.domain.ManualAdvanceGate;
import com.rimurusurvivors.domain.InteractionVerb;
import com.rimurusurvivors.domain.NarrativeBeat;
import com.rimurusurvivors.domain.NarrativeLine;
import com.rimurusurvivors.domain.NarrativeSequence;
import com.rimurusurvivors.domain.RunInput;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Entrada principal do jogo: menu -> exploracao (contrato da fatia Caverna do Selo).
 * A arena antiga (TensuraGameController) so roda como HordeEncounter de depuracao,
 * atras da tecla F9 — nunca no fluxo padrao.
 *
 * <p>ExplorationController, InteractionController e DialogueController vivem como
 * classes aninhadas ESTATICAS neste mesmo arquivo (nao arquivos separados): o
 * compilador de scripts do Ignis compila cada .java isoladamente contra as classes
 * ja compiladas, sem enxergar arquivos-irmaos novos na mesma leva — o mesmo motivo
 * pelo qual TensuraGameController ja guarda VisualSpec/AnimatedVisualSpec aninhados
 * em vez de em arquivos proprios. GameFlowController e o unico script anexado via
 * attach_script; os demais sao instanciados e dirigidos manualmente por ele
 * (decomposicao pedida no plano mestre, sem virar god-class).</p>
 */
public final class GameFlowController extends IgnisScript {

    private static final String MAP_DATA = "data/cave-seal-map.json";
    private static final String DIALOGUE_DATA = "data/cave-seal-dialogues.json";
    private static final String AWAKENING_DATA = "data/cutscene-awakening.json";
    private static final String VELDORA_DATA = "data/cutscene-veldora-first-meeting.json";
    // Reaproveita o objeto/controller ja existente da arena em vez de duplicar —
    // TensuraGameController continua intocado, so deixa de ser o script padrao.
    private static final String HORDE_DEBUG_OBJECT = "TensuraGameDirector";
    private static final String[] HORDE_BACKGROUND_OBJECTS = { "JuraForestFloor", "MagiculeMotes" };
    // Objetos estaticos da cena da Caverna (tilemaps + props) — ficam sempre na cena
    // (nao ha troca real de cena, ver nota abaixo), mas so devem aparecer durante a
    // EXPLORACAO. Sem isto eles vazavam atras/abaixo do menu (revisao visual do Codex).
    private static final String[] CAVE_SCENE_OBJECTS = {
            "CaveTilemapAwakening", "CaveTilemapGallery",
            "CaveCrystalCyanProp", "MagiculeClusterProp", "SealFragmentProp", "CaveExitGlowProp"
    };

    // O motor JA suporta cenas de verdade (IgnisScript.loadScene, ver SceneHost/
    // SceneTools no MCP) — esta fatia ainda nao foi migrada para usa-las e continua
    // com as duas areas lado a lado no MESMO mundo, por deslocamento manual. O
    // dominio permanece so em coordenadas locais-a-area (contrato "dominio sem
    // paths/mundo") e este mapa de deslocamento e a UNICA peca que sabe onde cada
    // area foi desenhada. Migrar para cave_awakening/cave_gallery como cenas
    // separadas (com copy_object_to_scene) e trabalho futuro, nao deste checkpoint.
    private static final Map<String, double[]> AREA_OFFSETS = Map.of(
            "cave_awakening", new double[] {0, 0},
            "cave_gallery", new double[] {900, 0});

    private final ExplorationController explorationController = new ExplorationController();
    private final CutsceneDirector cutsceneDirector = new CutsceneDirector();
    private final VeldoraEncounterDirector veldoraEncounterDirector = new VeldoraEncounterDirector();
    private final InteractionController interactionController = new InteractionController();
    private final DialogueController dialogueController = new DialogueController();

    private JSONObject mapRoot;
    private Map<String, ExplorationArea> areas;
    private Map<String, DialogueScript> dialogues;
    private ExplorationSimulation simulation;
    private GameObject player;
    private FlowState state = FlowState.MENU;
    private boolean veldoraEncounterCompleted;

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

        explorationController.init(getGameObject(), getGame());
        cutsceneDirector.init(getGameObject(), getGame());
        veldoraEncounterDirector.init(getGameObject(), getGame());
        interactionController.init(getGameObject(), getGame());
        dialogueController.init(getGameObject(), getGame());

        mapRoot = readJson(MAP_DATA);
        areas = loadAreas(mapRoot);
        dialogues = loadDialogues(readJson(DIALOGUE_DATA));

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
        if (state == FlowState.CUTSCENE) {
            boolean advanceDown = isKeyPressed("E") || isKeyPressed("ENTER");
            boolean advancePressed = isKeyJustPressed("E") || isKeyJustPressed("ENTER");
            if (cutsceneDirector.update(
                    getDeltaTime(), advanceDown, advancePressed, isKeyJustPressed("ESCAPE"))) {
                completeAwakening();
            }
            return;
        }
        if (state == FlowState.VELDORA_ENCOUNTER) {
            if (veldoraEncounterDirector.update(
                    getDeltaTime(), isKeyJustPressed("E"), isKeyJustPressed("ESCAPE"))) {
                completeVeldoraEncounter();
            }
            return;
        }
        if (state != FlowState.EXPLORATION) return;
        // Guarda defensiva: se beginExploration() foi interrompida entre marcar o
        // estado e configurar o controlador (ex.: excecao no meio da montagem), o
        // loop do jogo nao pode quebrar com NPE — so espera o proximo tick tentar de novo.
        if (!explorationController.isReady()) return;

        boolean interactPressed = isKeyJustPressed("E");
        ExplorationSnapshot snapshot = explorationController.update(getDeltaTime(), interactPressed);
        if (shouldStartVeldoraEncounter(snapshot)) {
            beginVeldoraEncounter();
            return;
        }
        ExplorationArea currentArea = areas.get(snapshot.areaId());
        Interactable focused = currentArea != null
                ? currentArea.findInteractable(snapshot.focusedInteractableId())
                : null;
        double[] offset = AREA_OFFSETS.getOrDefault(snapshot.areaId(), new double[] {0, 0});
        // Prompt de interacao some durante o dialogo (revisao visual do Codex): passar
        // null forca o InteractionController a esconder, sem ele precisar saber de
        // dialogo (continua so conhecendo o Interactable focado).
        interactionController.sync(snapshot.dialogueActive() ? null : focused, offset[0], offset[1]);
        dialogueController.sync(snapshot);
        layoutHud();
        if (isKeyJustPressed("F9")) activateHordeDebug();
    }

    // ==================== Menu ====================

    private void showMainMenu() {
        clearUI();
        state = FlowState.MENU;
        setCaveSceneObjectsVisible(false);
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

    // ==================== Exploracao ====================

    private void beginAwakening() {
        if (state != FlowState.MENU) return;
        String startAreaId = mapRoot.getString("startArea");
        JSONObject startAreaJson = findAreaJson(mapRoot, startAreaId);
        double spawnX = startAreaJson.getDouble("spawnX");
        double spawnY = startAreaJson.getDouble("spawnY");
        simulation = new ExplorationSimulation(areas, dialogues, startAreaId, spawnX, spawnY);
        double[] startOffset = AREA_OFFSETS.getOrDefault(startAreaId, new double[] {0, 0});
        double worldSpawnX = startOffset[0] + spawnX;
        double worldSpawnY = startOffset[1] + spawnY;

        clearUI();
        setCaveSceneObjectsVisible(true);
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
        explorationController.configure(simulation, player, AREA_OFFSETS);
        cutsceneDirector.play(readJson(AWAKENING_DATA), player);
        state = FlowState.CUTSCENE;
    }

    private void completeAwakening() {
        clearUI();
        setupExplorationHud("Explore a Caverna do Selo. [E] interagir");
    }

    private void setupExplorationHud(String objective) {
        interactionController.setup();
        dialogueController.setup();

        objectiveLabel = createLabel(objective, 0, 20, 620, 26);
        objectiveLabel.setFont("SansSerif", Font.BOLD, 14);
        objectiveLabel.setTextColor(new Color(255, 230, 147));
        objectiveLabel.setOutline(true, new Color(22, 14, 9), 1);
        debugHintLabel = createLabel("F9: HordeEncounter (debug)", 0, 0, 240, 20);
        debugHintLabel.setFont("SansSerif", Font.PLAIN, 11);
        debugHintLabel.setTextColor(new Color(140, 170, 180));
        layoutHud();
        // So entra em EXPLORATION depois que TUDO acima deu certo — se algo no meio
        // lancar excecao, o tick() continua no MENU em vez de rodar update() sobre um
        // ExplorationController que nunca foi configurado (era o NPE que derrubava o
        // loop do jogo: "this.simulation is null").
        state = FlowState.EXPLORATION;
    }

    private void layoutHud() {
        double width = Math.max(480, getGame().getWidth());
        objectiveLabel.setPosition((width - objectiveLabel.getWidth()) / 2.0, 18);
        debugHintLabel.setPosition(width - 250, 18);
    }

    private boolean shouldStartVeldoraEncounter(ExplorationSnapshot snapshot) {
        if (veldoraEncounterCompleted || snapshot == null || snapshot.events() == null) return false;
        return snapshot.events().stream().anyMatch(event ->
                event.type() == ExplorationEventType.DIALOGUE_ENDED
                        && "dlg_gallery_seal".equals(event.detail()));
    }

    private void beginVeldoraEncounter() {
        if (state != FlowState.EXPLORATION || veldoraEncounterCompleted) return;
        clearUI();
        double[] offset = AREA_OFFSETS.getOrDefault("cave_gallery", new double[] {0, 0});
        veldoraEncounterDirector.play(readJson(VELDORA_DATA), player, offset[0] + 176, offset[1] + 80);
        state = FlowState.VELDORA_ENCOUNTER;
    }

    private void completeVeldoraEncounter() {
        if (veldoraEncounterCompleted) return;
        veldoraEncounterCompleted = true;
        clearUI();
        setupExplorationHud("Veldora esta com voce. Encontre a saida da caverna.");
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
        setCaveSceneObjectsVisible(false);
        if (player != null) player.setVisible(false);
        setHordeBackgroundVisible(true);
        setHordeScriptEnabled(true);
        log("HordeEncounter (debug) ativado via F9.");
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

    private Map<String, ExplorationArea> loadAreas(JSONObject root) {
        Map<String, ExplorationArea> result = new HashMap<>();
        JSONArray areasJson = root.getJSONArray("areas");
        for (int i = 0; i < areasJson.length(); i++) {
            JSONObject areaJson = areasJson.getJSONObject(i);
            String id = areaJson.getString("id");
            int cols = areaJson.getInt("cols");
            int rows = areaJson.getInt("rows");
            double cellSize = areaJson.getDouble("cellSize");

            Set<ExplorationMap.Cell> blocked = new HashSet<>();
            JSONArray walls = areaJson.optJSONArray("walls");
            if (walls != null) {
                for (int w = 0; w < walls.length(); w++) {
                    JSONObject rect = walls.getJSONObject(w);
                    int col0 = rect.getInt("col0");
                    int row0 = rect.getInt("row0");
                    int col1 = rect.getInt("col1");
                    int row1 = rect.getInt("row1");
                    for (int col = col0; col <= col1; col++) {
                        for (int row = row0; row <= row1; row++) {
                            blocked.add(new ExplorationMap.Cell(col, row));
                        }
                    }
                }
            }
            ExplorationMap map = new ExplorationMap(cols, rows, cellSize, blocked);

            List<Interactable> interactables = new ArrayList<>();
            JSONArray interactablesJson = areaJson.optJSONArray("interactables");
            if (interactablesJson != null) {
                for (int k = 0; k < interactablesJson.length(); k++) {
                    JSONObject entry = interactablesJson.getJSONObject(k);
                    interactables.add(new Interactable(
                            entry.getString("id"),
                            entry.getDouble("x"),
                            entry.getDouble("y"),
                            entry.getDouble("radius"),
                            InteractionVerb.valueOf(entry.getString("verb")),
                            entry.optString("dialogueId", null),
                            entry.optBoolean("collectible", false),
                            entry.optString("targetAreaId", null),
                            entry.optDouble("targetSpawnX", 0),
                            entry.optDouble("targetSpawnY", 0)));
                }
            }
            result.put(id, new ExplorationArea(id, map, interactables));
        }
        return result;
    }

    private Map<String, DialogueScript> loadDialogues(JSONObject root) {
        Map<String, DialogueScript> result = new HashMap<>();
        JSONArray dialoguesJson = root.getJSONArray("dialogues");
        for (int i = 0; i < dialoguesJson.length(); i++) {
            JSONObject dialogueJson = dialoguesJson.getJSONObject(i);
            String id = dialogueJson.getString("id");
            List<DialogueLine> lines = new ArrayList<>();
            JSONArray linesJson = dialogueJson.getJSONArray("lines");
            for (int k = 0; k < linesJson.length(); k++) {
                JSONObject lineJson = linesJson.getJSONObject(k);
                lines.add(new DialogueLine(lineJson.optString("speaker", ""), lineJson.getString("text")));
            }
            result.put(id, new DialogueScript(id, lines));
        }
        return result;
    }

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
        EXPLORATION
    }

    // ==================== ExplorationController (aninhada) ====================

    /**
     * Move o jogador da exploracao, delega colisao/foco/dialogo para
     * ExplorationSimulation e sincroniza o GameObject visual (posicao, locomocao,
     * camera). Nao conhece dados de mapa nem conteudo de dialogo — so a simulacao e
     * o visual do jogador. Instanciada e dirigida manualmente pelo GameFlowController
     * (nao e anexada a um GameObject via attach_script).
     */
    private static final class ExplorationController extends IgnisScript {

        private static final String CLIP_IDLE = "rimuru_slime_idle";
        private static final String CLIP_MOVE = "rimuru_slime_move";

        private final Map<String, SpriteAnimation> clips = new HashMap<>();

        private ExplorationSimulation simulation;
        private GameObject player;
        // O dominio usa coordenadas LOCAIS a cada area (cada area comeca do zero). No
        // mundo do editor as duas areas vivem lado a lado (nao ha troca real de cena —
        // ver nota na classe externa), entao cada area precisa de um deslocamento
        // proprio para nao desenhar objetos de areas diferentes um em cima do outro.
        private Map<String, double[]> areaOffsets = Map.of();

        /** Liga a simulacao e o visual do jogador; chame antes do primeiro update(). */
        void configure(ExplorationSimulation simulation, GameObject player, Map<String, double[]> areaOffsets) {
            this.simulation = simulation;
            this.player = player;
            this.areaOffsets = areaOffsets == null ? Map.of() : areaOffsets;
            attachClips(player, CLIP_IDLE, CLIP_MOVE);
        }

        /** Falso ate configure() rodar — guarda usada pelo GameFlowController antes de chamar update(). */
        boolean isReady() {
            return simulation != null && player != null;
        }

        ExplorationSnapshot update(double deltaTime, boolean interactPressed) {
            // Mesma conversao tela->mundo do RunInput da arena: W/seta-cima = +1 no
            // mundo, sem essa troca o jogador andaria para baixo ao apertar W.
            RunInput movement = RunInput.fromScreenAxes(getHorizontalAxis(), getVerticalAxis());
            ExplorationSnapshot snapshot = simulation.update(deltaTime, movement, interactPressed);
            syncPlayer(snapshot);
            cameraFollow(player, 0.18);
            return snapshot;
        }

        private void syncPlayer(ExplorationSnapshot snapshot) {
            if (player == null) return;
            double[] offset = areaOffsets.getOrDefault(snapshot.areaId(), new double[] {0, 0});
            double width = player.getWidth();
            double height = player.getHeight();
            player.setX(offset[0] + snapshot.playerX() - width / 2.0);
            player.setY(offset[1] + snapshot.playerY() - height / 2.0);
            String clipName = snapshot.playerMoving() ? CLIP_MOVE : CLIP_IDLE;
            Animator animator = player.getOrCreateAnimator();
            if (clip(clipName) != null && !clipName.equals(animator.getCurrentName())) {
                animator.play(clipName, true);
            }
        }

        private SpriteAnimation clip(String name) {
            if (clips.containsKey(name)) return clips.get(name);
            SpriteAnimation loaded = null;
            try {
                File file = AssetResolver.resolve("assets/animations/" + name + ".anim.json");
                if (file != null && file.exists()) loaded = AnimationIO.load(file);
            } catch (IOException exception) {
                loaded = null; // clipe ausente nao pode derrubar a exploracao
            }
            clips.put(name, loaded);
            return loaded;
        }

        private void attachClips(GameObject object, String... names) {
            if (object == null) return;
            Animator animator = object.getOrCreateAnimator();
            for (String name : names) {
                SpriteAnimation animation = clip(name);
                if (animation != null) animator.addAnimation(animation);
            }
        }
    }

    // ==================== CutsceneDirector (aninhada) ====================

    /**
     * Apresenta uma timeline sem colocar regra de tempo no fluxo principal. Os cues
     * vem de dados; a classe de dominio garante skip e conclusao idempotentes.
     */
    private static final class CutsceneDirector extends IgnisScript {

        private static final String CLIP_REVEAL = "cutscene_slime_reveal";
        private static final String CLIP_IDLE = "rimuru_slime_idle";
        private static final String CLIP_MAGICULE = "prop_magicule_cluster";
        private static final String CLIP_SCAN = "vfx_great_sage_scan";

        private final Map<String, SpriteAnimation> clips = new HashMap<>();

        private CutsceneTimeline timeline;
        private GameObject actor;
        private GameObject magiculeVisual;
        private GameObject scanVisual;
        private UIPanel fadePanel;
        private UIPanel analysisPanel;
        private UIImage sageIcon;
        private UILabel speakerLabel;
        private UILabel analysisLabel;
        private UILabel skipLabel;
        private String analysisSpeaker;
        private String analysisText;
        private double actorCenterX;
        private double actorCenterY;
        private ManualAdvanceGate analysisAdvanceGate;
        private boolean analysisPaused;
        private boolean handoffDelivered;

        void play(JSONObject data, GameObject player) {
            Objects.requireNonNull(data, "cutscene data");
            Objects.requireNonNull(player, "cutscene actor");
            List<CutsceneCue> cues = new ArrayList<>();
            JSONArray cuesJson = data.getJSONArray("cues");
            for (int i = 0; i < cuesJson.length(); i++) {
                JSONObject cue = cuesJson.getJSONObject(i);
                cues.add(new CutsceneCue(cue.getString("id"), cue.getDouble("atSeconds")));
            }
            timeline = new CutsceneTimeline(
                    data.getDouble("durationSeconds"),
                    data.getDouble("skippableAfterSeconds"),
                    cues);
            actor = player;
            actorCenterX = actor.getX() + actor.getWidth() / 2.0;
            actorCenterY = actor.getY() + actor.getHeight() / 2.0;
            handoffDelivered = false;

            JSONObject analysis = data.getJSONObject("analysis");
            analysisSpeaker = analysis.getString("speaker");
            analysisText = analysis.getString("text");
            analysisAdvanceGate = new ManualAdvanceGate(analysis.optDouble("minimumReadSeconds", 1.0));
            analysisPaused = false;
            setupUi();
            attachClip(actor, CLIP_REVEAL);
            attachClip(actor, CLIP_IDLE);
            actor.setVisible(false);
            actor.setOpacity(0);
            setCameraPosition(actorCenterX, actorCenterY);
            setCameraZoom(1.42);
            log("Cutscene cave_awakening iniciada: duracao="
                    + data.getDouble("durationSeconds") + "s, cues=" + cues.size());
        }

        /** Retorna true uma unica vez quando o controle deve passar a exploracao. */
        boolean update(
                double deltaTime, boolean advanceDown, boolean advancePressed, boolean skipPressed) {
            if (timeline == null || handoffDelivered) return false;
            if (skipPressed) {
                boolean accepted = timeline.requestSkip();
                log("Pedido de skip da cutscene: aceito=" + accepted
                        + ", tempo=" + timeline.elapsedSeconds());
                if (accepted) analysisPaused = false;
            }
            if (analysisPaused && !timeline.isFinished()) {
                if (analysisAdvanceGate.update(deltaTime, advanceDown, advancePressed)) {
                    analysisPaused = false;
                    hideAnalysis();
                    if (scanVisual != null) {
                        destroy(scanVisual);
                        scanVisual = null;
                    }
                }
                if (analysisPaused) {
                    updatePresentation();
                    return false;
                }
            }
            for (String cue : timeline.advance(deltaTime)) {
                applyCue(cue);
            }
            updatePresentation();
            if (analysisPaused) return false;
            if (!timeline.consumeCompletion()) return false;
            log("Cutscene cave_awakening concluida: tempo=" + timeline.elapsedSeconds());
            finishVisualState();
            handoffDelivered = true;
            return true;
        }

        private void applyCue(String cue) {
            switch (cue) {
                case "darkness_and_drops" -> {
                    playSound("assets/sounds/cave_water_ripple_01.wav", 0.22f);
                    actor.setVisible(false);
                }
                case "magicule_convergence" -> showMagiculeConvergence();
                case "slime_body_formation" -> showSlimeFormation();
                case "first_perception" -> {
                    actor.setOpacity(1);
                    setCameraZoom(1.54);
                }
                case "great_sage_analysis" -> showGreatSageAnalysis();
                case "veldora_foreshadow" -> showVeldoraForeshadow();
                case "control_handoff" -> hideAnalysis();
                default -> log("Cue de cutscene ignorado: " + cue);
            }
        }

        private void showMagiculeConvergence() {
            magiculeVisual = createRuntimeVisual("AwakeningMagicules", actorCenterX, actorCenterY, 72, 72, 24);
            attachClip(magiculeVisual, CLIP_MAGICULE);
            Animator animator = magiculeVisual.getOrCreateAnimator();
            if (clip(CLIP_MAGICULE) != null) animator.play(CLIP_MAGICULE, true);
            playSound("assets/sounds/magicule_collect_soft.wav", 0.28f);
        }

        private void showSlimeFormation() {
            if (magiculeVisual != null) {
                destroy(magiculeVisual);
                magiculeVisual = null;
            }
            actor.setVisible(true);
            actor.setOpacity(0.72);
            Animator animator = actor.getOrCreateAnimator();
            if (clip(CLIP_REVEAL) != null) animator.play(CLIP_REVEAL, false);
            playSound("assets/sounds/slime_land_soft_01.wav", 0.45f);
        }

        private void showGreatSageAnalysis() {
            scanVisual = createRuntimeVisual("AwakeningGreatSageScan", actorCenterX, actorCenterY, 168, 168, 45);
            attachClip(scanVisual, CLIP_SCAN);
            Animator animator = scanVisual.getOrCreateAnimator();
            if (clip(CLIP_SCAN) != null) animator.play(CLIP_SCAN, false);
            analysisPanel.setVisible(true);
            sageIcon.setVisible(true);
            speakerLabel.setVisible(true);
            analysisLabel.setVisible(true);
            analysisPaused = true;
            playSound("assets/sounds/great_sage_scan_short.wav", 0.58f);
        }

        private void showVeldoraForeshadow() {
            hideAnalysis();
            playSound("assets/sounds/veldora_distant_rumble.wav", 0.34f);
            setCameraPosition(actorCenterX + 28, actorCenterY - 8);
            cameraShake(1.8);
        }

        private void updatePresentation() {
            double elapsed = timeline.elapsedSeconds();
            int fadeAlpha;
            if (elapsed < 0.85) {
                fadeAlpha = 238;
            } else if (elapsed < 2.1) {
                fadeAlpha = lerpInt(210, 132, (elapsed - 0.85) / 1.25);
            } else if (elapsed < 3.55) {
                fadeAlpha = lerpInt(132, 0, (elapsed - 2.1) / 1.45);
            } else {
                fadeAlpha = 0;
            }
            fadePanel.setSize(Math.max(480, getGame().getWidth()), Math.max(360, getGame().getHeight()));
            setUIColors(fadePanel, new Color(0, 4, 9, fadeAlpha), null, null);
            fadePanel.setVisible(fadeAlpha > 0);

            skipLabel.setVisible(timeline.canSkip());
            skipLabel.setPosition(Math.max(18, getGame().getWidth() - 180), 18);
            layoutAnalysis();

            if (elapsed >= 2.1 && elapsed < 3.55 && actor.isVisible()) {
                double local = (elapsed - 2.1) / 1.45;
                double pulse = 1.0 + Math.sin(local * Math.PI * 4) * (1.0 - local) * 0.10;
                resizeActorCentered(28 * pulse, 28 * pulse);
                actor.setOpacity(Math.min(1.0, 0.72 + local * 0.28));
            }
        }

        private void setupUi() {
            fadePanel = createPanel(0, 0, Math.max(480, getGame().getWidth()), Math.max(360, getGame().getHeight()));
            setUIColors(fadePanel, new Color(0, 4, 9, 238), null, null);

            analysisPanel = createPanel(0, 0, 720, 112);
            setUIColors(analysisPanel, new Color(4, 14, 23, 238), null, new Color(82, 211, 238));
            sageIcon = createImage("assets/ui/icon_great_sage_indicator.png", 0, 0, 24, 24);
            speakerLabel = createLabel(analysisSpeaker, 0, 0, 240, 22);
            speakerLabel.setFont("SansSerif", Font.BOLD, 15);
            speakerLabel.setTextColor(new Color(120, 226, 255));
            analysisLabel = createLabel(analysisText + "  [E/ENTER] Continuar", 0, 0, 650, 52);
            analysisLabel.setFont("SansSerif", Font.PLAIN, 16);
            analysisLabel.setTextColor(new Color(232, 244, 248));
            analysisLabel.setMultiline(true);
            hideAnalysis();

            skipLabel = createLabel("[ESC] Pular", 0, 0, 150, 22);
            skipLabel.setFont("SansSerif", Font.BOLD, 12);
            skipLabel.setTextColor(new Color(188, 224, 232));
            skipLabel.setOutline(true, new Color(3, 10, 15), 1);
            skipLabel.setVisible(false);
        }

        private void layoutAnalysis() {
            double width = Math.max(480, getGame().getWidth());
            double height = Math.max(360, getGame().getHeight());
            double panelWidth = Math.min(760, width - 48);
            double x = (width - panelWidth) / 2.0;
            double y = height - 142;
            analysisPanel.setSize(panelWidth, 112);
            analysisPanel.setPosition(x, y);
            sageIcon.setPosition(x + 18, y + 16);
            speakerLabel.setPosition(x + 52, y + 16);
            analysisLabel.setSize(panelWidth - 40, 52);
            analysisLabel.setPosition(x + 20, y + 46);
        }

        private void hideAnalysis() {
            if (analysisPanel != null) analysisPanel.setVisible(false);
            if (sageIcon != null) sageIcon.setVisible(false);
            if (speakerLabel != null) speakerLabel.setVisible(false);
            if (analysisLabel != null) analysisLabel.setVisible(false);
        }

        private void finishVisualState() {
            hideAnalysis();
            if (fadePanel != null) fadePanel.setVisible(false);
            if (skipLabel != null) skipLabel.setVisible(false);
            if (magiculeVisual != null) destroy(magiculeVisual);
            if (scanVisual != null) destroy(scanVisual);
            magiculeVisual = null;
            scanVisual = null;
            actor.setVisible(true);
            actor.setOpacity(1);
            resizeActorCentered(28, 28);
            Animator animator = actor.getOrCreateAnimator();
            if (clip(CLIP_IDLE) != null) animator.play(CLIP_IDLE, true);
            setCameraPosition(actorCenterX, actorCenterY);
            setCameraZoom(1.6);
        }

        private GameObject createRuntimeVisual(
                String name, double centerX, double centerY, int width, int height, int zIndex) {
            GameObject visual = getGame().instantiatePrefab("RuntimeVisual", centerX, centerY);
            if (visual == null) {
                visual = new GameObject(name, getGame(), centerX, centerY, width, height);
                getGame().addEntity(visual);
            }
            visual.setName(name);
            visual.setWidth(width);
            visual.setHeight(height);
            visual.setX(centerX - width / 2.0);
            visual.setY(centerY - height / 2.0);
            visual.setZIndex(zIndex);
            visual.setVisible(true);
            return visual;
        }

        private void resizeActorCentered(double width, double height) {
            actor.setWidth((int) Math.round(width));
            actor.setHeight((int) Math.round(height));
            actor.setX(actorCenterX - actor.getWidth() / 2.0);
            actor.setY(actorCenterY - actor.getHeight() / 2.0);
        }

        private SpriteAnimation clip(String name) {
            if (clips.containsKey(name)) return clips.get(name);
            SpriteAnimation loaded = null;
            try {
                File file = AssetResolver.resolve("assets/animations/" + name + ".anim.json");
                if (file != null && file.exists()) loaded = AnimationIO.load(file);
            } catch (IOException exception) {
                loaded = null;
            }
            clips.put(name, loaded);
            return loaded;
        }

        private void attachClip(GameObject object, String name) {
            if (object == null) return;
            SpriteAnimation animation = clip(name);
            if (animation != null) object.getOrCreateAnimator().addAnimation(animation);
        }

        private int lerpInt(int start, int end, double amount) {
            double t = Math.max(0, Math.min(1, amount));
            return (int) Math.round(start + (end - start) * t);
        }
    }

    // ==================== Primeiro encontro com Veldora ====================

    /**
     * Adapta a sequencia narrativa de dados para retratos, camera e VFX. A classe
     * NarrativeSequence continua dona de avanco, auto-beats, skip e finalizacao.
     */
    private static final class VeldoraEncounterDirector extends IgnisScript {

        private static final String PORTRAITS = "assets/sprites/portraits/";
        private final Map<String, SpriteAnimation> clips = new HashMap<>();
        private final Map<String, String> speakerNames = new HashMap<>();

        private NarrativeSequence sequence;
        private GameObject actor;
        private GameObject veldoraVisual;
        private GameObject barrierVisual;
        private GameObject transientVisual;
        private GameObject secondaryVisual;
        private double actorCenterX;
        private double actorCenterY;
        private double sealCenterX;
        private double sealCenterY;
        private String shownBeatId;
        private String shownLineId;
        private boolean completionDelivered;

        private UIPanel cinemaShade;
        private UIPanel dialoguePanel;
        private UIImage portrait;
        private UIImage sageIcon;
        private UILabel sceneCaption;
        private UILabel speakerLabel;
        private UILabel textLabel;
        private UILabel continueHint;
        private UILabel skipLabel;

        void play(JSONObject data, GameObject player, double sealX, double sealY) {
            Objects.requireNonNull(data, "Veldora sequence data");
            Objects.requireNonNull(player, "Veldora sequence actor");
            actor = player;
            actorCenterX = actor.getX() + actor.getWidth() / 2.0;
            actorCenterY = actor.getY() + actor.getHeight() / 2.0;
            sealCenterX = sealX;
            sealCenterY = sealY + 62;
            completionDelivered = false;
            shownBeatId = null;
            shownLineId = null;
            speakerNames.clear();
            parseParticipants(data.getJSONObject("participants"));
            sequence = new NarrativeSequence(
                    parseBeats(data.getJSONArray("beats")), data.getDouble("skippableAfterSeconds"));

            setupWorldVisuals();
            setupUi();
            enterBeat(sequence.currentBeat().id());
            showLine(sequence.currentLine());
            setCameraPosition((actorCenterX + sealCenterX) / 2.0, (actorCenterY + sealCenterY) / 2.0);
            setCameraZoom(1.45);
            log("Encontro com Veldora iniciado: beats=" + data.getJSONArray("beats").length());
        }

        boolean update(double deltaTime, boolean advancePressed, boolean skipPressed) {
            if (sequence == null || completionDelivered) return false;
            if (skipPressed) sequence.requestSkip();

            String beforeBeat = sequence.currentBeat().id();
            NarrativeLine beforeLine = sequence.currentLine();
            sequence.update(deltaTime, advancePressed);

            if (!sequence.isFinished()) {
                String afterBeat = sequence.currentBeat().id();
                NarrativeLine afterLine = sequence.currentLine();
                if (!afterBeat.equals(beforeBeat) || !afterBeat.equals(shownBeatId)) enterBeat(afterBeat);
                String afterLineId = afterLine == null ? null : afterLine.id();
                String beforeLineId = beforeLine == null ? null : beforeLine.id();
                if (!Objects.equals(afterLineId, beforeLineId)
                        || !Objects.equals(afterLineId, shownLineId)) {
                    showLine(afterLine);
                }
                updateLayout();
            }

            if (!sequence.consumeCompletion()) return false;
            finishVisualState();
            completionDelivered = true;
            log("Encontro com Veldora concluido: tempo=" + sequence.elapsedSeconds());
            return true;
        }

        private List<NarrativeBeat> parseBeats(JSONArray beatsJson) {
            List<NarrativeBeat> beats = new ArrayList<>();
            for (int i = 0; i < beatsJson.length(); i++) {
                JSONObject beatJson = beatsJson.getJSONObject(i);
                List<NarrativeLine> lines = new ArrayList<>();
                JSONArray linesJson = beatJson.optJSONArray("lines");
                if (linesJson != null) {
                    for (int k = 0; k < linesJson.length(); k++) {
                        JSONObject line = linesJson.getJSONObject(k);
                        lines.add(new NarrativeLine(
                                line.getString("id"), line.getString("speaker"),
                                line.optString("portrait", ""), line.getString("text")));
                    }
                }
                beats.add(new NarrativeBeat(
                        beatJson.getString("id"), beatJson.optDouble("autoDurationSeconds", 0), lines));
            }
            return beats;
        }

        private void parseParticipants(JSONObject participants) {
            for (String id : participants.keySet()) {
                speakerNames.put(id, participants.getJSONObject(id).getString("displayName"));
            }
        }

        private void setupWorldVisuals() {
            barrierVisual = createRuntimeVisual(
                    "InfinitePrisonBarrier", sealCenterX, sealCenterY, 270, 270, 34);
            attachAndPlay(barrierVisual, "vfx_infinite_prison_barrier", true);
            veldoraVisual = createRuntimeVisual(
                    "VeldoraCutscene", sealCenterX, sealCenterY, 220, 220, 35);
            attachAndPlay(veldoraVisual, "cutscene_veldora_breath_canon_v2", true);
            actor.setVisible(true);
            actor.setOpacity(1);
        }

        private void setupUi() {
            cinemaShade = createPanel(0, 0, Math.max(480, getGame().getWidth()), Math.max(360, getGame().getHeight()));
            setUIColors(cinemaShade, new Color(0, 4, 9, 78), null, null);

            sceneCaption = createLabel("", 0, 24, 620, 36);
            sceneCaption.setAlignment(UILabel.Alignment.CENTER);
            sceneCaption.setFont("SansSerif", Font.BOLD, 19);
            sceneCaption.setTextColor(new Color(205, 240, 255));
            sceneCaption.setOutline(true, new Color(2, 8, 14), 2);

            dialoguePanel = createPanel(0, 0, 800, 154);
            setUIColors(dialoguePanel, new Color(3, 11, 19, 242), null, new Color(70, 187, 221));
            portrait = createImage(PORTRAITS + "rimuru/portrait_rimuru_slime_curious.png", 0, 0, 112, 112);
            sageIcon = createImage("assets/ui/icon_great_sage_indicator.png", 0, 0, 32, 32);
            speakerLabel = createLabel("", 0, 0, 300, 24);
            speakerLabel.setFont("SansSerif", Font.BOLD, 16);
            speakerLabel.setTextColor(new Color(117, 224, 251));
            textLabel = createLabel("", 0, 0, 620, 74);
            textLabel.setFont("SansSerif", Font.PLAIN, 17);
            textLabel.setTextColor(new Color(235, 245, 248));
            textLabel.setMultiline(true);
            continueHint = createLabel("", 0, 0, 210, 20);
            continueHint.setFont("SansSerif", Font.ITALIC, 12);
            continueHint.setTextColor(new Color(159, 199, 211));
            skipLabel = createLabel("[ESC] Pular", 0, 18, 150, 22);
            skipLabel.setFont("SansSerif", Font.BOLD, 12);
            skipLabel.setTextColor(new Color(188, 224, 232));
            skipLabel.setOutline(true, new Color(3, 10, 15), 1);
            setDialogueVisible(false);
            updateLayout();
        }

        private void enterBeat(String beatId) {
            shownBeatId = beatId;
            destroyTransientVisuals();
            switch (beatId) {
                case "presence" -> {
                    sceneCaption.setText("UMA PRESENCA COLOSSAL");
                    sceneCaption.setVisible(true);
                    playSound("assets/sounds/veldora_distant_rumble.wav", 0.48f);
                    cameraShake(1.6);
                }
                case "first_contact" -> sceneCaption.setVisible(false);
                case "friendship" -> {
                    sceneCaption.setText("UM LACO IMPROVAVEL");
                    sceneCaption.setVisible(true);
                }
                case "name_exchange" -> {
                    sceneCaption.setText("RIMURU TEMPEST");
                    sceneCaption.setVisible(true);
                    transientVisual = createRuntimeVisual(
                            "NameExchangeWave", (actorCenterX + sealCenterX) / 2.0,
                            (actorCenterY + sealCenterY) / 2.0, 330, 330, 48);
                    attachAndPlay(transientVisual, "vfx_name_exchange_magicule_wave", false);
                    playSound("assets/sounds/magicule_collect_soft.wav", 0.55f);
                }
                case "predator_plan" -> {
                    sceneCaption.setText("GRANDE SABIO: ANALISE EM CURSO");
                    sceneCaption.setVisible(true);
                    transientVisual = createRuntimeVisual(
                            "GreatSageSealScan", sealCenterX, sealCenterY, 250, 250, 49);
                    attachAndPlay(transientVisual, "vfx_great_sage_scan", false);
                    playSound("assets/sounds/great_sage_scan_short.wav", 0.58f);
                }
                case "predator_absorption" -> {
                    sceneCaption.setText("PREDADOR");
                    sceneCaption.setVisible(true);
                    transientVisual = createRuntimeVisual(
                            "RimuruPredatorCast", actorCenterX, actorCenterY, 180, 180, 50);
                    attachAndPlay(transientVisual, "cutscene_rimuru_slime_predator_cast_canon_v2", false);
                    secondaryVisual = createRuntimeVisual(
                            "VeldoraAbsorption", sealCenterX, sealCenterY, 340, 340, 51);
                    attachAndPlay(secondaryVisual, "vfx_predator_absorption", false);
                    playSound("assets/sounds/predator.wav", 0.64f);
                }
                case "quiet_aftermath" -> {
                    sceneCaption.setText("A PRISAO FICOU SILENCIOSA");
                    sceneCaption.setVisible(true);
                    destroyWorldVisual(veldoraVisual);
                    destroyWorldVisual(barrierVisual);
                    veldoraVisual = null;
                    barrierVisual = null;
                }
                default -> sceneCaption.setVisible(false);
            }
        }

        private void showLine(NarrativeLine line) {
            shownLineId = line == null ? null : line.id();
            if (line == null) {
                setDialogueVisible(false);
                return;
            }
            setDialogueVisible(true);
            String speaker = line.speaker();
            speakerLabel.setText(speakerNames.getOrDefault(speaker, speaker));
            textLabel.setText(line.text());
            boolean sage = "great_sage".equals(speaker);
            sageIcon.setVisible(sage);
            portrait.setVisible(!sage);
            if (!sage) portrait.setImagePath(portraitPath(speaker, line.portrait()));
            continueHint.setText(sequence.currentBeat().isAutomatic()
                    ? "A sequencia continua..." : "[E] continuar");
            updateLayout();
        }

        private String portraitPath(String speaker, String emotion) {
            if ("veldora".equals(speaker)) {
                return PORTRAITS + "veldora/portrait_veldora_dragon_" + emotion + ".png";
            }
            return PORTRAITS + "rimuru/portrait_rimuru_slime_" + emotion + ".png";
        }

        private void updateLayout() {
            double width = Math.max(480, getGame().getWidth());
            double height = Math.max(360, getGame().getHeight());
            cinemaShade.setSize(width, height);
            sceneCaption.setPosition((width - sceneCaption.getWidth()) / 2.0, 24);
            skipLabel.setPosition(Math.max(18, width - 170), 18);
            skipLabel.setVisible(sequence != null && sequence.canSkip());

            double panelWidth = Math.min(820, width - 48);
            double x = (width - panelWidth) / 2.0;
            double y = height - 176;
            dialoguePanel.setSize(panelWidth, 154);
            dialoguePanel.setPosition(x, y);
            portrait.setPosition(x + 16, y + 20);
            sageIcon.setPosition(x + 54, y + 28);
            speakerLabel.setPosition(x + 146, y + 18);
            textLabel.setSize(panelWidth - 172, 74);
            textLabel.setPosition(x + 146, y + 47);
            continueHint.setPosition(x + panelWidth - 220, y + 126);
        }

        private void setDialogueVisible(boolean visible) {
            dialoguePanel.setVisible(visible);
            if (!visible) {
                portrait.setVisible(false);
                sageIcon.setVisible(false);
            }
            speakerLabel.setVisible(visible);
            textLabel.setVisible(visible);
            continueHint.setVisible(visible);
        }

        private void finishVisualState() {
            destroyTransientVisuals();
            destroyWorldVisual(veldoraVisual);
            destroyWorldVisual(barrierVisual);
            veldoraVisual = null;
            barrierVisual = null;
            setDialogueVisible(false);
            sceneCaption.setVisible(false);
            skipLabel.setVisible(false);
            cinemaShade.setVisible(false);
            actor.setVisible(true);
            actor.setOpacity(1);
            setCameraPosition(actorCenterX, actorCenterY);
            setCameraZoom(1.6);
        }

        private void destroyTransientVisuals() {
            destroyWorldVisual(transientVisual);
            destroyWorldVisual(secondaryVisual);
            transientVisual = null;
            secondaryVisual = null;
        }

        private void destroyWorldVisual(GameObject visual) {
            if (visual != null) destroy(visual);
        }

        private GameObject createRuntimeVisual(
                String name, double centerX, double centerY, int width, int height, int zIndex) {
            GameObject visual = getGame().instantiatePrefab("RuntimeVisual", centerX, centerY);
            if (visual == null) {
                visual = new GameObject(name, getGame(), centerX, centerY, width, height);
                getGame().addEntity(visual);
            }
            visual.setName(name);
            visual.setWidth(width);
            visual.setHeight(height);
            visual.setX(centerX - width / 2.0);
            visual.setY(centerY - height / 2.0);
            visual.setZIndex(zIndex);
            visual.setVisible(true);
            return visual;
        }

        private void attachAndPlay(GameObject object, String clipName, boolean loop) {
            SpriteAnimation animation = clip(clipName);
            if (object == null || animation == null) return;
            Animator animator = object.getOrCreateAnimator();
            animator.addAnimation(animation);
            animator.play(clipName, loop);
        }

        private SpriteAnimation clip(String name) {
            if (clips.containsKey(name)) return clips.get(name);
            SpriteAnimation loaded = null;
            try {
                File file = AssetResolver.resolve("assets/animations/" + name + ".anim.json");
                if (file != null && file.exists()) loaded = AnimationIO.load(file);
            } catch (IOException exception) {
                loaded = null;
            }
            clips.put(name, loaded);
            return loaded;
        }
    }

    // ==================== InteractionController (aninhada) ====================

    /**
     * Mostra o prompt contextual (icone + verbo) proximo ao alvo interativo focado
     * pela exploracao. So conhece o Interactable atual — dialogo e movimento nao sao
     * responsabilidade dela (contrato de IDs semanticos: nenhum indice de tile ou
     * icone de mundo vaza para o dominio).
     */
    private static final class InteractionController extends IgnisScript {

        private static final String UI = "assets/ui/";

        private UIPanel promptPanel;
        private UIImage promptIcon;
        private UILabel promptLabel;
        private String lastFocusedId;

        void setup() {
            promptPanel = createPanel(0, 0, 132, 34);
            setUIColors(promptPanel, new Color(6, 15, 23, 235), null, new Color(87, 217, 242));
            promptIcon = createImage(UI + "icon_interact_examine.png", 0, 0, 22, 22);
            promptLabel = createLabel("", 0, 0, 96, 22);
            promptLabel.setFont("SansSerif", Font.BOLD, 13);
            promptLabel.setTextColor(new Color(226, 248, 255));
            setPromptVisible(false);
        }

        /**
         * offsetX/offsetY convertem a posicao local-a-area do Interactable para o
         * mundo visual do editor (as areas vivem lado a lado no mesmo mundo).
         */
        void sync(Interactable focused, double offsetX, double offsetY) {
            String focusedId = focused != null ? focused.id() : null;
            if (Objects.equals(focusedId, lastFocusedId)) {
                if (focused != null) positionPrompt(focused, offsetX, offsetY);
                return;
            }
            lastFocusedId = focusedId;
            if (focused == null) {
                setPromptVisible(false);
                return;
            }
            promptIcon.setImagePath(iconFor(focused.verb()));
            promptLabel.setText(verbLabel(focused.verb()));
            setPromptVisible(true);
            positionPrompt(focused, offsetX, offsetY);
        }

        private void positionPrompt(Interactable focused, double offsetX, double offsetY) {
            Camera camera = getCamera();
            if (camera == null) return;
            // Desaparece quando o alvo sai do alcance (feito em sync()); aqui so
            // reposiciona acima do alvo a cada tick em que ele segue focado.
            Point2D.Double screen = camera.worldToScreen(
                    offsetX + focused.x(), offsetY + focused.y() - focused.radius() - 26);
            double x = screen.x - promptPanel.getWidth() / 2.0;
            double y = screen.y;
            promptPanel.setPosition(x, y);
            promptIcon.setPosition(x + 6, y + 6);
            promptLabel.setPosition(x + 34, y + 8);
        }

        private void setPromptVisible(boolean visible) {
            promptPanel.setVisible(visible);
            promptIcon.setVisible(visible);
            promptLabel.setVisible(visible);
        }

        private String iconFor(InteractionVerb verb) {
            return switch (verb) {
                case EXAMINE -> UI + "icon_interact_examine.png";
                case TALK -> UI + "icon_interact_talk.png";
                case COLLECT -> UI + "icon_interact_collect.png";
                case ENTER -> UI + "icon_interact_enter.png";
            };
        }

        private String verbLabel(InteractionVerb verb) {
            return switch (verb) {
                case EXAMINE -> "Examinar";
                case TALK -> "Conversar";
                case COLLECT -> "Coletar";
                case ENTER -> "Entrar";
            };
        }
    }

    // ==================== DialogueController (aninhada) ====================

    /**
     * Caixa de dialogo em banda inferior (no maximo ~27% da altura da tela). Todo o
     * texto vem do snapshot da exploracao, que por sua vez vem de dados — nenhuma
     * fala vive neste controlador. Bloqueio de movimento durante o dialogo e
     * resolvido pela propria ExplorationSimulation; esta classe so apresenta o
     * estado atual.
     */
    private static final class DialogueController extends IgnisScript {

        private static final String UI = "assets/ui/";
        private static final double MAX_HEIGHT_RATIO = 0.27;

        private UIPanel panel;
        private UIImage sageIcon;
        private UILabel speakerLabel;
        private UILabel textLabel;
        private UILabel continueHint;
        private boolean visible;

        void setup() {
            panel = createPanel(0, 0, 760, 150);
            setUIColors(panel, new Color(5, 13, 20, 240), null, new Color(70, 150, 200));
            sageIcon = createImage(UI + "icon_great_sage_indicator.png", 0, 0, 24, 24);
            speakerLabel = createLabel("", 0, 0, 220, 22);
            speakerLabel.setFont("SansSerif", Font.BOLD, 15);
            speakerLabel.setTextColor(new Color(120, 226, 255));
            textLabel = createLabel("", 0, 0, 700, 70);
            textLabel.setFont("SansSerif", Font.PLAIN, 17);
            textLabel.setTextColor(new Color(232, 244, 248));
            textLabel.setMultiline(true);
            continueHint = createLabel("[E] continuar", 0, 0, 200, 20);
            continueHint.setFont("SansSerif", Font.ITALIC, 12);
            continueHint.setTextColor(new Color(150, 190, 200));
            applyVisibility(false);
        }

        void sync(ExplorationSnapshot snapshot) {
            boolean active = snapshot.dialogueActive();
            if (active != visible) {
                applyVisibility(active);
            }
            if (active) {
                layout();
                String speaker = snapshot.dialogueSpeaker();
                // O indicador do Grande Sabio e so dele — falas de Rimuru ou de outros
                // personagens nao usam um retrato inventado (revisao visual do Codex).
                sageIcon.setVisible(isGreatSage(speaker));
                speakerLabel.setText(speaker);
                textLabel.setText(snapshot.dialogueText());
                continueHint.setText(snapshot.dialogueHasMore() ? "[E] continuar" : "[E] fechar");
            }
        }

        private boolean isGreatSage(String speaker) {
            return speaker != null && speaker.toLowerCase(java.util.Locale.ROOT).contains("sabio");
        }

        boolean isActive() {
            return visible;
        }

        private void layout() {
            double width = Math.max(480, getGame().getWidth());
            double height = Math.max(360, getGame().getHeight());
            // Piso de largura proporcional a viewport (nao um fixo de 720px), para nao
            // estourar em janelas menores (revisao visual do Codex).
            double panelWidth = Math.min(1120, Math.max(width * 0.6, width - 120));
            double panelHeight = Math.min(height * MAX_HEIGHT_RATIO, 192);
            double x = (width - panelWidth) / 2.0;
            double y = height - panelHeight - 18;
            panel.setSize(panelWidth, panelHeight);
            panel.setPosition(x, y);
            sageIcon.setPosition(x + 20, y + 18);
            speakerLabel.setPosition(x + 52, y + 18);
            textLabel.setSize(panelWidth - 44, panelHeight - 66);
            textLabel.setPosition(x + 22, y + 46);
            continueHint.setPosition(x + panelWidth - 140, y + panelHeight - 26);
        }

        private void applyVisibility(boolean value) {
            visible = value;
            panel.setVisible(value);
            sageIcon.setVisible(value);
            speakerLabel.setVisible(value);
            textLabel.setVisible(value);
            continueHint.setVisible(value);
        }
    }
}
