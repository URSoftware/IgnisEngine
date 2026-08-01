import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.rimurusurvivors.domain.CutsceneCue;
import com.rimurusurvivors.domain.CutsceneTimeline;
import com.rimurusurvivors.domain.ManualAdvanceGate;
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
 * Timeline, atores, VFX, skip e handoff das duas cutscenes da fatia da
 * Caverna do Selo (despertar e primeiro encontro com Veldora), extraido de
 * GameFlowController no passo 1 do plano de engenharia. Anexado ao objeto
 * "CutsceneDirector" via attach_script.
 *
 * <p>Ver o comentario de classe de ExplorationDirector.java para a explicacao
 * completa de por que a coordenacao entre scripts usa sceneDispatcher em vez
 * de referencia direta.</p>
 */
public final class CutsceneDirector extends IgnisScript {

    // Sinal recebido de GameFlowController: comeca a cutscene do despertar.
    // Sem payload — encontra o ator "Rimuru" e os dados de
    // data/cutscene-awakening.json sozinho.
    private static final String SIGNAL_ENTER_AWAKENING_CUTSCENE = "TENSURA_ENTER_AWAKENING_CUTSCENE";
    // Sinal enviado para GameFlowController quando a cutscene do despertar
    // conclui (fim natural ou skip — o mesmo finalizador idempotente cobre os
    // dois caminhos, ver AwakeningCutscene.update()).
    private static final String SIGNAL_AWAKENING_CUTSCENE_COMPLETE = "TENSURA_AWAKENING_CUTSCENE_COMPLETE";
    // Sinal recebido de GameFlowController: comeca o encontro com Veldora.
    // Payload: double[]{sealCenterX, sealCenterY} — a unica coisa que este
    // diretor nao pode calcular sozinho, porque a posicao do selo depende do
    // AREA_OFFSETS que GameFlowController ja mantem para posicionar o jogador.
    private static final String SIGNAL_ENTER_VELDORA_ENCOUNTER = "TENSURA_ENTER_VELDORA_ENCOUNTER";
    // Sinal enviado para GameFlowController quando o encontro com Veldora conclui.
    private static final String SIGNAL_VELDORA_ENCOUNTER_COMPLETE = "TENSURA_VELDORA_ENCOUNTER_COMPLETE";

    private static final String AWAKENING_DATA = "data/cutscene-awakening.json";
    private static final String VELDORA_DATA = "data/cutscene-veldora-first-meeting.json";

    private final AwakeningCutscene awakeningCutscene = new AwakeningCutscene();
    private final VeldoraEncounter veldoraEncounter = new VeldoraEncounter();

    private enum ActiveCutscene { NONE, AWAKENING, VELDORA }

    private ActiveCutscene active = ActiveCutscene.NONE;

    @Override
    public void start() {
        awakeningCutscene.init(getGameObject(), getGame());
        veldoraEncounter.init(getGameObject(), getGame());

        onSceneSignal(SIGNAL_ENTER_AWAKENING_CUTSCENE, payload -> beginAwakeningCutscene());
        onSceneSignal(SIGNAL_ENTER_VELDORA_ENCOUNTER, payload -> beginVeldoraEncounter((double[]) payload));
    }

    @Override
    public void tick() {
        if (active == ActiveCutscene.AWAKENING) {
            boolean advanceDown = isKeyPressed("E") || isKeyPressed("ENTER");
            boolean advancePressed = isKeyJustPressed("E") || isKeyJustPressed("ENTER");
            if (awakeningCutscene.update(
                    getDeltaTime(), advanceDown, advancePressed, isKeyJustPressed("ESCAPE"))) {
                active = ActiveCutscene.NONE;
                sceneDispatcher.enqueue(SIGNAL_AWAKENING_CUTSCENE_COMPLETE, null);
            }
            return;
        }
        if (active == ActiveCutscene.VELDORA) {
            if (veldoraEncounter.update(getDeltaTime(), isKeyJustPressed("E"), isKeyJustPressed("ESCAPE"))) {
                active = ActiveCutscene.NONE;
                sceneDispatcher.enqueue(SIGNAL_VELDORA_ENCOUNTER_COMPLETE, null);
            }
        }
    }

