import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.Camera;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.rimurusurvivors.domain.DialogueLine;
import com.rimurusurvivors.domain.DialogueScript;
import com.rimurusurvivors.domain.ExplorationArea;
import com.rimurusurvivors.domain.ExplorationEventType;
import com.rimurusurvivors.domain.ExplorationMap;
import com.rimurusurvivors.domain.ExplorationSimulation;
import com.rimurusurvivors.domain.ExplorationSnapshot;
import com.rimurusurvivors.domain.Interactable;
import com.rimurusurvivors.domain.InteractionVerb;
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
 * Mapa, jogador, camera, interacao e dialogo da exploracao (extraido de
 * GameFlowController no passo 1 do plano de engenharia). Anexado ao objeto
 * "ExplorationDirector" via attach_script.
 *
 * <p>Scripts do Ignis sao compilados isoladamente (nenhum enxerga a classe de
 * outro arquivo, mesmo ja compilada — ver com.ignis.core.ScriptManager), entao
 * a coordenacao com GameFlowController.java e CutsceneDirector.java usa
 * exclusivamente sceneDispatcher (SceneSignalDispatcher, protected em
 * IgnisScript). Os nomes de sinal sao strings duplicadas por arquivo de
 * proposito — nao ha tipo compartilhavel entre scripts alem dos tipos do jar
 * de dominio e dos tipos do proprio motor (ver plano de engenharia,
 * secao "Armadilha verificada: estado estatico no jar vaza entre sessoes").</p>
 *
 * <p>Cada sinal recebido/enviado aqui tem o par exato em GameFlowController.java
 * — mudar uma string aqui sem mudar a outra ponta quebra a coordenacao
 * silenciosamente (o compilador nao acusa, sao strings).</p>
 */
public final class ExplorationDirector extends IgnisScript {

    // Sinal recebido de GameFlowController: comeca a exploracao do zero na
    // area inicial do mapa (mesmo momento em que beginAwakening() configurava
    // a simulacao antes desta extracao).
    private static final String SIGNAL_ENTER_EXPLORATION = "TENSURA_ENTER_EXPLORATION";
    // Sinal recebido de GameFlowController: retoma o tick apos uma interrupcao
    // narrativa (hoje, o encontro com Veldora) sem recriar a simulacao.
    private static final String SIGNAL_EXPLORATION_ACTIVATE = "TENSURA_EXPLORATION_ACTIVATE";
    // Sinal enviado para GameFlowController: o dialogo do selo da galeria
    // terminou, e hora do encontro com Veldora. Sem payload — GameFlowController
    // ja sabe calcular a posicao do selo com o proprio AREA_OFFSETS.
    private static final String SIGNAL_REQUEST_VELDORA_ENCOUNTER = "TENSURA_REQUEST_VELDORA_ENCOUNTER";
    // Sinal enviado para GameFlowController: o jogador examinou a boca da caverna
    // DEPOIS do encontro com Veldora — hora de sair para a floresta e encontrar os
    // goblins. Sem payload — GoblinContactDirector le a posicao do ator sozinho.
    private static final String SIGNAL_REQUEST_GOBLIN_CONTACT = "TENSURA_REQUEST_GOBLIN_CONTACT";
    // Sinal recebido de GameFlowController: o contato goblin acabou; comece a
    // exploracao conversacional na Floresta de Jura (nova simulacao, area diferente).
    private static final String SIGNAL_ENTER_FOREST_EXPLORATION = "TENSURA_ENTER_FOREST_EXPLORATION";

    private static final String MAP_DATA = "data/cave-seal-map.json";
    private static final String DIALOGUE_DATA = "data/cave-seal-dialogues.json";
    private static final String VELDORA_TRIGGER_DIALOGUE_ID = "dlg_gallery_seal";
    // O gatilho da saida reusa o padrao comprovado do Veldora (DIALOGUE_ENDED de um
    // interactable), porque a invariante do dominio proibe um ENTER sem area de
    // destino e a floresta e uma cutscene, nao uma ExplorationArea de verdade.
    private static final String CAVE_EXIT_TRIGGER_DIALOGUE_ID = "dlg_cave_exit";
    // Area minima da floresta (retangulo aberto + goblin conversavel) para o marco
    // fechar de ponta a ponta; layout/tilemap/sprites finais ficam com o Codex.
    private static final String FOREST_AREA_ID = "jura_forest_approach";

    // Duplicado de proposito a partir de GameFlowController.AREA_OFFSETS: e
    // dado estatico de layout de nivel (onde cada area foi desenhada no MESMO
    // mundo, ja que a fatia ainda nao usa cenas reais — ver nota do offset em
    // GameFlowController), nao logica. Se uma area nova entrar no mapa, os
    // dois lugares precisam mudar juntos.
    private static final Map<String, double[]> AREA_OFFSETS = Map.of(
            "cave_awakening", new double[] {0, 0},
            "cave_gallery", new double[] {900, 0},
            FOREST_AREA_ID, new double[] {0, 700});

    private final ExplorationController explorationController = new ExplorationController();
    private final InteractionController interactionController = new InteractionController();
    private final DialogueController dialogueController = new DialogueController();

