import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Diretor da resolucao do duelo dos lobos e cutscene de nomeacao de Ranga.
 *
 * <p>Adaptador FINO: toda a maquina de sequencia narrativa vive em {@link NarrativeSequence}.
 * Este script apenas traduz beats para retratos, falas na UI, camera e reacoes
 * no objeto unico {@code DireWolfLeader}.
 */
public final class DireWolfResolutionDirector extends IgnisScript {

    private static final String SIGNAL_ENTER_RESOLUTION = "TENSURA_ENTER_DIRE_WOLF_RESOLUTION";
    private static final String SIGNAL_RESOLUTION_COMPLETE = "TENSURA_DIRE_WOLF_RESOLUTION_COMPLETE";
    private static final String SIGNAL_ENTER_NAMING = "TENSURA_ENTER_NAMING_CUTSCENE";
    private static final String SIGNAL_NAMING_COMPLETE = "TENSURA_NAMING_CUTSCENE_COMPLETE";

    private static final String RESOLUTION_DATA = "data/cutscene-dire-wolf-resolution.json";
    private static final String NAMING_DATA = "data/cutscene-naming-first-village.json";

    private final ResolutionHandler handler = new ResolutionHandler();
    private Mode currentMode = Mode.NONE;

    private enum Mode {
        NONE,
        RESOLUTION,
        NAMING
    }

    @Override
    public void start() {
        handler.init(getGameObject(), getGame());
        onSceneSignal(SIGNAL_ENTER_RESOLUTION, payload -> beginResolution());
        onSceneSignal(SIGNAL_ENTER_NAMING, payload -> beginNaming());
        log("DireWolfResolutionDirector: inicializado.");
    }

    @Override
    public void tick() {
        if (currentMode == Mode.NONE) return;

        boolean advancePressed = isKeyJustPressed("E") || isKeyJustPressed("ENTER");
        boolean skipPressed = isKeyJustPressed("ESCAPE");

        if (handler.update(getDeltaTime(), advancePressed, skipPressed)) {
            if (currentMode == Mode.RESOLUTION) {
                currentMode = Mode.NONE;
                sceneDispatcher.enqueue(SIGNAL_RESOLUTION_COMPLETE, null);
                log("DireWolfResolutionDirector: resolucao concluida.");
            } else if (currentMode == Mode.NAMING) {
                currentMode = Mode.NONE;
                sceneDispatcher.enqueue(SIGNAL_NAMING_COMPLETE, null);
                log("DireWolfResolutionDirector: nomeacao de Ranga concluida.");
            }
        }
    }

    private void beginResolution() {
        GameObject player = findObject("Rimuru");
        GameObject leader = findObject("DireWolfLeader");
        handler.play(readJson(RESOLUTION_DATA), player, leader);
        currentMode = Mode.RESOLUTION;
    }

    private void beginNaming() {
        GameObject player = findObject("Rimuru");
        GameObject leader = findObject("DireWolfLeader");
        handler.play(readJson(NAMING_DATA), player, leader);
        currentMode = Mode.NAMING;
    }