    private void beginAwakeningCutscene() {
        GameObject player = findObject("Rimuru");
        awakeningCutscene.play(readJson(AWAKENING_DATA), player);
        active = ActiveCutscene.AWAKENING;
        log("CutsceneDirector: cutscene do despertar iniciada.");
    }

    private void beginVeldoraEncounter(double[] sealCenter) {
        GameObject player = findObject("Rimuru");
        veldoraEncounter.play(readJson(VELDORA_DATA), player, sealCenter[0], sealCenter[1]);
        active = ActiveCutscene.VELDORA;
        log("CutsceneDirector: encontro com Veldora iniciado.");
    }

    private JSONObject readJson(String relativePath) {
        File file = AssetResolver.resolve(relativePath);
        try {
            return new JSONObject(Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar " + relativePath, exception);
        }
    }

    // ==================== AwakeningCutscene (aninhada) ====================

    /**
     * Apresenta uma timeline sem colocar regra de tempo no fluxo principal. Os cues
     * vem de dados; a classe de dominio garante skip e conclusao idempotentes.
     */
    private static final class AwakeningCutscene extends IgnisScript {

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
        private String sequenceId;
        private String manualGateCue;
        private String lastFeedbackCue;
        // O passo de frame pode cruzar o portao manual e o cue seguinte na MESMA chamada
        // de advance. O que vier depois do portao fica aqui e so e aplicado quando o
        // jogador dispensa a analise, para que o feedback responda a dispensa.
        private final List<String> deferredCues = new ArrayList<>();
        // Relogio de instrumentacao: soma deltaTime sempre, inclusive durante o portao
        // manual, para que o carimbo seja monotono mesmo com a timeline congelada.
        private double traceClockSeconds;

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
            sequenceId = data.optString("sequenceId", "cave_awakening");
            JSONObject rhythm = data.optJSONObject("rhythmContract");
            manualGateCue = rhythm == null
                    ? "great_sage_analysis"
                    : rhythm.optString("manualGateCue", "great_sage_analysis");
            lastFeedbackCue = cues.size() < 2 ? null : cues.get(cues.size() - 2).id();
            deferredCues.clear();
            traceClockSeconds = 0;
            // Estado inicial do contrato visual: anel oculto antes da convergencia.
            setFormationRuneVisible(false);
            setupUi();
            attachClip(actor, CLIP_REVEAL);
            attachClip(actor, CLIP_IDLE);
            actor.setVisible(false);
            actor.setOpacity(0);
            setCameraPosition(actorCenterX, actorCenterY);
            setCameraZoom(1.42);
            trace("sequence_started", sequenceId, "signal");
            log("Cutscene cave_awakening iniciada: duracao="
                    + data.getDouble("durationSeconds") + "s, cues=" + cues.size());
        }

        /** Retorna true uma unica vez quando o controle deve passar a exploracao. */
        boolean update(
                double deltaTime, boolean advanceDown, boolean advancePressed, boolean skipPressed) {
            if (timeline == null || handoffDelivered) return false;
            if (Double.isFinite(deltaTime) && deltaTime > 0) {
                traceClockSeconds += deltaTime;
            }
            if (skipPressed) {
                boolean accepted = timeline.requestSkip();
                trace(accepted ? "skip_accepted" : "skip_rejected_too_early",
                        sequenceId, "input:ESCAPE");
                log("Pedido de skip da cutscene: aceito=" + accepted
                        + ", tempo=" + timeline.elapsedSeconds());
                if (accepted && analysisPaused) {
                    trace("manual_gate_dismissed", manualGateCue, "skip");
                }
                if (accepted) {
                    analysisPaused = false;
                    // Skip converge no mesmo estado final: os cues adiados nao viram
                    // apresentacao, o finalizador unico cuida do visual.
                    deferredCues.clear();
                }
            }
            if (analysisPaused && !timeline.isFinished()) {
                if (analysisAdvanceGate.update(deltaTime, advanceDown, advancePressed)) {
                    analysisPaused = false;
                    trace("manual_gate_dismissed", manualGateCue, "input:E_OR_ENTER");
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
                // Mesmo frame da dispensa: o feedback adiado sai agora, nao no proximo cue.
                applyPendingCues();
            }
            if (!analysisPaused) {
                deferredCues.addAll(timeline.advance(deltaTime));
                applyPendingCues();
            }
            updatePresentation();
            if (analysisPaused) return false;
            if (!timeline.consumeCompletion()) return false;
            trace("finalizer_committed", sequenceId, "timeline");
            log("Cutscene cave_awakening concluida: tempo=" + timeline.elapsedSeconds());
            finishVisualState();
            trace("camera_restored", sequenceId, "adapter");
            handoffDelivered = true;
            // Aqui a cutscene apenas PEDE o controle. O input_restored real e emitido
            // pelo ExplorationDirector quando a exploracao passa a aceitar teclado.
            trace("input_restore_requested", sequenceId, "adapter");
            return true;
        }

        /**
         * Liga/desliga o anel de formacao autoral. O objeto pertence ao Codex: aqui so a
         * visibilidade muda, nunca sprite, transform, z-index ou instancia.
         */
        private void setFormationRuneVisible(boolean visible) {
            GameObject rune = findObject("CaveAwakeningRuneRing");
            if (rune == null) {
                log("CaveAwakeningRuneRing ausente na cena; convergencia segue sem o anel.");
                return;
            }
            rune.setVisible(visible);
        }

        /** Consome os cues pendentes e para no portao manual, guardando o resto do lote. */
        private void applyPendingCues() {
            while (!deferredCues.isEmpty()) {
                String cue = deferredCues.remove(0);
                applyCue(cue);
                if (analysisPaused) {
                    return;
                }
            }
        }

        /** Instrumentacao do contrato de polimento: id, beat, carimbo monotono e origem. */
        private void trace(String event, String beatId, String origin) {
            // t = relogio da sequencia; mono = relogio monotono do processo, unico jeito
            // de ordenar este trace contra o input_restored emitido pelo ExplorationDirector.
            log("CUTSCENE_TRACE seq=" + sequenceId
                    + " beat=" + beatId
                    + " event=" + event
                    + " t=" + String.format(java.util.Locale.ROOT, "%.3f", traceClockSeconds)
                    + " mono=" + String.format(java.util.Locale.ROOT, "%.3f", System.nanoTime() / 1e9)
                    + " origin=" + origin);
        }

        private void applyCue(String cue) {
            trace("beat_or_cue_entered", cue, "timeline");
            if (cue.equals(manualGateCue)) {
                trace("manual_gate_opened", cue, "timeline");
            }
            if (cue.equals(lastFeedbackCue)) {
                trace("last_visible_or_audible_feedback", cue, "timeline");
            }
            switch (cue) {
                case "darkness_and_drops" -> {
                    playSound("assets/sounds/cave_water_ripple_01.wav", 0.22f);
                    actor.setVisible(false);
                }
                case "magicule_convergence" -> showMagiculeConvergence();
                case "slime_body_formation" -> showSlimeFormation();
                case "first_perception" -> {
                    actor.setOpacity(1);
                    setFormationRuneVisible(false);
                    setCameraZoom(1.54);
                }
                case "great_sage_analysis" -> showGreatSageAnalysis();
                case "veldora_foreshadow" -> showVeldoraForeshadow();
                case "control_handoff" -> hideAnalysis();
                default -> log("Cue de cutscene ignorado: " + cue);
            }
        }

        private void showMagiculeConvergence() {
            // 64x64 = 2x exatos sobre a fonte 32x32, preservando nearest-neighbor.
            setFormationRuneVisible(true);
            magiculeVisual = createRuntimeVisual("AwakeningMagicules", actorCenterX, actorCenterY, 64, 64, 24);
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
            // 192x192 = 0,5 exato sobre a fonte 384x384.
            scanVisual = createRuntimeVisual("AwakeningGreatSageScan", actorCenterX, actorCenterY, 192, 192, 45);
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
            // Os tres VFX da sequencia saem juntos, tanto no fim natural quanto no skip:
            // dois sao runtime e o anel de formacao e objeto autoral, que so e escondido.
            if (magiculeVisual != null) destroy(magiculeVisual);
            if (scanVisual != null) destroy(scanVisual);
            magiculeVisual = null;
            scanVisual = null;
            setFormationRuneVisible(false);
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

    // ==================== VeldoraEncounter (aninhada) ====================

    /**
     * Adapta a sequencia narrativa de dados para retratos, camera e VFX. A classe
     * NarrativeSequence continua dona de avanco, auto-beats, skip e finalizacao.
     */
    private static final class VeldoraEncounter extends IgnisScript {

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
        private boolean absorptionTransitionActive;
        private double absorptionTransitionElapsed;
        private double veldoraDepartureCenterX;
        private double veldoraDepartureCenterY;
        private int veldoraDepartureWidth;
        private int veldoraDepartureHeight;

        private static final double ABSORPTION_TRANSITION_SECONDS = 1.05;
        private static final double ABSORPTION_RISE_PIXELS = 56.0;

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

            updateAbsorptionTransition(deltaTime);

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
                    beginAbsorptionTransition();
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
            absorptionTransitionActive = false;
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

        private void beginAbsorptionTransition() {
            if (veldoraVisual == null) {
                destroyWorldVisual(barrierVisual);
                barrierVisual = null;
                absorptionTransitionActive = false;
                return;
            }
            absorptionTransitionElapsed = 0;
            absorptionTransitionActive = true;
            veldoraDepartureWidth = veldoraVisual.getWidth();
            veldoraDepartureHeight = veldoraVisual.getHeight();
            veldoraDepartureCenterX = veldoraVisual.getX() + veldoraDepartureWidth / 2.0;
            veldoraDepartureCenterY = veldoraVisual.getY() + veldoraDepartureHeight / 2.0;
        }

        private void updateAbsorptionTransition(double deltaTime) {
            if (!absorptionTransitionActive || veldoraVisual == null) return;
            absorptionTransitionElapsed += Math.max(0, deltaTime);
            double t = Math.min(1.0,
                    absorptionTransitionElapsed / ABSORPTION_TRANSITION_SECONDS);
            double eased = 1.0 - Math.pow(1.0 - t, 3.0);
            double scale = 1.0 - 0.28 * eased;
            int width = Math.max(1, (int) Math.round(veldoraDepartureWidth * scale));
            int height = Math.max(1, (int) Math.round(veldoraDepartureHeight * scale));
            veldoraVisual.setWidth(width);
            veldoraVisual.setHeight(height);
            veldoraVisual.setX(veldoraDepartureCenterX - width / 2.0);
            veldoraVisual.setY(veldoraDepartureCenterY - height / 2.0
                    - ABSORPTION_RISE_PIXELS * eased);
            veldoraVisual.setOpacity(1.0 - t);
            if (barrierVisual != null) barrierVisual.setOpacity(1.0 - t);

            if (t >= 1.0) {
                destroyWorldVisual(veldoraVisual);
                destroyWorldVisual(barrierVisual);
                veldoraVisual = null;
                barrierVisual = null;
                absorptionTransitionActive = false;
            }
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
}