    private JSONObject mapRoot;
    private Map<String, ExplorationArea> areas;
    private Map<String, DialogueScript> dialogues;
    private boolean active;
    private boolean uiReady;
    private boolean veldoraRequested;
    // Vira true so quando GameFlowController devolve o controle apos o Veldora
    // (SIGNAL_EXPLORATION_ACTIVATE). E a guarda que impede a saida para a floresta
    // de disparar antes da hora, mesmo que o jogador ache a boca da caverna cedo.
    private boolean veldoraDone;
    private boolean goblinRequested;

    @Override
    public void start() {
        explorationController.init(getGameObject(), getGame());
        interactionController.init(getGameObject(), getGame());
        dialogueController.init(getGameObject(), getGame());

        mapRoot = readJson(MAP_DATA);
        areas = loadAreas(mapRoot);
        dialogues = loadDialogues(readJson(DIALOGUE_DATA));

        sceneDispatcher.connect(SIGNAL_ENTER_EXPLORATION, payload -> beginExploration());
        sceneDispatcher.connect(SIGNAL_EXPLORATION_ACTIVATE, payload -> {
            active = true;
            veldoraDone = true;
        });
        sceneDispatcher.connect(SIGNAL_ENTER_FOREST_EXPLORATION, payload -> beginForestExploration());
    }

    @Override
    public void tick() {
        if (!active) return;
        // Guarda defensiva: se beginExploration() foi interrompida entre marcar
        // active e configurar o controlador (ex.: excecao no meio da montagem),
        // o loop do jogo nao pode quebrar com NPE — so espera o proximo tick
        // tentar de novo. Mesma protecao que ja existia antes da extracao.
        if (!explorationController.isReady()) return;

        boolean interactPressed = isKeyJustPressed("E");
        ExplorationSnapshot snapshot = explorationController.update(getDeltaTime(), interactPressed);
        if (shouldRequestVeldoraEncounter(snapshot)) {
            active = false;
            veldoraRequested = true;
            log("Exploracao: selo da galeria dialogado, pedindo encontro com Veldora.");
            sceneDispatcher.enqueue(SIGNAL_REQUEST_VELDORA_ENCOUNTER, null);
            return;
        }
        if (shouldRequestGoblinContact(snapshot)) {
            active = false;
            goblinRequested = true;
            log("Exploracao: boca da caverna examinada apos Veldora, pedindo contato goblin.");
            sceneDispatcher.enqueue(SIGNAL_REQUEST_GOBLIN_CONTACT, null);
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
    }

    private void beginExploration() {
        String startAreaId = mapRoot.getString("startArea");
        JSONObject startAreaJson = findAreaJson(mapRoot, startAreaId);
        beginExplorationAt(startAreaId, startAreaJson.getDouble("spawnX"), startAreaJson.getDouble("spawnY"));
        log("ExplorationDirector: exploracao configurada em " + startAreaId + ".");
    }

    private void beginForestExploration() {
        JSONObject forestAreaJson = findAreaJson(mapRoot, FOREST_AREA_ID);
        beginExplorationAt(FOREST_AREA_ID, forestAreaJson.getDouble("spawnX"), forestAreaJson.getDouble("spawnY"));
        log("ExplorationDirector: exploracao conversacional da floresta iniciada.");
    }

    /**
     * Monta (ou remonta) a simulacao de exploracao numa area/spawn. A UI de
     * interacao e dialogo e criada uma unica vez (uiReady) — chamar setup() a cada
     * troca de area empilharia paineis fantasmas; so a simulacao e recriada.
     */
    private void beginExplorationAt(String areaId, double spawnX, double spawnY) {
        ExplorationSimulation simulation =
                new ExplorationSimulation(areas, dialogues, areaId, spawnX, spawnY);
        GameObject player = findObject("Rimuru");
        explorationController.configure(simulation, player, AREA_OFFSETS);
        if (!uiReady) {
            interactionController.setup();
            dialogueController.setup();
            uiReady = true;
        }
        active = true;
    }

    private boolean shouldRequestVeldoraEncounter(ExplorationSnapshot snapshot) {
        if (veldoraRequested || snapshot == null || snapshot.events() == null) return false;
        return snapshot.events().stream().anyMatch(event ->
                event.type() == ExplorationEventType.DIALOGUE_ENDED
                        && VELDORA_TRIGGER_DIALOGUE_ID.equals(event.detail()));
    }

    private boolean shouldRequestGoblinContact(ExplorationSnapshot snapshot) {
        if (!veldoraDone || goblinRequested || snapshot == null || snapshot.events() == null) return false;
        return snapshot.events().stream().anyMatch(event ->
                event.type() == ExplorationEventType.DIALOGUE_ENDED
                        && CAVE_EXIT_TRIGGER_DIALOGUE_ID.equals(event.detail()));
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

    // ==================== ExplorationController (aninhada) ====================

    /**
     * Move o jogador da exploracao, delega colisao/foco/dialogo para
     * ExplorationSimulation e sincroniza o GameObject visual (posicao, locomocao,
     * camera). Nao conhece dados de mapa nem conteudo de dialogo — so a simulacao e
     * o visual do jogador. Instanciada e dirigida manualmente pelo ExplorationDirector
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

        /** Falso ate configure() rodar — guarda usada pelo ExplorationDirector antes de chamar update(). */
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