    private JSONObject readJson(String relativePath) {
        File file = AssetResolver.resolve(relativePath);
        try {
            return new JSONObject(Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar " + relativePath, exception);
        }
    }

    // ==================== ResolutionHandler (aninhada) ====================

    private static final class ResolutionHandler extends IgnisScript {

        private NarrativeSequence sequence;
        private GameObject player;
        private GameObject leader;
        private boolean completionDelivered;
        private final Map<String, String> speakerNames = new HashMap<>();

        private UIPanel dialoguePanel;
        private UILabel speakerLabel;
        private UILabel textLabel;
        private UILabel hintLabel;
        private UIPanel sageIndicator;
        private boolean uiReady;

        private final Map<String, SpriteAnimation> animationCache = new HashMap<>();

        void init(GameObject owner, Object game) {
            preloadClip("dire_wolf_leader_idle");
            preloadClip("dire_wolf_leader_reaction_hit_v1");
            preloadClip("dire_wolf_leader_reaction_surrender_v1");
            preloadClip("dire_wolf_leader_reaction_alliance_v1");
            preloadClip("cutscene_ranga_arrival_canon_v2");
        }

        void play(JSONObject data, GameObject playerActor, GameObject leaderActor) {
            player = playerActor;
            leader = leaderActor;
            completionDelivered = false;

            speakerNames.clear();
            if (data.has("participants")) {
                JSONObject participants = data.getJSONObject("participants");
                for (String id : participants.keySet()) {
                    speakerNames.put(id, participants.getJSONObject(id).getString("displayName"));
                }
            }

            List<NarrativeBeat> beats = parseBeats(data.getJSONArray("beats"));
            double skippable = data.optDouble("skippableAfterSeconds", 0.5);
            sequence = new NarrativeSequence(beats, skippable);

            if (leader != null) {
                leader.setVisible(true);
                leader.setOpacity(1);
            }

            // Forca recriacao: clearUI() do GameFlowController limpa o UICanvas
            // GLOBAL (compartilhado), nao so o proprio. Sem isto, uma segunda chamada
            // a play() nesta mesma sessao de Play (retomada de save re-entrando na
            // cutscene, por exemplo) pularia setupUi() e apontaria para widgets
            // orfaos ja removidos do canvas — mesma causa raiz corrigida em
            // BattleDirector.beginDuel().
            uiReady = false;
            setupUi();
            setUiVisible(true);
            applyBeatState(sequence.currentBeat());
            showLine(sequence.currentLine());
        }

        boolean update(double dt, boolean advancePressed, boolean skipPressed) {
            if (sequence == null || completionDelivered) return true;
            if (skipPressed) sequence.requestSkip();

            String beforeBeat = sequence.currentBeat() != null ? sequence.currentBeat().id() : "";
            NarrativeLine beforeLine = sequence.currentLine();

            sequence.update(dt, advancePressed);

            if (!sequence.isFinished()) {
                String afterBeat = sequence.currentBeat() != null ? sequence.currentBeat().id() : "";
                NarrativeLine afterLine = sequence.currentLine();

                if (!afterBeat.equals(beforeBeat)) {
                    applyBeatState(sequence.currentBeat());
                }
                if (!Objects.equals(afterLine, beforeLine)) {
                    showLine(afterLine);
                }
            }

            if (!sequence.consumeCompletion()) return false;
            finish();
            completionDelivered = true;
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

        private void applyBeatState(NarrativeBeat beat) {
            if (beat == null || leader == null) return;
            String beatId = beat.id();
            String clipName = switch (beatId) {
                case "leader_staggers" -> "dire_wolf_leader_reaction_hit_v1";
                case "wolf_surrenders" -> "dire_wolf_leader_reaction_surrender_v1";
                case "ranga_name" -> "dire_wolf_leader_reaction_alliance_v1";
                default -> "dire_wolf_leader_idle";
            };

            SpriteAnimation anim = clip(clipName);
            if (anim != null) {
                Animator animator = leader.getOrCreateAnimator();
                animator.addAnimation(anim);
                animator.play(anim.getName());
            }
        }

        private void showLine(NarrativeLine line) {
            if (!uiReady) return;
            if (line == null) {
                dialoguePanel.setVisible(false);
                return;
            }
            dialoguePanel.setVisible(true);
            String displayName = speakerNames.getOrDefault(line.speaker(), line.speaker());
            speakerLabel.setText(displayName);

            boolean isSage = "great_sage".equalsIgnoreCase(line.speaker());
            sageIndicator.setVisible(isSage);

            textLabel.setText(line.text());
            hintLabel.setText("[E] Continuar");
        }

        private void finish() {
            setUiVisible(false);
        }

        private void setupUi() {
            if (uiReady) return;
            uiReady = true;
            dialoguePanel = createPanel(0, 0, 800, 130);
            setUIColors(dialoguePanel, new Color(10, 18, 26, 230), null, new Color(100, 160, 210));

            speakerLabel = createLabel("", 0, 0, 760, 24);
            speakerLabel.setFont("SansSerif", Font.BOLD, 15);
            speakerLabel.setTextColor(new Color(230, 242, 250));

            textLabel = createLabel("", 0, 0, 760, 60);
            textLabel.setFont("SansSerif", Font.PLAIN, 14);
            textLabel.setTextColor(new Color(220, 235, 245));
            textLabel.setMultiline(true);

            hintLabel = createLabel("", 0, 0, 200, 20);
            hintLabel.setFont("SansSerif", Font.ITALIC, 12);
            hintLabel.setTextColor(new Color(160, 190, 210));

            sageIndicator = createPanel(0, 0, 6, 24);
            setUIColors(sageIndicator, new Color(0, 220, 255), null, null);

            layoutUi();
        }

        private void layoutUi() {
            if (!uiReady) return;
            double width = Math.max(640, getGame().getWidth());
            double height = Math.max(400, getGame().getHeight());
            double panelW = Math.min(840, width - 40);
            dialoguePanel.setSize(panelW, 130);
            double panelX = (width - panelW) / 2.0;
            double panelY = height - 150;
            dialoguePanel.setPosition(panelX, panelY);

            speakerLabel.setPosition(panelX + 24, panelY + 12);
            sageIndicator.setPosition(panelX + 12, panelY + 12);
            textLabel.setSize(panelW - 36, 60);
            textLabel.setPosition(panelX + 24, panelY + 40);
            hintLabel.setPosition(panelX + panelW - 140, panelY + 100);
        }

        private void setUiVisible(boolean visible) {
            if (!uiReady) return;
            dialoguePanel.setVisible(visible);
            speakerLabel.setVisible(visible);
            textLabel.setVisible(visible);
            hintLabel.setVisible(visible);
            sageIndicator.setVisible(visible);
        }

        private void preloadClip(String clipName) {
            File file = AssetResolver.resolve("assets/animations/" + clipName + ".anim.json");
            if (!file.exists()) return;
            try {
                SpriteAnimation anim = AnimationIO.load(file);
                animationCache.put(clipName, anim);
            } catch (Exception ignored) { }
        }

        private SpriteAnimation clip(String name) {
            return animationCache.get(name);
        }
    }
}
