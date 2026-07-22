import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.rimurusurvivors.domain.NarrativeBeat;
import com.rimurusurvivors.domain.NarrativeLine;
import com.rimurusurvivors.domain.NarrativeSequence;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Saida da caverna e primeiro contato com os goblins da Floresta de Jura (marco
 * seguinte ao encontro com Veldora). Diretor proprio, anexado ao objeto
 * "GoblinContactDirector" via attach_script — NAO foi adicionado ao
 * CutsceneDirector (que ja tem 755 linhas com o despertar + Veldora); cada
 * cutscene grande fica no seu proprio GameObject, conforme o contrato da tarefa.
 *
 * <p>Ver o comentario de classe de ExplorationDirector.java para a explicacao
 * completa de por que a coordenacao entre scripts usa sceneDispatcher em vez de
 * referencia direta: scripts do Ignis compilam isoladamente, entao os nomes de
 * sinal sao strings duplicadas por arquivo de proposito e cada sinal aqui tem o
 * par exato em GameFlowController.java.</p>
 *
 * <p>A sequencia narrativa (avanco, auto-beats por tempo, skip e finalizacao
 * idempotente) pertence a NarrativeSequence do dominio; este diretor so adapta
 * beats para camera, retratos, VFX de mundo e musica. Fim natural e skip passam
 * pelo MESMO finalizador (finishVisualState) — nunca ha dois caminhos de saida.
 * O jogo termina em exploracao conversacional da floresta, nunca no
 * HordeEncounter (roteamento em GameFlowController).</p>
 */
public final class GoblinContactDirector extends IgnisScript {

    // Sinal recebido de GameFlowController: comeca a cutscene do contato goblin.
    // Sem payload — encontra o ator "Rimuru" (na boca da caverna) e os dados de
    // data/cutscene-goblin-first-contact.json sozinho, como AwakeningCutscene faz.
    private static final String SIGNAL_ENTER_GOBLIN_CONTACT = "TENSURA_ENTER_GOBLIN_CONTACT";
    // Sinal enviado para GameFlowController quando a cutscene conclui (fim natural
    // ou skip — o mesmo finalizador idempotente cobre os dois caminhos). O
    // desbloqueio da rota (goblin_village_route_unlocked) e a entrada na exploracao
    // da floresta sao decididos por GameFlowController ao receber este sinal.
    private static final String SIGNAL_GOBLIN_CONTACT_COMPLETE = "TENSURA_GOBLIN_CONTACT_COMPLETE";

    private static final String GOBLIN_DATA = "data/cutscene-goblin-first-contact.json";

    private final GoblinFirstContact contact = new GoblinFirstContact();

    private boolean active;

    @Override
    public void start() {
        contact.init(getGameObject(), getGame());
        onSceneSignal(SIGNAL_ENTER_GOBLIN_CONTACT, payload -> beginGoblinContact());
    }

    @Override
    public void tick() {
        if (!active) return;
        // advanceAction do JSON = INTERACT: mesma tecla da exploracao/dialogo (E),
        // com ENTER como alias tolerante (igual a AwakeningCutscene).
        boolean advancePressed = isKeyJustPressed("E") || isKeyJustPressed("ENTER");
        if (contact.update(getDeltaTime(), advancePressed, isKeyJustPressed("ESCAPE"))) {
            active = false;
            sceneDispatcher.enqueue(SIGNAL_GOBLIN_CONTACT_COMPLETE, null);
        }
    }

    private void beginGoblinContact() {
        GameObject player = findObject("Rimuru");
        contact.play(readJson(GOBLIN_DATA), player);
        active = true;
        log("GoblinContactDirector: primeiro contato goblin iniciado.");
    }

