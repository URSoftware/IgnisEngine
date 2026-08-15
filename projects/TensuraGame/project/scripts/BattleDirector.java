import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.CanvasComponent;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UICanvas;
import com.ignis.core.ui.UIComponent;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.ignis.core.ui.UIProgressBar;
import com.rimurusurvivors.domain.BattleActionResult;
import com.rimurusurvivors.domain.BattleCommand;
import com.rimurusurvivors.domain.BattleIntention;
import com.rimurusurvivors.domain.BattleOutcome;
import com.rimurusurvivors.domain.BattleSimulation;
import com.rimurusurvivors.domain.LeaderPhase;
import com.rimurusurvivors.domain.RandomSource;
import com.rimurusurvivors.domain.ReactionTiming;
import com.rimurusurvivors.domain.ReactionWindow;
import com.rimurusurvivors.domain.VillagePreparation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
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
 * dominio.</p>
 *
 * <p>A maquina {@code COMMAND -> ACTION -> REACTION -> COMMAND} mora em
 * {@link DuelFlow}, uma classe aninhada PURA: ela e a unica autoridade sobre qual
 * input e aceito em cada instante, entao "um input produz exatamente um calculo" e
 * uma propriedade testavel fora do editor, nao um efeito colateral do {@code tick}.
 * Todo comando e calculado EXATAMENTE UMA VEZ (em {@link #beginAction}), o resultado
 * fica retido em {@link #pendingResult} e so e revelado ao fim do beat de
 * apresentacao, quando {@link #resolveAction} decide o proximo modo.</p>
 *
 * <p>A apresentacao esta separada em {@link BattleHud}, com exatamente duas
 * implementacoes escolhidas UMA VEZ por entrada no duelo: {@link PersistentBattleHud}
 * consome o {@code BattleHudCanvas} da cena e {@link VolatileBattleHud} e o fallback
 * transitorio enquanto o Codex nao sincroniza esse canvas. Canvas presente porem
 * incompleto e erro de integracao e falha com diagnostico — nunca recai em silencio no
 * fallback. Os dois consomem o MESMO {@link HudSnapshot} e os MESMOS sinais; nenhum
 * calculo de combate mora no HUD.</p>
 */
public final class BattleDirector extends IgnisScript {

    private static final String SIGNAL_ENTER_DUEL = "TENSURA_ENTER_DIRE_WOLF_DUEL";
    private static final String SIGNAL_DUEL_COMPLETE = "TENSURA_DIRE_WOLF_DUEL_COMPLETE";
    private static final String COMMAND_SIGNAL_PREFIX = "TENSURA_BATTLE_COMMAND_";
    private static final String DEFAULT_RETRY_SIGNAL = "TENSURA_BATTLE_RETRY";
    private static final String ACTION_DATA_PREFIX = "signal:";

    private static final String BATTLE_DATA = "data/battle-dire-wolf-leader.json";
    private static final String VISUAL_DATA = "data/battle-dire-wolf-leader-visuals.json";
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
    private final Map<String, BattleCommand> signalToCommand = new LinkedHashMap<>();
    private final Map<String, String> commandLabel = new LinkedHashMap<>();
    private final Map<String, String> commandHint = new LinkedHashMap<>();
    private final Map<String, String> outcomeText = new LinkedHashMap<>();
    private final Map<String, GradeFeedback> gradeFeedback = new LinkedHashMap<>();
    private final Map<String, SpriteAnimation> clipCache = new HashMap<>();

    private JSONObject config;
    private JSONObject visuals;
    private double telegraphSeconds = 1.1;
    private ReactionWindow reactionWindow = ReactionWindow.standard();
    private String timingModeLabel = "";

    private BattleSimulation battle;
    private final DuelFlow flow = new DuelFlow();
    private double reactionElapsed;
    private String statusMessage = "";
    private boolean completionDelivered;

    // Estado do beat de ACAO em curso: o comando ja foi calculado (beginAction);
    // ACAO so espera o efeito visual terminar (ou o jogador pular) antes de revelar
    // o resultado guardado em pendingResult.
    private double actionElapsed;
    private final Map<String, Double> actionSeconds = new LinkedHashMap<>();
    private double minimumActionSeconds = MIN_ACTION_SECONDS;
    private BattleCommand pendingCommand;
    private BattleActionResult pendingResult;
    private String pendingReactionGrade;
    private final List<GameObject> activeVfxThisBeat = new ArrayList<>();
    private boolean leaderPoseChangedThisBeat;

    // Feedback do grau de reacao: sobrevive ao beat de ACAO para o jogador ler
    // palavra, cor, forma e movimento antes de voltar a escolher.
    private GradeFeedback shownGrade;
    private double gradeElapsed;

    private BattleHud hud;
    private String retrySignalName = DEFAULT_RETRY_SIGNAL;
    // Estado autorado pelo Codex, fotografado ANTES da primeira mutacao do presenter e
    // reusado em todo recalculo. Os widgets do BattleHudCanvas sao PERSISTIDOS: o que o
    // presenter escreve neles o autosave grava no .ignis. Por isso o baseline e unico
    // por sessao e o presenter e obrigado a devolve-lo antes de ocultar ou encerrar.
    private final Map<String, WidgetState> authoredState = new LinkedHashMap<>();

    private GameObject leaderActor;
    private GameObject hydrolaminaVfx;
    private GameObject guardReactionVfx;
    private GameObject wolfStrikeVfx;

    public void start() {
        config = readJson(BATTLE_DATA);
        visuals = readOptionalJson(VISUAL_DATA);
        parseConfig(config);
        preloadClips();
        onSceneSignal(SIGNAL_ENTER_DUEL, this::beginDuelFromSignal);
        for (Map.Entry<String, BattleCommand> entry : signalToCommand.entrySet()) {
            BattleCommand command = entry.getValue();
            onSceneSignal(entry.getKey(), payload -> requestCommand(command));
        }
        // Registrado UMA vez: se o bind do canvas registrasse por duelo, cada nova
        // entrada empilharia um ouvinte e um clique viraria varias chamadas.
        retrySignalName = resolveRetrySignalName();
        onSceneSignal(retrySignalName, payload -> requestRetry());
        log("BattleDirector: inicializado com " + signalToCommand.size() + " sinais de comando e "
                + retrySignalName + ". " + DEBUG_START_KEY + " e entrada de QA/debug apenas.");
    }

    /**
     * Stop no meio do duelo nao passa por {@code finishDuel}. Sem este gancho, os
     * widgets persistidos ficariam com o layout do combate e o autosave seguinte
     * gravaria esse estado no projeto do usuario.
     */
    @Override
    public void onDetach() {
        if (hud != null) {
            hud.hide();
        }
        super.onDetach();
    }

    public void tick() {
        if (flow.isIdle()) {
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
        // O gate — nao um switch paralelo — decide o que este frame aceita. Um unico
        // lugar responde "que input vale agora", e e o mesmo lugar que os testes
        // exercitam.
        if (flow.acceptsCommand()) {
            tickCommand();
        } else if (flow.acceptsReaction()) {
            tickReaction();
        } else if (flow.acceptsSkip()) {
            tickAction();
        } else if (flow.acceptsFinish()) {
            tickResult();
        }
        if (hud != null) {
            gradeElapsed += getDeltaTime();
            hud.animate(getDeltaTime());
        }
    }

    // ==================== Entrada do duelo ====================

    private void beginDuelFromSignal(Object payload) {
        beginDuel(preparationsFromPayload(payload));
    }

    private void beginDuel(Set<VillagePreparation> preparations) {
        if (!flow.isIdle()) return;
        battle = new BattleSimulation(preparations, RandomSource.seeded(DEFAULT_SEED));
        completionDelivered = false;
        reactionElapsed = 0;
        shownGrade = null;
        gradeElapsed = 0;
        statusMessage = "Escolha um comando. Leia a intencao do lider antes de agir.";
        ensureActors();
        if (!mountHud()) {
            return;
        }
        flow.beginDuel();
        hud.show();
        refreshHud();
        setCameraZoom(1.35);
        log("BattleDirector: duelo iniciado (semente=" + DEFAULT_SEED
                + ", preparacoes=" + preparations + ", hud=" + hud.kind()
                + ", timing=" + timingModeLabel + ").");
    }

    /**
     * Escolhe EXATAMENTE UM presenter por entrada no duelo. Canvas ausente e o caso
     * transitorio esperado (o Codex ainda nao sincronizou {@code BattleHudCanvas});
     * canvas presente porem incompleto e erro de integracao e derruba o duelo com
     * diagnostico, porque cair no fallback ali esconderia o defeito ate o QA visual.
     */
    private boolean mountHud() {
        if (hud != null) {
            hud.dispose();
            hud = null;
        }
        // clearUI() limpa o UICanvas GLOBAL (compartilhado por todos os scripts). No
        // retorno jogavel apos derrota, GameFlowController o chama antes de permitir um
        // novo duelo: por isso a UI volatil e sempre reconstruida, nunca reaproveitada.
        JSONObject canvasContract = visuals == null ? null : visuals.optJSONObject("canvas");
        String canvasName = canvasContract == null
                ? null
                : canvasContract.optString("objectName", null);
        GameObject canvasObject = canvasName == null ? null : findObject(canvasName);
        if (canvasObject != null) {
            PersistentBattleHud persistent = new PersistentBattleHud(canvasObject);
            String failure = persistent.bind();
            if (failure != null) {
                log("BattleDirector: ERRO de integracao no " + canvasName + " — " + failure
                        + ". Nao vou cair no HUD volatil e esconder o defeito.");
                return false;
            }
            hud = persistent;
        } else {
            hud = new VolatileBattleHud();
            ((VolatileBattleHud) hud).build();
        }
        log("BattleDirector: presenter selecionado = " + hud.kind() + ".");
        return true;
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
                requestCommand(entry.getValue());
                return;
            }
        }
    }

    /**
     * Porta unica de entrada de comando: teclado e clique do HUD (via sinal
     * {@code TENSURA_BATTLE_COMMAND_*}) chegam aqui. Fora de {@code COMMAND} o pedido
     * e descartado ANTES de tocar o dominio — e isso que garante que input durante
     * ACTION/REACTION nao produza um segundo calculo, mesmo que o sinal tenha sido
     * enfileirado antes da troca de modo.
     */
    private void requestCommand(BattleCommand command) {
        if (command == null || battle == null) return;
        if (!flow.acceptsCommand()) {
            log("BattleDirector: comando " + command.name() + " ignorado em "
                    + flow.mode() + " (janela de escolha fechada).");
            return;
        }
        if (!commandEnabled(command)) {
            statusMessage = commandHint.getOrDefault(command.name(), command.description());
            refreshHud();
            return;
        }
        chooseCommand(command);
    }

    private void chooseCommand(BattleCommand command) {
        if (command == BattleCommand.DEFEND) {
            reactionElapsed = 0;
            flow.awaitReaction();
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
            return;
        }
        refreshHud();
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
            flow.backToCommand();
            refreshHud();
            return;
        }

        pendingResult = result;
        pendingCommand = command;
        pendingReactionGrade = reactionGrade;
        actionElapsed = 0;
        flow.beginAction();
        statusMessage = commandLabel.getOrDefault(command.name(), command.commandName()) + "...";
        // O selo do grau pertence ao beat da reacao: some quando o proximo comando
        // comeca, em vez de ficar pendurado no HUD por turnos que nao o produziram.
        shownGrade = reactionGrade == null ? null : gradeFeedback.get(reactionGrade);
        gradeElapsed = 0;

        presentAction(command, priorIntention);
        refreshHud();
    }

    /** Avanca o beat de ACAO: espera o VFX/pose terminar (ou skip) antes de revelar. */
    private void tickAction() {
        actionElapsed += getDeltaTime();
        boolean skip = isKeyJustPressed("ENTER") || isKeyJustPressed("SPACE");
        boolean animationsFinished = activeVfxThisBeat.stream()
                .allMatch(go -> !go.getOrCreateAnimator().isPlaying());
        if (skip || (animationsFinished && actionElapsed >= actionSecondsFor(pendingCommand))) {
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
            flow.backToCommand(); // guarda defensiva; beginAction sempre preenche antes de entrar em ACTION
            return;
        }

        StringBuilder message = new StringBuilder(pendingResult.message());
        if (pendingReactionGrade != null) {
            GradeFeedback feedback = gradeFeedback.get(pendingReactionGrade);
            message.append("  (Reacao: ")
                    .append(feedback == null ? pendingReactionGrade : feedback.label())
                    .append(')');
        }
        statusMessage = message.toString();
        pendingResult = null;
        pendingCommand = null;
        pendingReactionGrade = null;

        // O modo PRECISA mudar antes de refreshHud(): o override de outcomeText/
        // "[E] encerrar" dentro do snapshot checa RESULT, entao chamar na ordem errada
        // mostraria a mensagem crua do turno em vez do desfecho.
        flow.settle(battle.outcome() != BattleOutcome.IN_PROGRESS);
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

    /**
     * Tres modos de timing declarados nos dados, todos construidos a partir do que o
     * dominio ja oferece: escala 0 cai em storyMode(), escala 1 em standard() e
     * qualquer outro valor em scaled(). Nenhuma faixa nova e inventada no script.
     */
    private ReactionWindow resolveTimingMode(JSONObject data) {
        String mode = data.optString("timingMode", "strategic");
        JSONObject modes = data.optJSONObject("timingModes");
        if (modes == null || !modes.has(mode)) {
            timingModeLabel = mode;
            return "story".equalsIgnoreCase(mode)
                    ? ReactionWindow.storyMode()
                    : ReactionWindow.standard();
        }
        JSONObject entry = modes.getJSONObject(mode);
        timingModeLabel = entry.optString("label", mode);
        double scale = entry.optDouble("scale", 1.0);
        if (scale <= 0) {
            return ReactionWindow.storyMode();
        }
        return scale == 1.0 ? ReactionWindow.standard() : ReactionWindow.standard().scaled(scale);
    }

    /** Duracao de apresentacao por comando; o minimo antigo vira piso declarado. */
    private void parseActionSeconds(JSONObject data) {
        actionSeconds.clear();
        JSONObject durations = data.optJSONObject("actionSeconds");
        if (durations == null) {
            minimumActionSeconds = MIN_ACTION_SECONDS;
            return;
        }
        // O piso legado nunca desce: uma configuracao futura pode alongar um beat, mas
        // nao pode encurtar a leitura abaixo dos 0,6 s ja aceitos no golden path.
        minimumActionSeconds = Math.max(
                MIN_ACTION_SECONDS, requireSeconds(durations, "minimum", MIN_ACTION_SECONDS));
        for (String key : durations.keySet()) {
            if ("minimum".equals(key)) {
                continue;
            }
            double authored = requireSeconds(durations, key, minimumActionSeconds);
            actionSeconds.put(key, Math.max(minimumActionSeconds, authored));
        }
    }

    /**
     * Recusa negativo, NaN e infinito nomeando a chave. Um valor invalido passando
     * silenciosamente viraria beat instantaneo ou eterno, e o duelo so falharia em
     * runtime, longe da causa.
     */
    private double requireSeconds(JSONObject durations, String key, double fallback) {
        if (!durations.has(key)) {
            return fallback;
        }
        double value = durations.optDouble(key, Double.NaN);
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalStateException("Duracao de acao invalida em " + BATTLE_DATA
                    + ": " + key + " = " + durations.get(key));
        }
        return value;
    }

    private double actionSecondsFor(BattleCommand command) {
        return command == null
                ? minimumActionSeconds
                : actionSeconds.getOrDefault(command.name(), minimumActionSeconds);
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
        if (isKeyJustPressed("R")) {
            requestRetry();
            return;
        }
        if (isKeyJustPressed("E") || isKeyJustPressed("ENTER")) {
            requestFinish();
        }
    }

    /** Nome do sinal de retry declarado pelo Codex, com o padrao como rede de seguranca. */
    private String resolveRetrySignalName() {
        JSONObject actions = visuals == null ? null : visuals.optJSONObject("outcomeActions");
        JSONObject retry = actions == null ? null : actions.optJSONObject("retry");
        String actionData = retry == null ? null : retry.optString("actionData", null);
        if (actionData == null || !actionData.startsWith(ACTION_DATA_PREFIX)
                || actionData.length() == ACTION_DATA_PREFIX.length()) {
            return DEFAULT_RETRY_SIGNAL;
        }
        return actionData.substring(ACTION_DATA_PREFIX.length());
    }

    /**
     * Porta unica da nova tentativa, para o clique do {@code BattleRetryButton} e para a
     * tecla R. Vale SOMENTE em {@code RESULT} com {@code DEFEAT}: vitoria nao se repete,
     * e um sinal que chegue em qualquer outro modo e descartado antes de virar acao.
     *
     * <p>Nao existe segunda {@code BattleSimulation} aqui. Quem devolve o jogador a
     * exploracao e prepara outra tentativa e o {@code GameFlowController}, ao ler o
     * desfecho no payload de {@code SIGNAL_DUEL_COMPLETE}.</p>
     */
    private void requestRetry() {
        BattleOutcome outcome = battle == null ? null : battle.outcome();
        if (!retryAllowed(flow.mode(), outcome)) {
            log("BattleDirector: retry ignorado em " + flow.mode()
                    + " (desfecho=" + (outcome == null ? "sem duelo" : outcome) + ").");
            return;
        }
        finishDuel();
    }

    /** Regra do retry isolada e pura: {@code RESULT} e {@code DEFEAT}, nada mais. */
    private static boolean retryAllowed(Mode mode, BattleOutcome outcome) {
        return mode == Mode.RESULT && outcome == BattleOutcome.DEFEAT;
    }

    private void requestFinish() {
        if (!flow.acceptsFinish()) {
            log("BattleDirector: encerramento ignorado em " + flow.mode() + ".");
            return;
        }
        finishDuel();
    }

    /**
     * Unico caminho de saida do duelo, para vitoria, rendicao e derrota. O retorno
     * jogavel e a nova tentativa apos derrota pertencem ao GameFlowController, que le
     * o desfecho do payload: por isso o botao de retry do canvas tambem chega aqui em
     * vez de reiniciar a batalha por dentro e deixar o fluxo de exploracao dessincronizado.
     */
    private void finishDuel() {
        if (completionDelivered) return;
        completionDelivered = true;
        BattleOutcome outcome = battle.outcome();
        if (hud != null) {
            hud.hide();
        }
        hideVfx(hydrolaminaVfx);
        hideVfx(guardReactionVfx);
        hideVfx(wolfStrikeVfx);
        if (leaderActor != null) {
            leaderActor.setVisible(false);
        }
        setCameraZoom(1.6);
        shownGrade = null;
        flow.reset();
        sceneDispatcher.enqueue(SIGNAL_DUEL_COMPLETE, outcome.name());
        log("BattleDirector: duelo encerrado com desfecho " + outcome.name()
                + " no turno " + battle.turnCount() + ".");
    }

    // ==================== Snapshot (unica fonte de apresentacao) ====================

    private void refreshHud() {
        if (hud != null) {
            hud.render(snapshot());
        }
    }

    private HudSnapshot snapshot() {
        HudSnapshot snap = new HudSnapshot();
        snap.mode = flow.mode();
        snap.rimuruHp = battle.rimuruHp();
        snap.rimuruMaxHp = BattleSimulation.RIMURU_MAX_HP;
        snap.magicules = battle.rimuruMagicules();
        snap.maxMagicules = BattleSimulation.RIMURU_MAX_MAGICULES;
        snap.leaderHp = battle.leaderHp();
        snap.leaderMaxHp = battle.leaderMaxHp();
        snap.morale = battle.leaderMorale();
        snap.maxMorale = BattleSimulation.LEADER_MAX_MORALE;
        snap.turn = battle.turnCount();
        snap.phaseTitle = battle.leaderPhase().title();
        snap.timingLabel = timingModeLabel;

        BattleIntention intention = battle.currentIntention();
        snap.intentionName = intention.actionName();
        snap.intentionHint = intention.telegraphHint();
        snap.intentionTelegraphed = intention.requiresReaction();
        snap.intentionShape = telegraphKeyOf(intention.phase());
        snap.analyzed = battle.analyzed();
        snap.revealed = sortedInfo();

        snap.statusMessage = statusMessage;
        for (BattleCommand command : keyToCommand.values()) {
            snap.commandEnabled.put(command.name(), commandEnabled(command));
        }
        snap.reacting = flow.mode() == Mode.REACTION;
        if (snap.reacting) {
            snap.reactionPrompt = config.getJSONObject("reaction").getString("prompt");
            double total = telegraphSeconds + reactionWindow.activeRadius();
            snap.reactionProgress = total <= 0 ? 0 : Math.min(1.0, reactionElapsed / total);
            snap.reactionIdealProgress = total <= 0 ? 0 : telegraphSeconds / total;
        }
        snap.grade = shownGrade;
        snap.gradeElapsed = gradeElapsed;
        if (flow.mode() == Mode.RESULT) {
            snap.outcomeText = outcomeText.getOrDefault(
                    battle.outcome().name(), battle.outcome().description());
            snap.retryOffered = battle.outcome() == BattleOutcome.DEFEAT;
        }
        return snap;
    }

    /**
     * Chave semantica do telegraph a partir da FASE do dominio, nao do texto da
     * intencao: comparar substrings em portugues quebraria silenciosamente a qualquer
     * ajuste de copy, e o manifesto visual indexa exatamente por essas tres chaves.
     */
    private static String telegraphKeyOf(LeaderPhase phase) {
        return switch (phase) {
            case PROBING -> "bite";
            case PACK_PRESSURE -> "pack_call";
            case DECISIVE_STRIKE -> "charge";
            case MORAL_BREAKDOWN -> null;
        };
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

    private static String bar(int value, int max) {
        int slots = 10;
        int filled = max <= 0 ? 0 : Math.max(0, Math.min(slots, (int) Math.round((double) value / max * slots)));
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < slots; i++) {
            builder.append(i < filled ? '#' : '.');
        }
        return builder + " " + value + "/" + max;
    }

    // ==================== Atores e VFX de mundo ====================

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

    // ==================== Dados ====================

    private void parseConfig(JSONObject data) {
        telegraphSeconds = data.optDouble("reactionTelegraphSeconds", 1.1);
        reactionWindow = resolveTimingMode(data);
        parseActionSeconds(data);

        JSONArray commands = data.getJSONArray("commands");
        for (int i = 0; i < commands.length(); i++) {
            JSONObject entry = commands.getJSONObject(i);
            String key = entry.getString("key");
            BattleCommand command = BattleCommand.valueOf(entry.getString("command"));
            keyToCommand.put(key, command);
            commandLabel.put(command.name(), entry.optString("label", command.commandName()));
            commandHint.put(command.name(), entry.optString("hint", command.description()));
            signalToCommand.put(COMMAND_SIGNAL_PREFIX + command.name(), command);
        }
        JSONObject outcomes = data.getJSONObject("outcomes");
        for (String key : outcomes.keySet()) {
            outcomeText.put(key, outcomes.getString(key));
        }
        parseGrades(data);
    }

    /**
     * Cada grau recebe palavra, cor, forma e movimento. A palavra vem do texto de
     * batalha; cor/forma/movimento vem do manifesto visual do Codex quando ele existe
     * e, se ele faltar, dos defaults abaixo — nunca de uma degradacao para "so cor",
     * que e exatamente o que a acessibilidade do POL-3 proibe.
     */
    private void parseGrades(JSONObject data) {
        JSONObject labels = data.getJSONObject("reaction").getJSONObject("gradeLabels");
        JSONObject authored = visuals == null ? null : visuals.optJSONObject("reactionGrades");
        for (ReactionTiming timing : ReactionTiming.values()) {
            String name = timing.name();
            JSONObject entry = authored == null ? null : authored.optJSONObject(name);
            String label = entry != null && entry.has("label")
                    ? entry.getString("label")
                    : labels.optString(name, name);
            Color color = parseColor(entry == null ? null : entry.optString("color", null),
                    DEFAULT_GRADE_COLORS.get(name));
            String shape = entry == null ? null : entry.optString("shape", null);
            String motion = entry == null ? null : entry.optString("motion", null);
            gradeFeedback.put(name, new GradeFeedback(
                    label,
                    color,
                    glyphOf(shape == null ? DEFAULT_GRADE_SHAPES.get(name) : shape),
                    Motion.of(motion == null ? DEFAULT_GRADE_MOTIONS.get(name) : motion)));
        }
    }

    private static final Map<String, Color> DEFAULT_GRADE_COLORS = Map.of(
            "PERFECT", new Color(114, 246, 255),
            "GOOD", new Color(117, 220, 139),
            "EARLY", new Color(242, 193, 78),
            "LATE", new Color(240, 138, 75),
            "NONE", new Color(164, 170, 181));

    private static final Map<String, String> DEFAULT_GRADE_SHAPES = Map.of(
            "PERFECT", "four_point_star",
            "GOOD", "closed_ring",
            "EARLY", "left_chevrons",
            "LATE", "right_chevrons",
            "NONE", "broken_ring");

    private static final Map<String, String> DEFAULT_GRADE_MOTIONS = Map.of(
            "PERFECT", "snap_and_ring",
            "GOOD", "short_pulse",
            "EARLY", "pull_left",
            "LATE", "push_right",
            "NONE", "fade_down");

    // Medicao de texto sem Graphics: serve tanto ao runtime quanto a prova offline.
    private static final FontRenderContext TEXT_METRICS = new FontRenderContext(null, true, true);
    private static final String ELLIPSIS = "...";

    private static String fitToBox(String text, UILabel label) {
        return fitToBox(text, label.getWidth(), label.getHeight(),
                label.getFont(), label.getLineSpacing());
    }

    /**
     * Quebra {@code text} nas palavras que cabem na largura e descarta o que passar da
     * altura, marcando o corte. Determinista: mesma caixa e mesma fonte sempre produzem
     * as mesmas linhas, o que torna a contencao provavel fora do editor.
     */
    static String fitToBox(String text, double width, double height, Font font, int lineSpacing) {
        if (text == null || text.isEmpty() || width <= 0 || height <= 0) {
            return text == null ? "" : text;
        }
        double lineHeight = font.getLineMetrics("Ay", TEXT_METRICS).getHeight() + lineSpacing;
        int maxLines = Math.max(1, (int) Math.floor(height / lineHeight));

        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            wrapParagraph(paragraph, width, font, lines);
        }
        if (lines.size() <= maxLines) {
            return String.join("\n", lines);
        }
        List<String> kept = new ArrayList<>(lines.subList(0, maxLines));
        int last = maxLines - 1;
        kept.set(last, truncate(kept.get(last), width, font));
        return String.join("\n", kept);
    }

    private static void wrapParagraph(String paragraph, double width, Font font,
            List<String> lines) {
        if (paragraph.isEmpty()) {
            lines.add("");
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            if (line.length() == 0) {
                line.append(word);
                continue;
            }
            String candidate = line + " " + word;
            if (textWidth(candidate, font) <= width) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        // Palavra unica maior que a caixa ainda precisa caber: corta com reticencias.
        String tail = line.toString();
        lines.add(textWidth(tail, font) <= width ? tail : truncate(tail, width, font));
    }

    /**
     * Sempre termina em reticencias: quem chama ja decidiu que ha conteudo descartado,
     * e uma ultima linha sem marca nenhuma esconderia o corte do leitor.
     */
    private static String truncate(String text, double width, Font font) {
        if (textWidth(text + ELLIPSIS, font) <= width) {
            return text + ELLIPSIS;
        }
        StringBuilder shortened = new StringBuilder(text);
        while (shortened.length() > 0
                && textWidth(shortened + ELLIPSIS, font) > width) {
            shortened.setLength(shortened.length() - 1);
        }
        return shortened + ELLIPSIS;
    }

    private static double textWidth(String text, Font font) {
        return font.getStringBounds(text, TEXT_METRICS).getWidth();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    /** Forma legivel do grau sem depender de cor, tambem para quem le so texto. */
    private static String glyphOf(String shape) {
        if (shape == null) return "[ ]";
        return switch (shape) {
            case "four_point_star" -> "-*-";
            case "closed_ring" -> "(O)";
            case "left_chevrons" -> "<<<";
            case "right_chevrons" -> ">>>";
            case "broken_ring" -> "( )";
            case "double_chevron" -> ">>";
            case "concentric_ring" -> "(())";
            case "falling_wedge" -> "\\/";
            default -> "[ ]";
        };
    }

    /** Aceita {@code #RRGGBB} e {@code #RRGGBBAA} do manifesto visual. */
    private static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        String value = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (value.length() == 6) {
                return new Color(Integer.parseInt(value, 16));
            }
            if (value.length() == 8) {
                return new Color(
                        Integer.parseInt(value.substring(0, 2), 16),
                        Integer.parseInt(value.substring(2, 4), 16),
                        Integer.parseInt(value.substring(4, 6), 16),
                        Integer.parseInt(value.substring(6, 8), 16));
            }
        } catch (NumberFormatException ignored) {
            return fallback;
        }
        return fallback;
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

    /** O manifesto visual e do Codex e pode nao existir num build enxuto. */
    private JSONObject readOptionalJson(String relativePath) {
        File file = AssetResolver.resolve(relativePath);
        if (file == null || !file.exists()) {
            log("BattleDirector: " + relativePath + " ausente; usando defaults de forma e movimento.");
            return null;
        }
        try {
            return new JSONObject(Files.readString(file.toPath()));
        } catch (IOException | RuntimeException exception) {
            log("BattleDirector: " + relativePath + " ilegivel (" + exception.getMessage()
                    + "); usando defaults de forma e movimento.");
            return null;
        }
    }

    // ==================== Maquina de estados (pura) ====================

    private enum Mode {
        INACTIVE,
        COMMAND,
        ACTION,
        REACTION,
        RESULT
    }

    /**
     * Autoridade unica sobre o modo do duelo e sobre qual input e aceito em cada
     * instante. Pura de proposito: sem Ignis, sem dominio e sem UI, ela pode ser
     * exercitada fora do editor, que e como "input durante ACTION/REACTION nao produz
     * um segundo calculo" deixa de ser promessa e vira propriedade provada.
     *
     * <p>As transicoes falham rapido: todo chamador do adaptador ja consultou o
     * {@code accepts*} correspondente, entao uma transicao a partir de um modo
     * invalido e defeito de programacao, nao entrada do jogador.</p>
     */
    private static final class DuelFlow {

        private Mode mode = Mode.INACTIVE;

        Mode mode() {
            return mode;
        }

        boolean isIdle() {
            return mode == Mode.INACTIVE;
        }

        /** Somente em COMMAND um comando pode ser calculado. */
        boolean acceptsCommand() {
            return mode == Mode.COMMAND;
        }

        /** Somente em REACTION a barra de espaco vale como reacao defensiva. */
        boolean acceptsReaction() {
            return mode == Mode.REACTION;
        }

        /** Somente em ACTION o jogador pode encurtar a apresentacao. */
        boolean acceptsSkip() {
            return mode == Mode.ACTION;
        }

        /** Somente em RESULT o duelo pode ser encerrado pelo jogador. */
        boolean acceptsFinish() {
            return mode == Mode.RESULT;
        }

        void beginDuel() {
            require(mode == Mode.INACTIVE, "beginDuel");
            mode = Mode.COMMAND;
        }

        void awaitReaction() {
            require(mode == Mode.COMMAND, "awaitReaction");
            mode = Mode.REACTION;
        }

        void beginAction() {
            require(mode == Mode.COMMAND || mode == Mode.REACTION, "beginAction");
            mode = Mode.ACTION;
        }

        void backToCommand() {
            require(mode != Mode.INACTIVE, "backToCommand");
            mode = Mode.COMMAND;
        }

        void settle(boolean duelFinished) {
            require(mode == Mode.ACTION, "settle");
            mode = duelFinished ? Mode.RESULT : Mode.COMMAND;
        }

        void reset() {
            mode = Mode.INACTIVE;
        }

        private void require(boolean condition, String transition) {
            if (!condition) {
                throw new IllegalStateException(
                        "Transicao invalida do duelo: " + transition + " a partir de " + mode + ".");
            }
        }
    }

    // ==================== Feedback de reacao (palavra, cor, forma, movimento) ====================

    /** Deslocamento e opacidade do feedback ao longo do tempo; o movimento e a 4a pista. */
    private enum Motion {
        SNAP_AND_RING,
        SHORT_PULSE,
        PULL_LEFT,
        PUSH_RIGHT,
        FADE_DOWN;

        static Motion of(String authored) {
            if (authored == null) return SHORT_PULSE;
            return switch (authored) {
                case "snap_and_ring" -> SNAP_AND_RING;
                case "short_pulse" -> SHORT_PULSE;
                case "pull_left" -> PULL_LEFT;
                case "push_right" -> PUSH_RIGHT;
                case "fade_down" -> FADE_DOWN;
                default -> SHORT_PULSE;
            };
        }

        double dx(double t) {
            return switch (this) {
                case PULL_LEFT -> -34 * (1 - Math.exp(-7 * t));
                case PUSH_RIGHT -> 34 * (1 - Math.exp(-7 * t));
                default -> 0;
            };
        }

        double dy(double t) {
            return switch (this) {
                case SNAP_AND_RING -> -10 * Math.exp(-5 * t) * Math.cos(18 * t);
                case SHORT_PULSE -> -6 * Math.sin(Math.PI * Math.min(1.0, t / 0.35));
                case FADE_DOWN -> 20 * Math.min(1.0, t / 0.5);
                default -> 0;
            };
        }

        float alpha(double t) {
            double fade = this == FADE_DOWN ? 1.0 - 0.6 * Math.min(1.0, t / 0.5) : 1.0;
            return (float) Math.max(0.25, fade);
        }
    }

    private static final class GradeFeedback {
        private final String label;
        private final Color color;
        private final String glyph;
        private final Motion motion;

        GradeFeedback(String label, Color color, String glyph, Motion motion) {
            this.label = label;
            this.color = color;
            this.glyph = glyph;
            this.motion = motion;
        }

        String label() {
            return label;
        }

        Color color() {
            return color;
        }

        Motion motion() {
            return motion;
        }

        /** Palavra e forma juntas: legivel mesmo sem enxergar cor nem movimento. */
        String badge() {
            return glyph + "  " + label + "  " + glyph;
        }
    }

    // ==================== Snapshot compartilhado pelos presenters ====================

    /** Tudo que o HUD precisa saber. Nenhum presenter consulta a simulacao direto. */
    private static final class HudSnapshot {
        Mode mode = Mode.INACTIVE;
        int rimuruHp;
        int rimuruMaxHp;
        int magicules;
        int maxMagicules;
        int leaderHp;
        int leaderMaxHp;
        int morale;
        int maxMorale;
        int turn;
        String phaseTitle = "";
        String timingLabel = "";
        String intentionName = "";
        String intentionHint = "";
        boolean intentionTelegraphed;
        String intentionShape;
        boolean analyzed;
        List<String> revealed = List.of();
        String statusMessage = "";
        final Map<String, Boolean> commandEnabled = new LinkedHashMap<>();
        boolean reacting;
        String reactionPrompt = "";
        double reactionProgress;
        double reactionIdealProgress;
        GradeFeedback grade;
        double gradeElapsed;
        String outcomeText;
        boolean retryOffered;
    }

    // ==================== Presenters ====================

    /**
     * Contrato de apresentacao do duelo. Um unico presenter e montado por entrada no
     * duelo; ambos recebem o mesmo snapshot e nao decidem nada de combate.
     */
    private interface BattleHud {

        /** {@code persistent} ou {@code volatile_fallback}, para o log de integracao. */
        String kind();

        void show();

        void hide();

        void render(HudSnapshot snapshot);

        /** Anima o que depende do tempo (movimento do grau, layout responsivo). */
        void animate(double deltaTime);

        void dispose();
    }

    /**
     * Fallback TRANSITORIO enquanto o {@code BattleHudCanvas} nao existe na cena.
     * Constroi a UI no canvas global do editor a cada entrada no duelo e nao persiste
     * nada. Os botoes carregam o MESMO {@code actionData} do manifesto e emitem o
     * MESMO sinal do canvas persistente, entao teclado, mouse e QA percorrem um unico
     * caminho de comando nos dois presenters.
     */
    private final class VolatileBattleHud implements BattleHud {

        private UIPanel shade;
        private UILabel titleLabel;
        private UIPanel statusPanel;
        private UILabel vitalsLabel;
        private UILabel intentionLabel;
        private UIPanel commandPanel;
        private UILabel commandHintLabel;
        private UILabel messageLabel;
        private UILabel reactionLabel;
        private UIPanel reactionTrack;
        private UIPanel reactionMarker;
        private UILabel gradeLabel;
        private UILabel resultLabel;
        private final Map<String, UIButton> commandButtons = new LinkedHashMap<>();

        private UIImage leaderPortraitImage;
        private final Map<String, UIImage> telegraphImages = new LinkedHashMap<>();

        private double gradeBaseX;
        private double gradeBaseY;

        @Override
        public String kind() {
            return "volatile_fallback";
        }

        void build() {
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

            commandPanel = createPanel(0, 0, 360, 260);
            setUIColors(commandPanel, new Color(10, 18, 26, 236), null, new Color(120, 190, 140));
            buildCommandButtons();
            commandHintLabel = createLabel("", 0, 0, 330, 44);
            commandHintLabel.setFont("SansSerif", Font.PLAIN, 12);
            commandHintLabel.setTextColor(new Color(190, 214, 200));
            commandHintLabel.setMultiline(true);

            messageLabel = createLabel("", 0, 0, 900, 54);
            messageLabel.setAlignment(UILabel.Alignment.CENTER);
            messageLabel.setFont("SansSerif", Font.BOLD, 15);
            messageLabel.setTextColor(new Color(236, 246, 250));
            messageLabel.setMultiline(true);

            reactionTrack = createPanel(0, 0, 420, 14);
            setUIColors(reactionTrack, new Color(12, 24, 34, 230), null, new Color(111, 217, 245));
            reactionMarker = createPanel(0, 0, 10, 26);
            setUIColors(reactionMarker, new Color(255, 232, 160), null, new Color(255, 255, 255));

            reactionLabel = createLabel("", 0, 0, 400, 40);
            reactionLabel.setAlignment(UILabel.Alignment.CENTER);
            reactionLabel.setFont("SansSerif", Font.BOLD, 26);
            reactionLabel.setTextColor(new Color(255, 210, 120));
            reactionLabel.setOutline(true, new Color(40, 16, 6), 2);

            gradeLabel = createLabel("", 0, 0, 420, 44);
            gradeLabel.setAlignment(UILabel.Alignment.CENTER);
            gradeLabel.setFont("Monospaced", Font.BOLD, 24);
            gradeLabel.setOutline(true, new Color(10, 16, 24), 2);

            resultLabel = createLabel("", 0, 0, 900, 40);
            resultLabel.setAlignment(UILabel.Alignment.CENTER);
            resultLabel.setFont("SansSerif", Font.BOLD, 16);
            resultLabel.setTextColor(new Color(255, 236, 190));

            leaderPortraitImage = createImage(PORTRAIT_PATH, 30, 75, 72, 72);
            telegraphImages.put("bite", createImage(BITE_TELEGRAPH_PATH, 420, 160, 128, 128));
            telegraphImages.put("pack_call", createImage(PACK_CALL_TELEGRAPH_PATH, 420, 160, 128, 128));
            telegraphImages.put("charge", createImage(CHARGE_TELEGRAPH_PATH, 420, 160, 128, 128));

            hide();
            layout();
        }

        private void buildCommandButtons() {
            JSONArray authored = visuals == null ? null : visuals.optJSONArray("commands");
            for (Map.Entry<String, BattleCommand> entry : keyToCommand.entrySet()) {
                BattleCommand command = entry.getValue();
                String name = command.name();
                String label = "[" + entry.getKey() + "] "
                        + commandLabel.getOrDefault(name, command.commandName());
                UIButton button = createButton(label, 0, 0, 168, 52);
                button.setName(objectNameOf(authored, name, "BattleCommand" + name));
                button.setActionData(ACTION_DATA_PREFIX + COMMAND_SIGNAL_PREFIX + name);
                button.setFont("SansSerif", Font.BOLD, 13);
                button.setColorScheme(new Color(19, 42, 69), new Color(34, 74, 112), new Color(58, 110, 150));
                button.setDisabledColor(new Color(94, 104, 120));
                // O clique nunca chama o dominio: emite o mesmo sinal que o canvas
                // persistente emitiria, e requestCommand decide se a janela esta aberta.
                button.setOnClick(() -> sceneDispatcher.enqueue(
                        COMMAND_SIGNAL_PREFIX + name, null));
                commandButtons.put(name, button);
            }
        }

        private String objectNameOf(JSONArray authored, String command, String fallback) {
            if (authored == null) return fallback;
            for (int i = 0; i < authored.length(); i++) {
                JSONObject entry = authored.getJSONObject(i);
                if (command.equals(entry.optString("command"))) {
                    return entry.optString("objectName", fallback);
                }
            }
            return fallback;
        }

        @Override
        public void show() {
            setAll(true);
            layout();
        }

        @Override
        public void hide() {
            setAll(false);
        }

        private void setAll(boolean visible) {
            for (UIComponent component : List.of(shade, titleLabel, statusPanel, vitalsLabel,
                    intentionLabel, commandPanel, commandHintLabel, messageLabel)) {
                component.setVisible(visible);
            }
            for (UIButton button : commandButtons.values()) {
                button.setVisible(visible);
            }
            if (leaderPortraitImage != null) {
                leaderPortraitImage.setVisible(visible);
            }
            if (!visible) {
                reactionLabel.setVisible(false);
                reactionTrack.setVisible(false);
                reactionMarker.setVisible(false);
                gradeLabel.setVisible(false);
                resultLabel.setVisible(false);
                hideTelegraphs();
            }
        }

        @Override
        public void render(HudSnapshot snap) {
            vitalsLabel.setText(
                    "RIMURU   HP " + bar(snap.rimuruHp, snap.rimuruMaxHp)
                            + "   Magiaules " + snap.magicules + "/" + snap.maxMagicules
                            + "\nLIDER    HP " + bar(snap.leaderHp, snap.leaderMaxHp)
                            + "   Moral " + bar(snap.morale, snap.maxMorale)
                            + "\nTurno " + snap.turn + "   Fase: " + snap.phaseTitle
                            + "   Timing: " + snap.timingLabel);

            String telegraph = snap.intentionTelegraphed ? "  [!] investida telegrafada" : "";
            String reveal = snap.analyzed
                    ? "\nGrande Sabio: " + String.join(" | ", snap.revealed)
                    : "\n(Use Analisar para revelar a intencao e a condicao de rendicao.)";
            intentionLabel.setText("INTENCAO DO LIDER: " + snap.intentionName + telegraph
                    + "\n" + snap.intentionHint + reveal);

            messageLabel.setText(snap.statusMessage);
            renderCommands(snap);
            renderReaction(snap);
            renderGrade(snap);
            renderResult(snap);
            renderTelegraphs(snap);
            layout();
        }

        private void renderCommands(HudSnapshot snap) {
            boolean interactive = snap.mode == Mode.COMMAND;
            StringBuilder hint = new StringBuilder();
            for (Map.Entry<String, UIButton> entry : commandButtons.entrySet()) {
                boolean enabled = Boolean.TRUE.equals(snap.commandEnabled.get(entry.getKey()));
                // Comando indisponivel continua visivel com rotulo e atalho: o motivo
                // vai na linha de dica, nunca esconder a opcao do jogador.
                entry.getValue().setEnabled(interactive && enabled);
                if (!enabled && hint.length() == 0) {
                    hint.append(commandHint.getOrDefault(entry.getKey(), ""));
                }
            }
            commandHintLabel.setText(interactive
                    ? hint.toString()
                    : "Apresentacao em curso: os comandos voltam ao fim do beat.");
        }

        private void renderReaction(HudSnapshot snap) {
            reactionLabel.setVisible(snap.reacting);
            reactionTrack.setVisible(snap.reacting);
            reactionMarker.setVisible(snap.reacting);
            if (snap.reacting) {
                reactionLabel.setText(snap.reactionPrompt);
                double x = reactionTrack.getX() + snap.reactionProgress * (reactionTrack.getWidth() - 10);
                reactionMarker.setPosition(x, reactionTrack.getY() - 6);
            }
        }

        private void renderGrade(HudSnapshot snap) {
            GradeFeedback grade = snap.grade;
            gradeLabel.setVisible(grade != null);
            if (grade == null) return;
            gradeLabel.setText(grade.badge());
            gradeLabel.setTextColor(grade.color());
        }

        private void renderResult(HudSnapshot snap) {
            boolean finished = snap.mode == Mode.RESULT;
            resultLabel.setVisible(finished);
            if (!finished) return;
            resultLabel.setText(snap.outcomeText
                    + (snap.retryOffered ? "\n[R] tentar novamente   [E] encerrar" : "\n[E] encerrar"));
        }

        private void renderTelegraphs(HudSnapshot snap) {
            boolean showable = snap.mode != Mode.RESULT && snap.mode != Mode.ACTION
                    && snap.intentionShape != null
                    && (snap.reacting || snap.intentionTelegraphed);
            for (Map.Entry<String, UIImage> entry : telegraphImages.entrySet()) {
                if (entry.getValue() != null) {
                    entry.getValue().setVisible(showable && entry.getKey().equals(snap.intentionShape));
                }
            }
        }

        private void hideTelegraphs() {
            for (UIImage image : telegraphImages.values()) {
                if (image != null) image.setVisible(false);
            }
        }

        @Override
        public void animate(double deltaTime) {
            if (!gradeLabel.isVisible() || shownGrade == null) return;
            Motion motion = shownGrade.motion();
            gradeLabel.setPosition(
                    gradeBaseX + motion.dx(gradeElapsed),
                    gradeBaseY + motion.dy(gradeElapsed));
            Color base = shownGrade.color();
            int alpha = Math.round(255 * motion.alpha(gradeElapsed));
            gradeLabel.setTextColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        }

        private void layout() {
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
            layoutCommandButtons(commandX + 16, 78);
            commandHintLabel.setPosition(commandX + 16, 78 + 3 * 60 + 6);

            messageLabel.setSize(Math.min(900, width - 40), 54);
            messageLabel.setPosition((width - messageLabel.getWidth()) / 2.0, height - 70);

            reactionTrack.setPosition((width - reactionTrack.getWidth()) / 2.0, height / 2.0 + 26);
            reactionLabel.setPosition((width - 400) / 2.0, height / 2.0 - 20);

            gradeBaseX = (width - gradeLabel.getWidth()) / 2.0;
            gradeBaseY = height / 2.0 + 56;
            if (shownGrade == null) {
                gradeLabel.setPosition(gradeBaseX, gradeBaseY);
            }
            resultLabel.setSize(Math.min(900, width - 40), 40);
            resultLabel.setPosition((width - resultLabel.getWidth()) / 2.0, height - 128);

            if (leaderPortraitImage != null) {
                leaderPortraitImage.setPosition(margin + 16, 72);
            }
            double telegraphX = (width - 128) / 2.0;
            for (UIImage image : telegraphImages.values()) {
                if (image != null) image.setPosition(telegraphX, 56);
            }
        }

        /** Duas colunas de tres: cada alvo respeita o minimo de 72x52 do manifesto. */
        private void layoutCommandButtons(double originX, double originY) {
            int index = 0;
            for (UIButton button : commandButtons.values()) {
                double x = originX + (index % 2) * 176;
                double y = originY + (index / 2) * 60;
                button.setPosition(x, y);
                index++;
            }
        }

        @Override
        public void dispose() {
            for (UIButton button : commandButtons.values()) {
                removeUI(button);
            }
            commandButtons.clear();
            for (UIComponent component : List.of(shade, titleLabel, statusPanel, vitalsLabel,
                    intentionLabel, commandPanel, commandHintLabel, messageLabel, reactionLabel,
                    reactionTrack, reactionMarker, gradeLabel, resultLabel)) {
                removeUI(component);
            }
            for (UIImage image : telegraphImages.values()) {
                removeUI(image);
            }
            telegraphImages.clear();
            removeUI(leaderPortraitImage);
        }
    }

    /**
     * Presenter definitivo: consome o {@code BattleHudCanvas} persistente que o Codex
     * sincroniza na {@code JuraForestScene}. Nao cria widget nenhum — se um widget
     * declarado no manifesto faltar, {@link #bind()} devolve o diagnostico e o duelo
     * recusa iniciar, porque um canvas pela metade e defeito de integracao.
     */
    private final class PersistentBattleHud implements BattleHud {

        private static final String ANCHOR_PLAYER = "playerPanel";
        private static final String ANCHOR_LEADER = "leaderPanel";
        private static final String ANCHOR_INTENTION = "intentionPanel";
        private static final String ANCHOR_REACTION = "reactionTrack";
        private static final String ANCHOR_STATUS = "statusLine";
        private static final String ANCHOR_DOCK = "commandDock";

        private final GameObject canvasObject;
        private UICanvas canvas;
        private final Map<String, UIComponent> widgets = new LinkedHashMap<>();
        private final Map<String, UIButton> commandButtons = new LinkedHashMap<>();
        private final Map<String, UIComponent> commandIcons = new LinkedHashMap<>();
        private final Map<String, String> intentionSprites = new LinkedHashMap<>();
        // Cada widget reposicionavel e ancorado a um dos seis retangulos do manifesto.
        private final Map<String, String> anchorOfWidget = new LinkedHashMap<>();
        private UIButton retryButton;
        private String retrySignal;
        private double gradeBaseX;
        private double gradeBaseY;
        private double appliedViewportWidth;
        private double appliedViewportHeight;

        PersistentBattleHud(GameObject canvasObject) {
            this.canvasObject = canvasObject;
        }

        @Override
        public String kind() {
            return "persistent";
        }

        /** @return null quando o canvas atende ao manifesto; senao, o diagnostico. */
        String bind() {
            CanvasComponent component = canvasObject.getComponent(CanvasComponent.class);
            if (component == null || component.getCanvas() == null) {
                return "CanvasComponent ausente em " + canvasObject.getName();
            }
            canvas = component.getCanvas();

            JSONObject declared = visuals.getJSONObject("widgets");
            for (String role : declared.keySet()) {
                String widgetName = declared.getString(role);
                UIComponent widget = canvas.findByName(widgetName);
                if (widget == null) {
                    return "widget " + widgetName + " (" + role + ") nao existe no canvas";
                }
                widgets.put(role, widget);
            }

            JSONArray commands = visuals.getJSONArray("commands");
            for (int i = 0; i < commands.length(); i++) {
                JSONObject entry = commands.getJSONObject(i);
                String command = entry.getString("command");
                String objectName = entry.getString("objectName");
                UIComponent widget = canvas.findByName(objectName);
                if (!(widget instanceof UIButton button)) {
                    return "botao " + objectName + " (" + command + ") ausente ou de tipo errado";
                }
                String signal = signalOf(button, entry.optString("actionData", null));
                if (signal == null) {
                    return "botao " + objectName + " sem actionData 'signal:<NOME>'";
                }
                if (!signalToCommand.containsKey(signal)) {
                    return "botao " + objectName + " aponta para o sinal desconhecido " + signal;
                }
                // setOnClick substitui o handler anterior: recarregar o script nao acumula
                // ouvinte nem produz dois calculos para o mesmo clique.
                button.setOnClick(() -> sceneDispatcher.enqueue(signal, null));
                commandButtons.put(command, button);
                // O icone acompanha o botao no grid; e opcional no contrato.
                UIComponent icon = canvas.findByName(objectName + "Icon");
                if (icon != null) {
                    commandIcons.put(command, icon);
                }
            }
            if (commandButtons.size() != signalToCommand.size()) {
                return "manifesto liga " + commandButtons.size() + " comandos, o duelo tem "
                        + signalToCommand.size();
            }

            String missing = missingRequiredWidget();
            if (missing != null) {
                return "widget obrigatorio " + missing + " ausente no canvas";
            }

            String retryFailure = bindRetry();
            if (retryFailure != null) {
                return retryFailure;
            }

            JSONObject intentions = visuals.optJSONObject("intentionVisuals");
            if (intentions != null) {
                for (String key : intentions.keySet()) {
                    intentionSprites.put(key, intentions.getJSONObject(key).optString("sprite", null));
                }
            }
            captureAuthoredBounds();
            return null;
        }

        private String missingRequiredWidget() {
            JSONObject binding = visuals.optJSONObject("presenterBinding");
            JSONArray required = binding == null ? null : binding.optJSONArray("requiredWidgets");
            if (required == null) return null;
            for (int i = 0; i < required.length(); i++) {
                String name = required.getString(i);
                if (canvas.findByName(name) == null) {
                    return name;
                }
            }
            return null;
        }

        /**
         * Liga o botao de nova tentativa ao sinal declarado em
         * {@code outcomeActions.retry}. Ele nao cria batalha nenhuma: apenas enfileira o
         * sinal, e {@link BattleDirector#requestRetry()} decide se o estado permite —
         * mesma porta do teclado, mesmo {@code finishDuel()}, uma vez so.
         */
        private String bindRetry() {
            JSONObject actions = visuals.optJSONObject("outcomeActions");
            JSONObject retry = actions == null ? null : actions.optJSONObject("retry");
            if (retry == null) {
                return null;
            }
            String objectName = retry.optString("objectName", null);
            if (objectName == null || objectName.isBlank()) {
                return "outcomeActions.retry sem objectName";
            }
            UIComponent widget = canvas.findByName(objectName);
            if (!(widget instanceof UIButton button)) {
                return "botao de retry " + objectName + " ausente ou de tipo errado";
            }
            String signal = signalOf(button, retry.optString("actionData", null));
            if (signal == null) {
                return "botao de retry " + objectName + " sem actionData 'signal:<NOME>'";
            }
            if (signalToCommand.containsKey(signal)) {
                return "botao de retry " + objectName + " reusa o sinal de comando " + signal;
            }
            // O ouvinte do sinal foi registrado uma unica vez em start(); se o canvas
            // apontar para outro sinal, ninguem o escutaria e o botao morreria calado.
            if (!signal.equals(retrySignalName)) {
                return "botao de retry " + objectName + " emite " + signal
                        + ", mas o duelo escuta " + retrySignalName;
            }
            retrySignal = signal;
            retryButton = button;
            button.setOnClick(() -> sceneDispatcher.enqueue(signal, null));
            return null;
        }

        /** O motor guarda actionData mas nao o interpreta; quem converte em sinal e este runtime. */
        private String signalOf(UIButton button, String authored) {
            String actionData = button.getActionData();
            if (actionData == null || actionData.isBlank()) {
                actionData = authored;
            }
            if (actionData == null || !actionData.startsWith(ACTION_DATA_PREFIX)
                    || actionData.length() == ACTION_DATA_PREFIX.length()) {
                return null;
            }
            return actionData.substring(ACTION_DATA_PREFIX.length());
        }

        // ---------- Layout responsivo pelo viewport vivo ----------

        /**
         * Fotografa uma unica vez o layout autorado pelo Codex. Todo recalculo parte
         * DESSE baseline, nunca do resultado anterior: reaplicar o layout dez vezes
         * produz exatamente a mesma tela que aplicar uma vez.
         */
        private void captureAuthoredBounds() {
            captureAuthoredState();
            anchorOfWidget.clear();
            anchor(ANCHOR_PLAYER, "BattlePlayerPanel", "BattlePlayerLabel", "BattleMagiculesBar");
            anchor(ANCHOR_LEADER, "BattleLeaderPanel", "BattleLeaderLabel", "BattleHpBar",
                    "BattleMoraleBar", "BattleLeaderPortrait");
            anchor(ANCHOR_INTENTION, "BattleIntentionPanel", "BattleIntentionIcon",
                    "BattleIntentionLabel");
            anchor(ANCHOR_REACTION, "BattleReactionTrack", "BattleReactionMarker",
                    "BattleReactionPrompt", "BattleReactionResult");
            anchor(ANCHOR_STATUS, "BattleResultPanel", "BattleStatusLine", "BattleRetryButton");
            for (Map.Entry<String, UIButton> entry : commandButtons.entrySet()) {
                remember(entry.getValue().getName(), ANCHOR_DOCK);
                UIComponent icon = commandIcons.get(entry.getKey());
                if (icon != null) {
                    remember(icon.getName(), ANCHOR_DOCK);
                }
            }
            remember("BattlePhaseBadge", null);
            remember("BattleDimmer", null);
        }

        private void anchor(String anchorRole, String... widgetNames) {
            for (String name : widgetNames) {
                remember(name, anchorRole);
            }
        }

        private void remember(String widgetName, String anchorRole) {
            if (canvas.findByName(widgetName) == null || anchorRole == null) return;
            anchorOfWidget.put(widgetName, anchorRole);
        }

        /**
         * Fotografa TODOS os filhos do canvas — nao so os que o manifesto nomeia — antes
         * de qualquer escrita do presenter. Sao objetos de cena: o que ficar mutado aqui
         * o autosave grava no projeto do usuario.
         */
        private void captureAuthoredState() {
            if (!authoredState.isEmpty()) {
                return; // baseline e por sessao; recapturar apos o layout seria gravar o wide
            }
            for (UIComponent child : canvas.getChildrenOfType(UIComponent.class)) {
                if (child.getName() != null) {
                    authoredState.put(child.getName(), WidgetState.capture(child));
                }
            }
            warnIfBaselineLooksMutated();
        }

        /**
         * O canvas e autorado em coordenadas {@code compact}. Se o baseline chegar
         * diferente disso, alguem salvou a cena com o HUD ja transformado — e continuar
         * calado transformaria o acidente em novo padrao.
         */
        private void warnIfBaselineLooksMutated() {
            WidgetState dimmer = authoredState.get(
                    visuals.getJSONObject("widgets").optString("dimmer", "BattleDimmer"));
            JSONObject minimum = visuals.getJSONObject("canvas").getJSONObject("minimumViewport");
            if (dimmer == null) return;
            if (Math.abs(dimmer.width - minimum.getDouble("width")) > 1
                    || Math.abs(dimmer.height - minimum.getDouble("height")) > 1) {
                log("BattleDirector: ATENCAO — baseline do BattleHudCanvas veio em "
                        + (int) dimmer.width + "x" + (int) dimmer.height + ", e nao no compact "
                        + minimum.getInt("width") + "x" + minimum.getInt("height")
                        + ". A cena pode ter sido salva com o HUD ja transformado.");
            }
        }

        /** Devolve os 32 widgets exatamente como o Codex os autorou. */
        private void restoreAuthoredState() {
            for (Map.Entry<String, WidgetState> entry : authoredState.entrySet()) {
                UIComponent widget = canvas == null ? null : canvas.findByName(entry.getKey());
                if (widget != null) {
                    entry.getValue().restore(widget);
                }
            }
            appliedViewportWidth = 0;
            appliedViewportHeight = 0;
        }

        private Bounds authoredBoundsOf(String widgetName) {
            WidgetState state = authoredState.get(widgetName);
            return state == null ? null : state.bounds();
        }

        private double viewportWidth() {
            double width = canvas == null ? 0 : canvas.getWidth();
            return width > 0 ? width : Math.max(1, getGame().getWidth());
        }

        private double viewportHeight() {
            double height = canvas == null ? 0 : canvas.getHeight();
            return height > 0 ? height : Math.max(1, getGame().getHeight());
        }

        /**
         * Aplica os retangulos do manifesto ao viewport VIVO do canvas. O mapa logico do
         * mundo nao entra nesta decisao: o HUD e overlay de tela, e o proprio manifesto
         * declara essa independencia em {@code layoutSelection}.
         */
        private void applyLayout() {
            double viewWidth = viewportWidth();
            double viewHeight = viewportHeight();
            JSONObject selection = visuals.optJSONObject("layoutSelection");
            JSONObject wideWhen = selection == null ? null : selection.optJSONObject("wideWhen");
            double minWidth = wideWhen == null ? 960 : wideWhen.optDouble("minimumWidth", 960);
            double minHeight = wideWhen == null ? 600 : wideWhen.optDouble("minimumHeight", 600);
            boolean wide = viewWidth >= minWidth && viewHeight >= minHeight;

            JSONObject canvasContract = visuals.getJSONObject("canvas");
            double margin = canvasContract.optDouble("safeMargin", 24);
            JSONObject reference = canvasContract.getJSONObject(
                    wide ? "referenceViewport" : "minimumViewport");
            double referenceWidth = reference.getDouble("width");
            double referenceHeight = reference.getDouble("height");
            // Escala uniforme e centralizada: o layout autorado nunca e distorcido, so
            // reduzido ate caber, e o que sobra vira margem simetrica.
            double scale = Math.min(viewWidth / referenceWidth, viewHeight / referenceHeight);
            double offsetX = (viewWidth - referenceWidth * scale) / 2.0;
            double offsetY = (viewHeight - referenceHeight * scale) / 2.0;

            JSONObject layout = visuals.getJSONObject("layout");
            JSONObject target = layout.getJSONObject(wide ? "wide" : "compact");
            // A origem e SEMPRE o bloco compact: e nele que o Codex autorou o canvas
            // persistido, entao e nele que os widgets estao posicionados de fabrica.
            JSONObject origin = layout.getJSONObject("compact");

            for (Map.Entry<String, String> entry : anchorOfWidget.entrySet()) {
                if (ANCHOR_DOCK.equals(entry.getValue())) {
                    continue; // o dock e resolvido pelo grid, logo abaixo
                }
                UIComponent widget = canvas.findByName(entry.getKey());
                Bounds authored = authoredBoundsOf(entry.getKey());
                if (widget == null || authored == null) continue;
                Bounds from = boundsOf(origin, entry.getValue());
                Bounds to = scaled(boundsOf(target, entry.getValue()), scale, offsetX, offsetY);
                place(widget, authored.remapped(from, to), viewWidth, viewHeight, margin);
            }

            layoutCommandDock(target, scale, offsetX, offsetY, viewWidth, viewHeight, margin);
            layoutFullScreen("BattleDimmer", viewWidth, viewHeight);
            layoutTopCenter("BattlePhaseBadge", scale, viewWidth, margin);

            appliedViewportWidth = viewWidth;
            appliedViewportHeight = viewHeight;
            log("BattleDirector: layout " + (wide ? "wide" : "compact") + " aplicado em "
                    + (int) viewWidth + "x" + (int) viewHeight + " (escala "
                    + String.format("%.3f", scale) + ").");
        }

        private Bounds boundsOf(JSONObject block, String role) {
            JSONObject rect = block.getJSONObject(role);
            return new Bounds(rect.getDouble("x"), rect.getDouble("y"),
                    rect.getDouble("width"), rect.getDouble("height"));
        }

        private Bounds scaled(Bounds rect, double scale, double offsetX, double offsetY) {
            return new Bounds(rect.x * scale + offsetX, rect.y * scale + offsetY,
                    rect.width * scale, rect.height * scale);
        }

        /** Nenhum widget pode terminar fora da tela, nem invadir a margem de seguranca. */
        private void place(UIComponent widget, Bounds rect,
                double viewWidth, double viewHeight, double margin) {
            double width = Math.min(rect.width, viewWidth - 2 * margin);
            double height = Math.min(rect.height, viewHeight - 2 * margin);
            double x = Math.max(margin, Math.min(rect.x, viewWidth - margin - width));
            double y = Math.max(margin, Math.min(rect.y, viewHeight - margin - height));
            widget.setSize(width, height);
            widget.setPosition(x, y);
        }

        /**
         * Seis comandos no grid declarado: {@code 6x1} no wide, {@code 3x2} no compact.
         * A celula nunca fica menor que o alvo minimo de toque do manifesto.
         */
        private void layoutCommandDock(JSONObject target, double scale, double offsetX,
                double offsetY, double viewWidth, double viewHeight, double margin) {
            Bounds dock = scaled(boundsOf(target, ANCHOR_DOCK), scale, offsetX, offsetY);
            JSONObject arrangement = target.getJSONObject("commandArrangement");
            int columns = Math.max(1, arrangement.optInt("columns", 3));
            int rows = Math.max(1, arrangement.optInt("rows", 2));
            double gap = arrangement.optDouble("gap", 8) * scale;

            JSONObject accessibility = visuals.getJSONObject("accessibility");
            JSONObject hitTarget = accessibility.optJSONObject("minimumHitTarget");
            double minCellWidth = hitTarget == null ? 72 : hitTarget.optDouble("width", 72);
            double minCellHeight = hitTarget == null ? 52 : hitTarget.optDouble("height", 52);
            JSONObject maxTarget = accessibility.optJSONObject("maximumCommandTarget");
            double maxCellWidth = arrangement.optDouble("maximumCellWidth",
                    maxTarget == null ? 184 : maxTarget.optDouble("width", 184));
            double maxCellHeight = arrangement.optDouble("maximumCellHeight",
                    maxTarget == null ? 72 : maxTarget.optDouble("height", 72));

            // O teto vale em PIXEIS FINAIS: e numa janela grande, com escala acima de 1,
            // que a celula estoura — foi assim que o QA viu 206x169.
            double cellWidth = clamp((dock.width - gap * (columns - 1)) / columns,
                    minCellWidth, maxCellWidth);
            double cellHeight = clamp((dock.height - gap * (rows - 1)) / rows,
                    minCellHeight, maxCellHeight);
            // Sobra do dock vira margem simetrica, entao limitar o botao nao o joga
            // para o canto esquerdo do painel.
            double usedWidth = columns * cellWidth + gap * (columns - 1);
            double usedHeight = rows * cellHeight + gap * (rows - 1);
            double originX = dock.x + Math.max(0, (dock.width - usedWidth) / 2.0);
            double originY = dock.y + Math.max(0, (dock.height - usedHeight) / 2.0);

            int index = 0;
            for (Map.Entry<String, UIButton> entry : commandButtons.entrySet()) {
                double x = originX + (index % columns) * (cellWidth + gap);
                double y = originY + (index / columns) * (cellHeight + gap);
                UIButton button = entry.getValue();
                Bounds authored = authoredBoundsOf(button.getName());
                place(button, new Bounds(x, y, cellWidth, cellHeight),
                        viewWidth, viewHeight, margin);

                UIComponent icon = commandIcons.get(entry.getKey());
                Bounds iconAuthored = icon == null ? null : authoredBoundsOf(icon.getName());
                if (icon != null && authored != null && iconAuthored != null) {
                    // O icone conserva o deslocamento relativo com que foi autorado dentro
                    // do botao, encolhendo junto quando a celula encolhe.
                    double ratioX = (iconAuthored.x - authored.x) / authored.width;
                    double ratioY = (iconAuthored.y - authored.y) / authored.height;
                    double iconWidth = iconAuthored.width * (button.getWidth() / authored.width);
                    double iconHeight = iconAuthored.height * (button.getHeight() / authored.height);
                    place(icon, new Bounds(
                                    button.getX() + ratioX * button.getWidth(),
                                    button.getY() + ratioY * button.getHeight(),
                                    iconWidth, iconHeight),
                            viewWidth, viewHeight, margin);
                }
                index++;
            }
        }

        private void layoutFullScreen(String widgetName, double viewWidth, double viewHeight) {
            UIComponent widget = canvas.findByName(widgetName);
            if (widget == null) return;
            widget.setPosition(0, 0);
            widget.setSize(viewWidth, viewHeight);
        }

        private void layoutTopCenter(String widgetName, double scale,
                double viewWidth, double margin) {
            UIComponent widget = canvas.findByName(widgetName);
            Bounds authored = authoredBoundsOf(widgetName);
            if (widget == null || authored == null) return;
            double width = authored.width * scale;
            double height = authored.height * scale;
            widget.setSize(width, height);
            widget.setPosition(Math.max(margin, (viewWidth - width) / 2.0), margin);
        }

        @Override
        public void show() {
            canvasObject.setVisible(true);
            applyLayout();
            setRole("dimmer", true);
            for (UIComponent widget : widgets.values()) {
                widget.setVisible(true);
            }
            for (UIButton button : commandButtons.values()) {
                button.setVisible(true);
            }
            for (UIComponent icon : commandIcons.values()) {
                icon.setVisible(true);
            }
            setRole("reactionPrompt", false);
            setRole("reactionResult", false);
            setRole("resultPanel", false);
            setRetryVisible(false);
            focusFirstCommand();
        }

        /**
         * Some o duelo devolvendo a cena ao estado autorado. Nao basta apagar os widgets:
         * eles sao persistidos, entao ocultar sem restaurar deixaria bounds, texto e
         * enabled do duelo prontos para o proximo autosave gravar no projeto. Quem
         * esconde o HUD e o objeto pai, que ja nasce oculto por contrato.
         */
        @Override
        public void hide() {
            restoreAuthoredState();
            if (canvasObject != null) {
                canvasObject.setVisible(false);
            }
        }

        /** Retry so existe visualmente onde ele e valido; fora disso nem clicavel fica. */
        private void setRetryVisible(boolean visible) {
            if (retryButton == null) return;
            retryButton.setVisible(visible);
            retryButton.setEnabled(visible);
        }

        @Override
        public void render(HudSnapshot snap) {
            setBar("hpBar", snap.leaderHp, snap.leaderMaxHp);
            setBar("magiculesBar", snap.magicules, snap.maxMagicules);
            setBar("moraleBar", snap.morale, snap.maxMorale);
            setText("phaseBadge", snap.phaseTitle + "  -  Turno " + snap.turn
                    + "  -  " + snap.timingLabel);
            setTextFitted("intentionLabel", snap.intentionName
                    + (snap.intentionTelegraphed ? "  [!]" : "")
                    + "\n" + snap.intentionHint
                    + (snap.analyzed ? "\n" + String.join(" | ", snap.revealed) : ""));
            setTextFitted("statusLine", snap.statusMessage);
            setSprite("intentionIcon", snap.intentionShape);

            boolean interactive = snap.mode == Mode.COMMAND;
            for (Map.Entry<String, UIButton> entry : commandButtons.entrySet()) {
                boolean enabled = Boolean.TRUE.equals(snap.commandEnabled.get(entry.getKey()));
                entry.getValue().setEnabled(interactive && enabled);
            }

            setRole("reactionTrack", snap.reacting);
            setRole("reactionMarker", snap.reacting);
            setRole("reactionPrompt", snap.reacting);
            if (snap.reacting) {
                setText("reactionPrompt", snap.reactionPrompt);
                UIComponent track = widgets.get("reactionTrack");
                UIComponent marker = widgets.get("reactionMarker");
                if (track != null && marker != null) {
                    marker.setPosition(
                            track.getX() + snap.reactionProgress * (track.getWidth() - marker.getWidth()),
                            track.getY());
                }
            }

            UIComponent result = widgets.get("reactionResult");
            if (result != null) {
                result.setVisible(snap.grade != null);
                if (snap.grade != null) {
                    gradeBaseX = result.getX();
                    gradeBaseY = result.getY();
                    setText("reactionResult", snap.grade.badge());
                    result.setTextColor(snap.grade.color());
                }
            }

            boolean finished = snap.mode == Mode.RESULT;
            setRole("resultPanel", finished);
            setRetryVisible(finished && snap.retryOffered);
            if (finished) {
                setTextFitted("statusLine", snap.outcomeText);
            }
        }

        /**
         * Escreve texto ja quebrado e truncado para a caixa do widget. {@code UILabel} so
         * quebra em {@code \n} — sem isto, uma copy mais longa (a analise do Grande Sabio,
         * o texto de rendicao) atravessa o painel, que foi o que o QA viu.
         */
        private void setTextFitted(String role, String text) {
            UIComponent widget = widgets.get(role);
            if (widget == null) return;
            if (widget instanceof UILabel label) {
                label.setMultiline(true);
                label.setText(fitToBox(text, label));
                return;
            }
            setText(role, text);
        }

        @Override
        public void animate(double deltaTime) {
            // Um resize do editor ou da janela muda o viewport vivo; o layout volta a ser
            // calculado a partir do baseline autorado, nunca do estado ja deslocado.
            if (viewportWidth() != appliedViewportWidth || viewportHeight() != appliedViewportHeight) {
                applyLayout();
            }
            UIComponent result = widgets.get("reactionResult");
            if (result == null || !result.isVisible() || shownGrade == null) return;
            Motion motion = shownGrade.motion();
            result.setPosition(
                    gradeBaseX + motion.dx(gradeElapsed),
                    gradeBaseY + motion.dy(gradeElapsed));
        }

        private void focusFirstCommand() {
            JSONObject accessibility = visuals.optJSONObject("accessibility");
            JSONArray order = accessibility == null ? null : accessibility.optJSONArray("focusOrder");
            if (order == null || order.isEmpty() || canvas == null) return;
            UIComponent first = canvas.findByName(order.getString(0));
            if (first != null) {
                canvas.setFocus(first);
            }
        }

        private void setRole(String role, boolean visible) {
            UIComponent widget = widgets.get(role);
            if (widget != null) {
                widget.setVisible(visible);
            }
        }

        private void setText(String role, String text) {
            UIComponent widget = widgets.get(role);
            if (widget instanceof UILabel label) {
                label.setText(text);
            } else if (widget instanceof UIButton button) {
                button.setText(text);
            }
        }

        private void setBar(String role, int value, int max) {
            UIComponent widget = widgets.get(role);
            if (widget instanceof UIProgressBar bar) {
                bar.setValue(value, Math.max(1, max));
            } else if (widget instanceof UILabel label) {
                label.setText(bar(value, max));
            }
        }

        private void setSprite(String role, String intentionKey) {
            UIComponent widget = widgets.get(role);
            if (!(widget instanceof UIImage image)) return;
            String sprite = intentionKey == null ? null : intentionSprites.get(intentionKey);
            image.setVisible(sprite != null);
            if (sprite != null) {
                image.setImagePath(sprite);
            }
        }

        @Override
        public void dispose() {
            // O canvas e da cena: este presenter so devolve os widgets ao estado oculto,
            // nunca remove nem recria objeto persistente.
            hide();
            widgets.clear();
            commandButtons.clear();
            commandIcons.clear();
            anchorOfWidget.clear();
            retryButton = null;
        }
    }

    /**
     * Fotografia de tudo que o presenter escreve num widget PERSISTIDO da cena. Existe
     * porque esses widgets nao sao descartaveis: o autosave grava no `.ignis` o que
     * estiver neles quando o editor salvar, entao o duelo tem de devolver cada
     * propriedade que tocou.
     */
    private static final class WidgetState {

        private final double x;
        private final double y;
        private final double width;
        private final double height;
        private final boolean visible;
        private final boolean enabled;
        private final String text;
        private final Color textColor;
        private final String imagePath;
        private final float progressValue;
        private final float progressMax;
        private final Boolean multiline;

        private WidgetState(UIComponent widget) {
            this.x = widget.getX();
            this.y = widget.getY();
            this.width = widget.getWidth();
            this.height = widget.getHeight();
            this.visible = widget.isVisible();
            this.enabled = widget.isEnabled();
            this.textColor = widget.getTextColor();
            // multiline entra aqui porque setTextFitted() o liga para quebrar a copy —
            // e ele e serializado, entao ligar sem devolver contamina o autosave.
            this.multiline = widget instanceof UILabel label ? label.isMultiline() : null;
            if (widget instanceof UILabel label) {
                this.text = label.getText();
            } else if (widget instanceof UIButton button) {
                this.text = button.getText();
            } else {
                this.text = null;
            }
            this.imagePath = widget instanceof UIImage image ? image.getImagePath() : null;
            if (widget instanceof UIProgressBar bar) {
                this.progressValue = bar.getCurrentValue();
                this.progressMax = bar.getMaxValue();
            } else {
                this.progressValue = Float.NaN;
                this.progressMax = Float.NaN;
            }
        }

        static WidgetState capture(UIComponent widget) {
            return new WidgetState(widget);
        }

        void restore(UIComponent widget) {
            widget.setPosition(x, y);
            widget.setSize(width, height);
            widget.setVisible(visible);
            widget.setEnabled(enabled);
            widget.setTextColor(textColor);
            if (multiline != null && widget instanceof UILabel wrapped) {
                wrapped.setMultiline(multiline);
            }
            if (text != null && widget instanceof UILabel label) {
                label.setText(text);
            } else if (text != null && widget instanceof UIButton button) {
                button.setText(text);
            }
            if (imagePath != null && widget instanceof UIImage image) {
                image.setImagePath(imagePath);
            }
            if (!Float.isNaN(progressValue) && widget instanceof UIProgressBar bar) {
                bar.setValue(progressValue, progressMax);
            }
        }

        Bounds bounds() {
            return new Bounds(x, y, width, height);
        }
    }

    /** Retangulo simples de layout; existe para o remapeamento entre ancoras ser legivel. */
    private static final class Bounds {
        private final double x;
        private final double y;
        private final double width;
        private final double height;

        Bounds(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        /**
         * Reposiciona este retangulo preservando sua posicao e proporcao RELATIVAS
         * dentro da ancora: o que estava no meio do painel continua no meio dele.
         */
        Bounds remapped(Bounds from, Bounds to) {
            if (from.width <= 0 || from.height <= 0) {
                return this;
            }
            double scaleX = to.width / from.width;
            double scaleY = to.height / from.height;
            return new Bounds(
                    to.x + (x - from.x) * scaleX,
                    to.y + (y - from.y) * scaleY,
                    width * scaleX,
                    height * scaleY);
        }
    }
}
