import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.rimurusurvivors.domain.BattleActionResult;
import com.rimurusurvivors.domain.BattleCommand;
import com.rimurusurvivors.domain.BattleIntention;
import com.rimurusurvivors.domain.BattleOutcome;
import com.rimurusurvivors.domain.BattleSimulation;
import com.rimurusurvivors.domain.RandomSource;
import com.rimurusurvivors.domain.ReactionTiming;
import com.rimurusurvivors.domain.ReactionWindow;
import com.rimurusurvivors.domain.VillagePreparation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diretor do duelo por turnos reativo contra o Lider da Matilha (Floresta de Jura).
 *
 * <p>Adaptador FINO: toda regra de jogo vive em {@link BattleSimulation} e a
 * classificacao de tempo de reacao em {@link ReactionWindow}, ambos do jar de
 * dominio. Este script traduz snapshot -> HUD/VFX visual e input -> comando de
 * dominio, atraves de uma maquina explicita {@code COMMAND -> ACTION -> REACTION ->
 * COMMAND}: todo comando e calculado EXATAMENTE UMA VEZ (em {@link #beginAction}), o
 * resultado fica retido em {@link #pendingResult} e so e revelado ao fim do beat de
 * apresentacao (VFX/pose), quando {@link #resolveAction} decide o proximo modo. Input
 * de comando (1-6, SPACE de reacao) e ignorado fora de {@code COMMAND}/{@code
 * REACTION} porque {@link #tick} so despacha para o handler do modo atual — bloqueio
 * estrutural, nao uma checagem redundante.</p>
 */
public final class BattleDirector extends IgnisScript {

    private static final String SIGNAL_ENTER_DUEL = "TENSURA_ENTER_DIRE_WOLF_DUEL";
    private static final String SIGNAL_DUEL_COMPLETE = "TENSURA_DIRE_WOLF_DUEL_COMPLETE";

    private static final String BATTLE_DATA = "data/battle-dire-wolf-leader.json";
    private static final long DEFAULT_SEED = 20260721L;
    private static final Set<VillagePreparation> DEFAULT_PREPARATIONS = Set.of(
            VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK);

    // Entrada de QA/depuracao APENAS: o fluxo narrativo real dispara o duelo via
    // SIGNAL_ENTER_DUEL (GameFlowController -> BattleDirector). F10 nunca e
    // referenciado por outro script nem por save/progressao — pode ser removido do
    // build de producao sem quebrar nada; existe so para permitir QA determinista
    // (fita de input/click_ui) sem depender do fluxo narrativo completo.
    private static final String DEBUG_START_KEY = "F10";

    // Pausa minima de leitura de um beat de ACAO sem VFX proprio (ex.: Analisar) e
    // piso para nao cortar VFX curto demais antes do jogador conseguir ler o HUD.
    private static final double MIN_ACTION_SECONDS = 0.6;

    private static final String PORTRAIT_PATH = "assets/sprites/ui/battle/dire_wolf_leader_portrait_v1.png";
    private static final String BITE_TELEGRAPH_PATH = "assets/sprites/ui/battle/dire_wolf_telegraph_bite_v1.png";
    private static final String PACK_CALL_TELEGRAPH_PATH = "assets/sprites/ui/battle/dire_wolf_telegraph_pack_call_v1.png";
    private static final String CHARGE_TELEGRAPH_PATH = "assets/sprites/ui/battle/dire_wolf_telegraph_charge_v1.png";

    // Clipes de VFX/pose reutilizados dos assets ja entregues pelo Codex (nenhum
    // asset novo inventado). "hit"/"surrender" sao os MESMOS clipes que
    // DireWolfResolutionDirector usa na cutscene de resolucao — reuso semantico
    // deliberado, nao coincidencia de nome.
    private static final String CLIP_HYDROLAMINA = "battle_hydrolamina_v1";
    private static final String CLIP_GUARD_REACTION = "battle_guard_reaction_v1";
    private static final String CLIP_WOLF_STRIKE = "battle_dire_wolf_strike_v1";
    private static final String CLIP_LEADER_HIT = "dire_wolf_leader_reaction_hit_v1";
    private static final String CLIP_LEADER_SURRENDER = "dire_wolf_leader_reaction_surrender_v1";
    private static final String CLIP_LEADER_IDLE = "dire_wolf_leader_idle";

    private final Map<String, BattleCommand> keyToCommand = new LinkedHashMap<>();
    private final Map<String, String> commandKey = new LinkedHashMap<>();
    private final Map<String, String> commandLabel = new LinkedHashMap<>();
    private final Map<String, String> commandHint = new LinkedHashMap<>();
    private final Map<String, String> outcomeText = new LinkedHashMap<>();
    private final Map<String, String> gradeLabel = new LinkedHashMap<>();
    private final Map<String, SpriteAnimation> clipCache = new HashMap<>();

    private JSONObject config;
    private double telegraphSeconds = 1.1;
    private ReactionWindow reactionWindow = ReactionWindow.standard();

    private BattleSimulation battle;
    private Mode mode = Mode.INACTIVE;
    private double reactionElapsed;
    private String statusMessage = "";
    private boolean completionDelivered;

    // Estado do beat de ACAO em curso: o comando ja foi calculado (beginAction);
    // ACAO so espera o efeito visual terminar (ou o jogador pular) antes de revelar
    // o resultado guardado em pendingResult.
    private double actionElapsed;
    private BattleActionResult pendingResult;
    private String pendingReactionGrade;
    private final List<GameObject> activeVfxThisBeat = new ArrayList<>();
    private boolean leaderPoseChangedThisBeat;

    private boolean uiReady;
    private UIPanel shade;
    private UILabel titleLabel;
    private UIPanel statusPanel;
    private UILabel vitalsLabel;
    private UILabel intentionLabel;
    private UIPanel commandPanel;
    private UILabel commandsLabel;
    private UILabel messageLabel;
    private UILabel reactionLabel;

    private UIImage leaderPortraitImage;
    private UIImage biteTelegraphImage;
    private UIImage packCallTelegraphImage;
    private UIImage chargeTelegraphImage;
    private GameObject leaderActor;
    private GameObject hydrolaminaVfx;
    private GameObject guardReactionVfx;
    private GameObject wolfStrikeVfx;

    public void start() {
        config = readJson(BATTLE_DATA);
        parseConfig(config);
        preloadClips();
        onSceneSignal(SIGNAL_ENTER_DUEL, this::beginDuelFromSignal);
        log("BattleDirector: inicializado. " + DEBUG_START_KEY + " e entrada de QA/debug apenas.");
    }

    public void tick() {
        if (mode == Mode.INACTIVE) {
            // isKeyPressed (nivel), nao isKeyJustPressed: o loop do jogo continua
            // ticando em background mesmo com o mundo pausado (GameLoop.run() nao
            // depende de gameState), entao uma injecao MCP de borda pode ser
            // consumida por um tick de fundo antes do advance_frames controlado ler
            // o "just pressed". Nivel e imune a essa corrida e beginDuel() ja e
            // idempotente (guarda mode != INACTIVE), entao repetir a checagem em
            // varios frames com a tecla ainda "pressionada" e inofensivo.
            if (isKeyPressed(DEBUG_START_KEY)) {
                beginDuel(DEFAULT_PREPARATIONS);
            }
            return;
        }
        switch (mode) {
            case COMMAND -> tickCommand();
            case ACTION -> tickAction();
            case REACTION -> tickReaction();
            case RESULT -> tickResult();
            default -> { }
        }
        layoutUi();
        updateTelegraphs();
    }

    // ==================== Entrada do duelo ====================

    private void beginDuelFromSignal(Object payload) {
        beginDuel(preparationsFromPayload(payload));
    }

    private void beginDuel(Set<VillagePreparation> preparations) {
        if (mode != Mode.INACTIVE) return;
        battle = new BattleSimulation(preparations, RandomSource.seeded(DEFAULT_SEED));
        completionDelivered = false;
        reactionElapsed = 0;
        statusMessage = "Escolha um comando. Leia a intencao do lider antes de agir.";
        ensureActors();
        // Forca recriacao da UI a cada duelo: IgnisScript.clearUI() limpa o UICanvas
        // GLOBAL (compartilhado por todos os scripts, nao so o do chamador). No
        // retorno jogavel apos derrota, GameFlowController chama clearUI() antes de
        // permitir um novo duelo — sem este reset, uiReady continuaria true e
        // setupUi() pularia a criacao, deixando os campos apontando para widgets
        // orfaos (removidos do canvas, nunca mais renderizados).
        uiReady = false;
        setupUi();
        setUiVisible(true);
        mode = Mode.COMMAND;
        refreshHud();
        setCameraZoom(1.35);
        log("BattleDirector: duelo iniciado (semente=" + DEFAULT_SEED
                + ", preparacoes=" + preparations + ").");
    }

    private Set<VillagePreparation> preparationsFromPayload(Object payload) {
        if (!(payload instanceof String[] names) || names.length != 2) {
            return DEFAULT_PREPARATIONS;
        }
        Set<VillagePreparation> chosen = new LinkedHashSet<>();
        for (String name : names) {
            try {
                chosen.add(VillagePreparation.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                return DEFAULT_PREPARATIONS;
            }
        }
        return chosen.size() == 2 ? chosen : DEFAULT_PREPARATIONS;
    }

    // ==================== COMMAND: escolha do jogador ====================

    private void tickCommand() {
        for (Map.Entry<String, BattleCommand> entry : keyToCommand.entrySet()) {
            if (isKeyJustPressed(entry.getKey())) {
                chooseCommand(entry.getValue());
                return;
            }
        }
    }

    private void chooseCommand(BattleCommand command) {
        if (command == BattleCommand.DEFEND) {
            reactionElapsed = 0;
            mode = Mode.REACTION;
            statusMessage = config.getJSONObject("reaction").getString("telegraph");
            refreshHud();
            return;
        }
        beginAction(command, ReactionTiming.NONE, null);
    }

    // ==================== REACTION: janela de defesa ====================

    private void tickReaction() {
        reactionElapsed += getDeltaTime();
        if (isKeyJustPressed("SPACE")) {
            double offset = reactionElapsed - telegraphSeconds;
            ReactionTiming timing = reactionWindow.classify(offset);
            beginAction(BattleCommand.DEFEND, timing, timing.name());
            return;
        }
        if (reactionElapsed > telegraphSeconds + reactionWindow.activeRadius()) {
            beginAction(BattleCommand.DEFEND, ReactionTiming.NONE, ReactionTiming.NONE.name());
        }
    }

    // ==================== ACTION: calculo unico + apresentacao ====================

    /**
     * Calcula o comando no dominio EXATAMENTE UMA VEZ e decide a apresentacao.
     * Comando invalido/recusado (ex.: magicules insuficientes) nao gasta turno e nao
     * tem efeito para apresentar: volta direto a COMMAND sem passar por ACTION.
     */
    private void beginAction(BattleCommand command, ReactionTiming timing, String reactionGrade) {
        BattleIntention priorIntention = battle.currentIntention();
        BattleActionResult result = battle.executeCommand(command, timing);

        if (!result.valid()) {
            statusMessage = result.message();
            refreshHud();
            mode = Mode.COMMAND;
            return;
        }

        pendingResult = result;
        pendingReactionGrade = reactionGrade;
        actionElapsed = 0;
        mode = Mode.ACTION;
        statusMessage = commandLabel.getOrDefault(command.name(), command.commandName()) + "...";

        presentAction(command, priorIntention);
        refreshHud();
    }

    /** Avanca o beat de ACAO: espera o VFX/pose terminar (ou skip) antes de revelar. */
    private void tickAction() {
        actionElapsed += getDeltaTime();
        boolean skip = isKeyJustPressed("ENTER") || isKeyJustPressed("SPACE");
        boolean animationsFinished = activeVfxThisBeat.stream()
                .allMatch(go -> !go.getOrCreateAnimator().isPlaying());
        if (skip || (animationsFinished && actionElapsed >= MIN_ACTION_SECONDS)) {
            resolveAction();
        }
    }

    /** Fim do beat: esconde VFX, restaura a pose do lider e revela o resultado retido. */
    private void resolveAction() {
        hideVfx(hydrolaminaVfx);
        hideVfx(guardReactionVfx);
        hideVfx(wolfStrikeVfx);
        if (leaderPoseChangedThisBeat) {
            playClip(leaderActor, CLIP_LEADER_IDLE);
            leaderPoseChangedThisBeat = false;
        }
        activeVfxThisBeat.clear();

        if (pendingResult == null) {
            mode = Mode.COMMAND; // guarda defensiva; beginAction sempre preenche antes de entrar em ACTION
            return;
        }

        StringBuilder message = new StringBuilder(pendingResult.message());
        if (pendingReactionGrade != null) {
            message.append("  (Reacao: ")
                    .append(gradeLabel.getOrDefault(pendingReactionGrade, pendingReactionGrade))
                    .append(')');
        }
        statusMessage = message.toString();
        pendingResult = null;
        pendingReactionGrade = null;

        // mode PRECISA mudar antes de refreshHud(): o override de outcomeText/
        // "[E] encerrar" dentro de refreshHud() checa mode == RESULT, entao chamar
        // na ordem errada mostraria a mensagem crua do turno em vez do desfecho.
        mode = (battle.outcome() != BattleOutcome.IN_PROGRESS) ? Mode.RESULT : Mode.COMMAND;
        refreshHud();
    }

    /**
     * Decide e dispara o VFX/pose de cada comando. {@code priorIntention} e a
     * intencao do lider ANTES de {@code executeCommand} (que ja a substitui pela do
     * proximo turno) — e o unico jeito de saber se o contra-ataque deste turno e
     * real, usando a MESMA condicao que o dominio usa para aplica-lo (secao 4 de
     * {@code BattleSimulation.executeCommand}), em vez de inferir por delta de HP.
     */
    private void presentAction(BattleCommand command, BattleIntention priorIntention) {
        boolean counterIncoming = priorIntention != null
                && priorIntention.requiresReaction()
                && priorIntention.baseDamage() > 0;

        switch (command) {
            case WATER_BLADE -> {
                playVfx(hydrolaminaVfx, CLIP_HYDROLAMINA);
                trackVfx(hydrolaminaVfx);
                if (playClip(leaderActor, CLIP_LEADER_HIT)) {
                    leaderPoseChangedThisBeat = true;
                    trackVfx(leaderActor);
                }
                if (counterIncoming) {
                    playVfx(wolfStrikeVfx, CLIP_WOLF_STRIKE);
                    trackVfx(wolfStrikeVfx);
                }
            }
            case GOBLIN_SUPPORT -> {
                if (playClip(leaderActor, CLIP_LEADER_HIT)) {
                    leaderPoseChangedThisBeat = true;
                    trackVfx(leaderActor);
                }
                if (counterIncoming) {
                    playVfx(wolfStrikeVfx, CLIP_WOLF_STRIKE);
                    trackVfx(wolfStrikeVfx);
                }
            }
            case DEFEND -> {
                playVfx(guardReactionVfx, CLIP_GUARD_REACTION);
                trackVfx(guardReactionVfx);
                if (counterIncoming) {
                    playVfx(wolfStrikeVfx, CLIP_WOLF_STRIKE);
                    trackVfx(wolfStrikeVfx);
                }
            }
            case PREDATOR -> {
                if (playClip(leaderActor, CLIP_LEADER_HIT)) {
                    leaderPoseChangedThisBeat = true;
                    trackVfx(leaderActor);
                }
            }
            case NEGOTIATE -> {
                if (playClip(leaderActor, CLIP_LEADER_SURRENDER)) {
                    leaderPoseChangedThisBeat = true;
                    trackVfx(leaderActor);
                }
            }
            case ANALYZE -> {
                // Sem VFX: nada acontece com o lider. O beat vira so a pausa legivel
                // de MIN_ACTION_SECONDS antes de revelar a analise no HUD.
            }
        }
    }

    private void trackVfx(GameObject go) {
        if (go != null) activeVfxThisBeat.add(go);
    }

    private void playVfx(GameObject vfx, String clipName) {
        if (vfx == null) return;
        if (!playClip(vfx, clipName)) return;
        vfx.setVisible(true);
        vfx.setOpacity(1);
    }

    private void hideVfx(GameObject vfx) {
        if (vfx != null) vfx.setVisible(false);
    }

    /** Toca {@code clipName} no Animator de {@code actor}. @return false se ausente. */
    private boolean playClip(GameObject actor, String clipName) {
        SpriteAnimation anim = clipCache.get(clipName);
        if (actor == null || anim == null) return false;
        Animator animator = actor.getOrCreateAnimator();
        animator.addAnimation(anim);
        animator.play(anim.getName());
        return true;
    }

    // ==================== RESULT: fim do duelo ====================

    private void tickResult() {
        if (isKeyJustPressed("E") || isKeyJustPressed("ENTER")) {
            finishDuel();
        }
    }

    private void finishDuel() {
        if (completionDelivered) return;
        completionDelivered = true;
        BattleOutcome outcome = battle.outcome();
        setUiVisible(false);
        hideAllTelegraphs();
        hideVfx(hydrolaminaVfx);
        hideVfx(guardReactionVfx);
        hideVfx(wolfStrikeVfx);
        if (leaderActor != null) {
            leaderActor.setVisible(false);
        }
        setCameraZoom(1.6);
        mode = Mode.INACTIVE;
        sceneDispatcher.enqueue(SIGNAL_DUEL_COMPLETE, outcome.name());
        log("BattleDirector: duelo encerrado com desfecho " + outcome.name()
                + " no turno " + battle.turnCount() + ".");
    }

    // ==================== HUD (apresentacao do snapshot) ====================

    private void refreshHud() {
        BattleIntention intention = battle.currentIntention();
        vitalsLabel.setText(
                "RIMURU   HP " + bar(battle.rimuruHp(), BattleSimulation.RIMURU_MAX_HP)
                        + "   Magiaules " + battle.rimuruMagicules() + "/" + BattleSimulation.RIMURU_MAX_MAGICULES
                        + "\nLIDER    HP " + bar(battle.leaderHp(), battle.leaderMaxHp())
                        + "   Moral " + bar(battle.leaderMorale(), BattleSimulation.LEADER_MAX_MORALE)
                        + "\nTurno " + battle.turnCount() + "   Fase: " + battle.leaderPhase().title());

        String telegraph = intention.requiresReaction() ? "  [!] investida telegrafada" : "";
        String reveal = battle.analyzed()
                ? "\nGrande Sabio: " + String.join(" | ", sortedInfo())
                : "\n(Use Analisar para revelar a intencao e a condicao de rendicao.)";
        intentionLabel.setText("INTENCAO DO LIDER: " + intention.actionName() + telegraph
                + "\n" + intention.telegraphHint() + reveal);

        commandsLabel.setText(buildCommandMenu());
        messageLabel.setText(statusMessage);

        boolean reacting = mode == Mode.REACTION;
        reactionLabel.setVisible(reacting);
        if (reacting) {
            reactionLabel.setText(config.getJSONObject("reaction").getString("prompt"));
        }
        if (mode == Mode.RESULT) {
            messageLabel.setText(outcomeText.getOrDefault(battle.outcome().name(), battle.outcome().description())
                    + "\n[E] encerrar");
        }
    }

    private String buildCommandMenu() {
        StringBuilder menu = new StringBuilder("COMANDOS:");
        for (Map.Entry<String, BattleCommand> entry : keyToCommand.entrySet()) {
            BattleCommand command = entry.getValue();
            String name = command.name();
            String enabled = commandEnabled(command) ? "" : "  (indisponivel)";
            menu.append("\n[").append(entry.getKey()).append("] ")
                    .append(commandLabel.getOrDefault(name, command.commandName()))
                    .append(enabled);
        }
        return menu.toString();
    }

    private boolean commandEnabled(BattleCommand command) {
        return switch (command) {
            case WATER_BLADE -> battle.rimuruMagicules() >= BattleSimulation.WATER_BLADE_COST;
            case PREDATOR -> battle.isPredatorAvailable();
            case NEGOTIATE -> battle.isNegotiationAvailable();
            case ANALYZE, DEFEND, GOBLIN_SUPPORT -> true;
        };
    }

    private List<String> sortedInfo() {
        List<String> info = new ArrayList<>(battle.revealedInformation());
        info.sort(String::compareTo);
        return info;
    }

    private String bar(int value, int max) {
        int slots = 10;
        int filled = max <= 0 ? 0 : Math.max(0, Math.min(slots, (int) Math.round((double) value / max * slots)));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            builder.append(i < filled ? '#' : '.');
        }
        return builder + " " + value + "/" + max;
    }

    // ==================== Construcao / layout de UI ====================

    private void setupUi() {
        if (uiReady) return;
        uiReady = true;
        shade = createPanel(0, 0, 960, 540);
        setUIColors(shade, new Color(6, 10, 14, 224), null, null);

        titleLabel = createLabel(config.getString("title"), 0, 20, 900, 30);
        titleLabel.setAlignment(UILabel.Alignment.CENTER);
        titleLabel.setFont("SansSerif", Font.BOLD, 22);
        titleLabel.setTextColor(new Color(214, 236, 255));
        titleLabel.setOutline(true, new Color(8, 20, 30), 2);

        statusPanel = createPanel(0, 0, 560, 150);
        setUIColors(statusPanel, new Color(10, 18, 26, 236), null, new Color(96, 150, 190));
        vitalsLabel = createLabel("", 0, 0, 520, 78);
        vitalsLabel.setFont("Monospaced", Font.BOLD, 14);
        vitalsLabel.setTextColor(new Color(224, 240, 246));
        vitalsLabel.setMultiline(true);
        intentionLabel = createLabel("", 0, 0, 520, 120);
        intentionLabel.setFont("SansSerif", Font.PLAIN, 13);
        intentionLabel.setTextColor(new Color(255, 224, 168));
        intentionLabel.setMultiline(true);

        commandPanel = createPanel(0, 0, 360, 220);
        setUIColors(commandPanel, new Color(10, 18, 26, 236), null, new Color(120, 190, 140));
        commandsLabel = createLabel("", 0, 0, 330, 210);
        commandsLabel.setFont("Monospaced", Font.PLAIN, 14);
        commandsLabel.setTextColor(new Color(220, 244, 226));
        commandsLabel.setMultiline(true);

        messageLabel = createLabel("", 0, 0, 900, 54);
        messageLabel.setAlignment(UILabel.Alignment.CENTER);
        messageLabel.setFont("SansSerif", Font.BOLD, 15);
        messageLabel.setTextColor(new Color(236, 246, 250));
        messageLabel.setMultiline(true);

        reactionLabel = createLabel("", 0, 0, 400, 40);
        reactionLabel.setAlignment(UILabel.Alignment.CENTER);
        reactionLabel.setFont("SansSerif", Font.BOLD, 26);
        reactionLabel.setTextColor(new Color(255, 210, 120));
        reactionLabel.setOutline(true, new Color(40, 16, 6), 2);
        reactionLabel.setVisible(false);

        leaderPortraitImage = createImage(PORTRAIT_PATH, 30, 75, 72, 72);
        biteTelegraphImage = createImage(BITE_TELEGRAPH_PATH, 420, 160, 128, 128);
        packCallTelegraphImage = createImage(PACK_CALL_TELEGRAPH_PATH, 420, 160, 128, 128);
        chargeTelegraphImage = createImage(CHARGE_TELEGRAPH_PATH, 420, 160, 128, 128);
        hideAllTelegraphs();

        layoutUi();
    }

    /**
     * Resolve os atores/VFX unicos da cena e os posiciona relativos ao jogador, para
     * que fiquem dentro da safe area independente de onde o duelo comecar (mesma
     * logica ja validada na tarefa #11 para o lider; aqui estendida aos 3 VFX).
     */
    private void ensureActors() {
        if (leaderActor == null) {
            leaderActor = findObject("DireWolfLeader");
        }
        if (hydrolaminaVfx == null) {
            hydrolaminaVfx = findObject("BattleHydrolaminaVfx");
        }
        if (guardReactionVfx == null) {
            guardReactionVfx = findObject("BattleGuardReactionVfx");
        }
        if (wolfStrikeVfx == null) {
            wolfStrikeVfx = findObject("BattleWolfStrikeVfx");
        }

        GameObject player = findObject("Rimuru");
        if (leaderActor != null) {
            leaderActor.setVisible(true);
            leaderActor.setOpacity(1);
            if (player != null) {
                double px = player.getX();
                double py = player.getY();
                // Posiciona o Lider dos Lobos dentro da safe area visual (160px a direita de Rimuru)
                leaderActor.setX(px + 160);
                leaderActor.setY(py - 10);
                // Centraliza a camera no meio da arena entre Rimuru e o Lider
                setCameraPosition(px + 80, py - 10);
            }
        }
        // Hidrolamina e o ataque de Rimuru: centraliza no lider, alvo do golpe.
        centerOn(hydrolaminaVfx, leaderActor);
        // Guarda e investida acontecem em Rimuru: guarda bloqueia, investida atinge.
        centerOn(wolfStrikeVfx, player);
        centerOn(guardReactionVfx, player);
    }

    private void centerOn(GameObject vfx, GameObject anchor) {
        if (vfx == null || anchor == null) return;
        double cx = anchor.getX() + anchor.getWidth() / 2.0;
        double cy = anchor.getY() + anchor.getHeight() / 2.0;
        vfx.setX(cx - vfx.getWidth() / 2.0);
        vfx.setY(cy - vfx.getHeight() / 2.0);
    }

    private void updateTelegraphs() {
        if (mode != Mode.INACTIVE && mode != Mode.RESULT && mode != Mode.ACTION && battle != null) {
            BattleIntention intention = battle.currentIntention();
            boolean isReacting = mode == Mode.REACTION || intention.requiresReaction();
            if (isReacting) {
                String actionName = intention.actionName();
                boolean isBite = actionName.contains("Mordida") || actionName.contains("Probing") || actionName.contains("Sondagem");
                boolean isPackCall = actionName.contains("Chamado") || actionName.contains("Pack") || actionName.contains("Pressao");

                if (biteTelegraphImage != null) biteTelegraphImage.setVisible(isBite);
                if (packCallTelegraphImage != null) packCallTelegraphImage.setVisible(isPackCall);
                if (chargeTelegraphImage != null) chargeTelegraphImage.setVisible(!isBite && !isPackCall);
                return;
            }
        }
        hideAllTelegraphs();
    }

    private void hideAllTelegraphs() {
        if (biteTelegraphImage != null) biteTelegraphImage.setVisible(false);
        if (packCallTelegraphImage != null) packCallTelegraphImage.setVisible(false);
        if (chargeTelegraphImage != null) chargeTelegraphImage.setVisible(false);
    }

    private void layoutUi() {
        if (!uiReady) return;
        double width = Math.max(640, getGame().getWidth());
        double height = Math.max(400, getGame().getHeight());
        shade.setSize(width, height);
        titleLabel.setSize(Math.min(900, width - 40), 30);
        titleLabel.setPosition((width - titleLabel.getWidth()) / 2.0, 18);

        double margin = 24;
        statusPanel.setPosition(margin, 64);
        vitalsLabel.setPosition(margin + 18, 78);
        intentionLabel.setPosition(margin + 18, 150);

        double commandX = width - 384;
        commandPanel.setPosition(commandX, 64);
        commandsLabel.setPosition(commandX + 16, 78);

        messageLabel.setSize(Math.min(900, width - 40), 54);
        messageLabel.setPosition((width - messageLabel.getWidth()) / 2.0, height - 70);
        reactionLabel.setPosition((width - 400) / 2.0, height / 2.0 - 20);

        if (leaderPortraitImage != null) {
            leaderPortraitImage.setPosition(margin + 16, 72);
        }
        double telegraphX = (width - 128) / 2.0;
        double telegraphY = 56;
        if (biteTelegraphImage != null) biteTelegraphImage.setPosition(telegraphX, telegraphY);
        if (packCallTelegraphImage != null) packCallTelegraphImage.setPosition(telegraphX, telegraphY);
        if (chargeTelegraphImage != null) chargeTelegraphImage.setPosition(telegraphX, telegraphY);
    }

    private void setUiVisible(boolean visible) {
        if (!uiReady) return;
        shade.setVisible(visible);
        titleLabel.setVisible(visible);
        statusPanel.setVisible(visible);
        vitalsLabel.setVisible(visible);
        intentionLabel.setVisible(visible);
        commandPanel.setVisible(visible);
        commandsLabel.setVisible(visible);
        messageLabel.setVisible(visible);
        if (leaderPortraitImage != null) {
            leaderPortraitImage.setVisible(visible);
        }
        if (!visible) {
            reactionLabel.setVisible(false);
            hideAllTelegraphs();
        }
    }

    // ==================== Dados ====================

    private void parseConfig(JSONObject data) {
        telegraphSeconds = data.optDouble("reactionTelegraphSeconds", 1.1);
        reactionWindow = "story".equalsIgnoreCase(data.optString("timingMode", "strategic"))
                ? ReactionWindow.storyMode() : ReactionWindow.standard();

        JSONArray commands = data.getJSONArray("commands");
        for (int i = 0; i < commands.length(); i++) {
            JSONObject entry = commands.getJSONObject(i);
            String key = entry.getString("key");
            BattleCommand command = BattleCommand.valueOf(entry.getString("command"));
            keyToCommand.put(key, command);
            commandKey.put(command.name(), key);
            commandLabel.put(command.name(), entry.optString("label", command.commandName()));
            commandHint.put(command.name(), entry.optString("hint", command.description()));
        }
        JSONObject outcomes = data.getJSONObject("outcomes");
        for (String key : outcomes.keySet()) {
            outcomeText.put(key, outcomes.getString(key));
        }
        JSONObject grades = data.getJSONObject("reaction").getJSONObject("gradeLabels");
        for (String key : grades.keySet()) {
            gradeLabel.put(key, grades.getString(key));
        }
    }

    private void preloadClips() {
        preloadClip(CLIP_HYDROLAMINA);
        preloadClip(CLIP_GUARD_REACTION);
        preloadClip(CLIP_WOLF_STRIKE);
        preloadClip(CLIP_LEADER_HIT);
        preloadClip(CLIP_LEADER_SURRENDER);
        preloadClip(CLIP_LEADER_IDLE);
    }

    private void preloadClip(String clipName) {
        File file = AssetResolver.resolve("assets/animations/" + clipName + ".anim.json");
        if (file == null || !file.exists()) return;
        try {
            clipCache.put(clipName, AnimationIO.load(file));
        } catch (Exception ignored) { }
    }

    private JSONObject readJson(String relativePath) {
        File file = AssetResolver.resolve(relativePath);
        try {
            return new JSONObject(Files.readString(file.toPath()));
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar " + relativePath, exception);
        }
    }

    private enum Mode {
        INACTIVE,
        COMMAND,
        ACTION,
        REACTION,
        RESULT
    }
}