    private JSONObject readJson(String relativePath) {
        File file = AssetResolver.resolve(relativePath);
        try {
            return new JSONObject(java.nio.file.Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar " + relativePath, exception);
        }
    }

    // ==================== GoblinFirstContact (aninhada) ====================

    /**
     * Adapta a sequencia narrativa de dados para retratos, camera, VFX de mundo e
     * a troca de trilha (ambiente da caverna -> tema da floresta). A classe
     * NarrativeSequence continua dona de avanco, auto-beats, skip e finalizacao;
     * esta classe so apresenta o estado atual.
     */
    private static final class GoblinFirstContact extends IgnisScript {

        private static final String PORTRAITS = "assets/sprites/portraits/";
        private static final String FOREST_MUSIC = "assets/music/tempest_forest_theme.wav";
        private static final String CAVE_EXIT_CLIP = "cutscene_cave_exit_story_v1";
        private static final String GOBLIN_EMERGE_CLIP = "cutscene_goblin_scout_emerge_v3";
        private static final String GOBLIN_ALARM_CLIP = "cutscene_goblin_scout_alarm_v3";
        private static final String GOBLIN_LOWER_CLIP = "cutscene_goblin_scout_lower_weapons_v3";
        private static final String GOBLIN_BOW_CLIP = "cutscene_goblin_scout_bow_v3";
        private static final int GOBLIN_GROUP_SIZE = 192;
        private static final double FOREST_CAMERA_X = 320.0;
        private static final double FOREST_CAMERA_Y = 256.0;
        private static final double FOREST_CAMERA_ZOOM = 1.95;
        private static final String SPEAKER_GREAT_SAGE = "great_sage";
        private static final String SPEAKER_RIMURU = "rimuru";

        private final Map<String, SpriteAnimation> clips = new HashMap<>();
        private final Map<String, String> speakerNames = new HashMap<>();

        private NarrativeSequence sequence;
        private GameObject actor;
        private GameObject cinematicPanel;
        private GameObject goblinGroupVisual;
        private double actorCenterX;
        private double actorCenterY;
        // Ancora de CHAO do grupo goblin (centro-inferior): x central, y na linha dos
        // pes. O visual 384x384 sobe a partir daqui, entao o pivot fica no rodape.
        private double groupGroundX;
        private double groupGroundY;
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

        void play(JSONObject data, GameObject player) {
            Objects.requireNonNull(data, "goblin contact data");
            Objects.requireNonNull(player, "goblin contact actor");
            actor = player;
            actorCenterX = actor.getX() + actor.getWidth() / 2.0;
            actorCenterY = actor.getY() + actor.getHeight() / 2.0;
            cinematicPanel = findObject("CaveExitCinematicPanel");
            groupGroundX = 496;
            groupGroundY = 220;
            completionDelivered = false;
            shownBeatId = null;
            shownLineId = null;
            speakerNames.clear();
            parseParticipants(data.getJSONObject("participants"));
            sequence = new NarrativeSequence(
                    parseBeats(data.getJSONArray("beats")), data.getDouble("skippableAfterSeconds"));

            actor.setVisible(true);
            actor.setOpacity(1);
            setupUi();
            enterBeat(sequence.currentBeat().id());
            showLine(sequence.currentLine());
            setCameraPosition(FOREST_CAMERA_X, FOREST_CAMERA_Y);
            setCameraZoom(FOREST_CAMERA_ZOOM);
            log("Contato goblin iniciado: beats=" + data.getJSONArray("beats").length());
        }

        /** Retorna true uma unica vez quando o controle deve voltar ao GameFlow. */
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
            log("Contato goblin concluido: tempo=" + sequence.elapsedSeconds());
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

        private void enterBeat(String beatId) {
            shownBeatId = beatId;
            switch (beatId) {
                case "cave_exit_cinematic" -> {
                    destroyGoblinGroup();
                    actor.setVisible(false);
                    sceneCaption.setVisible(false);
                    if (cinematicPanel != null) {
                        cinematicPanel.setVisible(true);
                        attachAndPlay(cinematicPanel, CAVE_EXIT_CLIP, false);
                    }
                    setMusicVolume(0.32f);
                    playMusic(FOREST_MUSIC, true);
                    setCameraPosition(FOREST_CAMERA_X, FOREST_CAMERA_Y);
                    setCameraZoom(FOREST_CAMERA_ZOOM);
                }
                case "forest_silence" -> {
                    hideCinematicPanel();
                    actor.setVisible(true);
                    actor.setOpacity(1);
                    sceneCaption.setVisible(false);
                    setCameraPosition(FOREST_CAMERA_X, FOREST_CAMERA_Y);
                    setCameraZoom(FOREST_CAMERA_ZOOM);
                }
                case "scouts_emerge" -> {
                    hideCinematicPanel();
                    actor.setVisible(true);
                    sceneCaption.setVisible(false);
                    goblinGroupVisual = createBottomPivotVisual(
                            "GoblinScoutGroup", groupGroundX, groupGroundY, GOBLIN_GROUP_SIZE, 36);
                    attachAndPlay(goblinGroupVisual, GOBLIN_EMERGE_CLIP, false);
                    setCameraPosition(FOREST_CAMERA_X, FOREST_CAMERA_Y);
                    setCameraZoom(FOREST_CAMERA_ZOOM);
                }
                case "scouts_alarm" -> {
                    sceneCaption.setVisible(false);
                    attachAndPlay(goblinGroupVisual, GOBLIN_ALARM_CLIP, false);
                }
                case "cautious_lowering" -> {
                    sceneCaption.setVisible(false);
                    attachAndPlay(goblinGroupVisual, GOBLIN_LOWER_CLIP, false);
                }
                case "scouts_bow" -> {
                    sceneCaption.setVisible(false);
                    attachAndPlay(goblinGroupVisual, GOBLIN_BOW_CLIP, false);
                }
                case "request_and_choice" -> sceneCaption.setVisible(false);
                case "forest_handoff" -> {
                    // eventsOnEnter "goblin_village_route_unlocked": beat automatico curto
                    // que entrega o controle. O desbloqueio real e a entrada na exploracao
                    // sao emitidos por GameFlowController ao receber COMPLETE.
                    sceneCaption.setVisible(false);
                    destroyGoblinGroup();
                    setCameraPosition(FOREST_CAMERA_X, FOREST_CAMERA_Y);
                    setCameraZoom(FOREST_CAMERA_ZOOM);
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
            boolean sage = SPEAKER_GREAT_SAGE.equals(speaker);
            boolean rimuru = SPEAKER_RIMURU.equals(speaker);
            // Grande Sabio usa o indicador ciano no lugar de retrato (regra dos dados);
            // o goblin scout nao tem portraitSetId, entao aparece so com a placa de nome.
            sageIcon.setVisible(sage);
            portrait.setVisible(rimuru);
            if (rimuru) portrait.setImagePath(rimuruPortrait(line.portrait()));
            continueHint.setText(sequence.currentBeat().isAutomatic()
                    ? "A cena continua..." : "[E] continuar");
            updateLayout();
        }

        private String rimuruPortrait(String emotion) {
            String safe = emotion == null || emotion.isBlank() ? "curious" : emotion;
            return PORTRAITS + "rimuru/portrait_rimuru_slime_" + safe + ".png";
        }

        private void setupUi() {
            cinemaShade = createPanel(0, 0, Math.max(480, getGame().getWidth()), Math.max(360, getGame().getHeight()));
            setUIColors(cinemaShade, new Color(0, 6, 4, 74), null, null);

            sceneCaption = createLabel("", 0, 24, 640, 36);
            sceneCaption.setAlignment(UILabel.Alignment.CENTER);
            sceneCaption.setFont("SansSerif", Font.BOLD, 19);
            sceneCaption.setTextColor(new Color(212, 245, 214));
            sceneCaption.setOutline(true, new Color(4, 12, 6), 2);

            dialoguePanel = createPanel(0, 0, 800, 154);
            setUIColors(dialoguePanel, new Color(6, 16, 11, 242), null, new Color(96, 189, 121));
            portrait = createImage(PORTRAITS + "rimuru/portrait_rimuru_slime_curious.png", 0, 0, 112, 112);
            sageIcon = createImage("assets/ui/icon_great_sage_indicator.png", 0, 0, 32, 32);
            speakerLabel = createLabel("", 0, 0, 300, 24);
            speakerLabel.setFont("SansSerif", Font.BOLD, 16);
            speakerLabel.setTextColor(new Color(150, 233, 168));
            textLabel = createLabel("", 0, 0, 620, 74);
            textLabel.setFont("SansSerif", Font.PLAIN, 17);
            textLabel.setTextColor(new Color(235, 246, 238));
            textLabel.setMultiline(true);
            continueHint = createLabel("", 0, 0, 210, 20);
            continueHint.setFont("SansSerif", Font.ITALIC, 12);
            continueHint.setTextColor(new Color(168, 205, 178));
            skipLabel = createLabel("[ESC] Pular", 0, 18, 150, 22);
            skipLabel.setFont("SansSerif", Font.BOLD, 12);
            skipLabel.setTextColor(new Color(206, 232, 210));
            skipLabel.setOutline(true, new Color(4, 12, 6), 1);
            setDialogueVisible(false);
            updateLayout();
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
            destroyGoblinGroup();
            hideCinematicPanel();
            setDialogueVisible(false);
            sceneCaption.setVisible(false);
            skipLabel.setVisible(false);
            cinemaShade.setVisible(false);
            actor.setVisible(true);
            actor.setOpacity(1);
            setCameraPosition(FOREST_CAMERA_X, FOREST_CAMERA_Y);
            setCameraZoom(FOREST_CAMERA_ZOOM);
        }

        private void hideCinematicPanel() {
            if (cinematicPanel != null) cinematicPanel.setVisible(false);
        }

        private void destroyGoblinGroup() {
            if (goblinGroupVisual != null) {
                destroy(goblinGroupVisual);
                goblinGroupVisual = null;
            }
        }

        /**
         * Cria um visual quadrado com pivot CENTRO-INFERIOR: (groundX, groundY) e a
         * base (linha dos pes), o sprite sobe a partir dai. E o pivot que o Codex
         * pediu para o grupo goblin — assim ele fica plantado no chao da floresta
         * independentemente do tamanho de render.
         */
        private GameObject createBottomPivotVisual(
                String name, double groundX, double groundY, int size, int zIndex) {
            GameObject visual = getGame().instantiatePrefab("RuntimeVisual", groundX, groundY);
            if (visual == null) {
                visual = new GameObject(name, getGame(), groundX, groundY, size, size);
                getGame().addEntity(visual);
            }
            visual.setName(name);
            visual.setWidth(size);
            visual.setHeight(size);
            visual.setX(groundX - size / 2.0);
            visual.setY(groundY);
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
                loaded = null; // clipe ausente nao pode derrubar a cutscene
            }
            clips.put(name, loaded);
            return loaded;
        }
    }
}
