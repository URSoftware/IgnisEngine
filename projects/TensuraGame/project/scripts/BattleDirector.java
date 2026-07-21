import com.ignis.core.AssetResolver;
import com.ignis.core.IgnisScript;
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
 * dominio. Este script so traduz snapshot -> HUD de texto e input -> comando de
 * dominio. Nenhum "if" aqui decide o resultado da batalha; ele apenas apresenta.
 *
 * <p>Coordenacao entre scripts por sinais de cena (nunca referencia direta —
 * scripts do Ignis compilam isoladamente). Cada nome de sinal tem o par exato em
 * GameFlowController quando a fatia da aldeia estiver ligada; por ora o duelo e
 * alcancavel por uma entrada de depuracao (F10), espelhando o HordeEncounter (F9),
 * sem tocar no fluxo Caverna -> Floresta que ja funciona.
 *
 * <p>Assets de apresentacao final (retratos de batalha, telegraphs animados,
 * cutscene de rendicao/nomeacao) sao do Codex e ainda nao existem; a resolucao
 * usa um painel de texto e emite TENSURA_DIRE_WOLF_DUEL_COMPLETE. A persistencia
 * do desfecho (marco semantico) e a cutscene de nomeacao ficam com quem recebe
 * esse sinal (GameFlowController), preservando a fronteira de responsabilidade.
 */
public final class BattleDirector extends IgnisScript {

    private static final String SIGNAL_ENTER_DUEL = "TENSURA_ENTER_DIRE_WOLF_DUEL";
    private static final String SIGNAL_DUEL_COMPLETE = "TENSURA_DIRE_WOLF_DUEL_COMPLETE";

    private static final String BATTLE_DATA = "data/battle-dire-wolf-leader.json";
    // Semente fixa: duelo deterministico e reproduzivel ("semente X, turno Y").
    // Quando o duelo for disparado pela narrativa, a semente vira payload do sinal.
    private static final long DEFAULT_SEED = 20260721L;
    // Preparacoes padrao da entrada de depuracao; a narrativa passara as 2 escolhas
    // reais da aldeia no payload de TENSURA_ENTER_DIRE_WOLF_DUEL.
    private static final Set<VillagePreparation> DEFAULT_PREPARATIONS = Set.of(
            VillagePreparation.REINFORCE_ENTRANCE, VillagePreparation.LIGHT_FLANK);

    private JSONObject config;
    private ReactionWindow reactionWindow;
    private double telegraphSeconds;
    private final Map<String, BattleCommand> keyToCommand = new LinkedHashMap<>();
    private final Map<String, String> commandLabel = new LinkedHashMap<>();
    private final Map<String, String> commandKey = new LinkedHashMap<>();
    private final Map<String, String> commandHint = new LinkedHashMap<>();
    private final Map<String, String> outcomeText = new LinkedHashMap<>();
    private final Map<String, String> gradeLabel = new LinkedHashMap<>();

    private BattleSimulation battle;
    private Mode mode = Mode.INACTIVE;
    private String statusMessage = "";
    private double reactionElapsed;
    private boolean uiReady;
    private boolean completionDelivered;

    private UIPanel shade;
    private UIPanel statusPanel;
    private UIPanel commandPanel;
    private UILabel titleLabel;
    private UILabel vitalsLabel;
    private UILabel intentionLabel;
    private UILabel commandsLabel;
    private UILabel messageLabel;
    private UILabel reactionLabel;

    @Override
    public void start() {
        config = readJson(BATTLE_DATA);
        parseConfig(config);
        onSceneSignal(SIGNAL_ENTER_DUEL, this::beginDuelFromSignal);
    }

    @Override
    public void tick() {
        if (mode == Mode.INACTIVE) {
            if (isKeyJustPressed("F10")) {
                beginDuel(DEFAULT_PREPARATIONS);
            }
            return;
        }
        switch (mode) {
            case COMMAND -> tickCommand();
            case REACTION -> tickReaction();
            case RESULT -> tickResult();
            default -> { }
        }
        layoutUi();
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

    // ==================== Ciclo de comando ====================

    private void tickCommand() {
        for (Map.Entry<String, BattleCommand> entry : keyToCommand.entrySet()) {
            if (isKeyJustPressed(entry.getKey())) {
                chooseCommand(entry.getValue());
                return;
            }
        }
    }

    private void chooseCommand(BattleCommand command) {
        // DEFEND sempre abre a janela reativa: a leitura de tempo pertence ao
        // dominio (ReactionWindow), este script so mede o instante da tecla.
        if (command == BattleCommand.DEFEND) {
            reactionElapsed = 0;
            mode = Mode.REACTION;
            statusMessage = config.getJSONObject("reaction").getString("telegraph");
            refreshHud();
            return;
        }
        applyCommand(command, ReactionTiming.NONE, null);
    }

    private void tickReaction() {
        reactionElapsed += getDeltaTime();
        if (isKeyJustPressed("SPACE")) {
            double offset = reactionElapsed - telegraphSeconds;
            ReactionTiming timing = reactionWindow.classify(offset);
            applyCommand(BattleCommand.DEFEND, timing, timing.name());
            return;
        }
        // Sem reacao dentro da janela ativa: conta como golpe cheio (NONE).
        if (reactionElapsed > telegraphSeconds + reactionWindow.activeRadius()) {
            applyCommand(BattleCommand.DEFEND, ReactionTiming.NONE, ReactionTiming.NONE.name());
        }
    }

    private void applyCommand(BattleCommand command, ReactionTiming timing, String reactionGrade) {
        BattleActionResult result = battle.executeCommand(command, timing);
        StringBuilder message = new StringBuilder(result.message());
        if (reactionGrade != null) {
            message.append("  (Reacao: ")
                    .append(gradeLabel.getOrDefault(reactionGrade, reactionGrade))
                    .append(')');
        }
        statusMessage = message.toString();
        refreshHud();

        if (battle.outcome() != BattleOutcome.IN_PROGRESS) {
            mode = Mode.RESULT;
            return;
        }
        mode = Mode.COMMAND;
    }

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

        layoutUi();
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
        if (!visible) {
            reactionLabel.setVisible(false);
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
        REACTION,
        RESULT
    }
}
