import com.ignis.animation.AnimationIO;
import com.ignis.animation.Animator;
import com.ignis.animation.SpriteAnimation;
import com.ignis.core.AssetResolver;
import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UIImage;
import com.ignis.core.ui.UIButton;
import com.ignis.core.ui.UILabel;
import com.ignis.core.ui.UIPanel;
import com.ignis.core.ui.UIProgressBar;
import com.rimurusurvivors.domain.RimuruForm;
import com.rimurusurvivors.domain.RunEvent;
import com.rimurusurvivors.domain.RunEventType;
import com.rimurusurvivors.domain.RunInput;
import com.rimurusurvivors.domain.RunSimulation;
import com.rimurusurvivors.domain.RunSnapshot;
import com.rimurusurvivors.domain.UpgradeChoice;
import com.rimurusurvivors.domain.WeaponLevelStats;
import com.rimurusurvivors.domain.WeaponProgression;
import com.rimurusurvivors.domain.WorldEntityKind;
import com.rimurusurvivors.domain.WorldEntitySnapshot;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Adaptador entre a simulacao pura do jogo e os recursos de runtime da Ignis. */
public final class TensuraGameController extends IgnisScript {

    private static final String SPRITES = "assets/sprites/";
    private static final String SOUNDS = "assets/sounds/";
    // Icones simbolicos de INTERFACE (HUD, upgrade, painel, codex). Vivem na mesma
    // pasta dos sprites, mas o alias deixa explicito que nao sao arte de arena:
    // liga-los a entidades de mundo ja deu errado uma vez (16/07/2026).
    private static final String ICONS = "assets/sprites/";

    private final Map<Long, GameObject> visuals = new HashMap<>();

    // ---- Estabilidade de sessao longa (16/07/2026) ----
    // Pool de visuais: um orbe coletado devolve o GameObject aqui em vez de destruir,
    // e o proximo spawn o reusa. Sem isso, cada orbe nascia por instantiatePrefab e
    // morria por destroy — churn constante que aparecia como milhares de linhas de
    // "Prefab RuntimeVisual instanciada" no log da sessao que estourou a memoria.
    private final java.util.Deque<GameObject> visualPool = new java.util.ArrayDeque<>();
    private static final int MAX_POOLED_VISUALS = 256;

    // Coleções de trabalho reusadas: syncWorld roda 60x/s e alocava um HashSet e uma
    // lista de stream() a cada tick.
    private final Set<Long> activeIds = new HashSet<>();
    private final List<Long> staleIds = new ArrayList<>();

    // ---- Apresentacao: mapeamento evento/estado -> clipe (contrato do Codex em
    // 02_design/rimuru-survivors-direcao-visual-fatia-slime.md).
    // Este e o UNICO lugar que sabe nomes de clipe. O dominio so diz o que aconteceu
    // (PREDATOR_CAST, RANGA_ATTACK, playerMoving...) e nunca cita arte ou audio.
    private static final String CLIP_SLIME_IDLE = "rimuru_slime_idle";
    private static final String CLIP_SLIME_MOVE = "rimuru_slime_move";
    private static final String CLIP_SLIME_HIT = "rimuru_slime_hit";
    private static final String CLIP_SLIME_PREDATOR_CAST = "rimuru_slime_predator_cast";
    private static final String CLIP_HUMANOID_RUN = "rimuru_humanoid_run";
    private static final String CLIP_DEMON_LORD_RUN = "rimuru_demon_lord_run";
    private static final String CLIP_RANGA_RUN = "ranga_run";
    private static final String CLIP_RANGA_SUMMON = "ranga_summon";
    private static final String CLIP_RANGA_ATTACK = "ranga_attack";

    // Id do Ranga no snapshot do dominio (entidade unica, sempre a mesma).
    private static final long RANGA_ID = 0L;

    // Clipes carregados uma vez e compartilhados entre objetos: SpriteAnimation nao
    // guarda estado de playback (elapsed/currentName vivem no Animator), entao a
    // mesma instancia pode servir o Rimuru e o Ranga sem interferencia.
    private final Map<String, SpriteAnimation> clips = new HashMap<>();

    private RunSimulation simulation;
    private RunSnapshot snapshot;
    private GameObject player;
    private RimuruForm lastForm;
    private double messageTimer;
    private double sfxThrottle;
    private ScreenState screenState = ScreenState.MENU;

    private UIProgressBar healthBar;
    private UIProgressBar experienceBar;
    private UILabel statsLabel;
    private UILabel formLabel;
    private UILabel objectiveLabel;
    private UILabel messageLabel;
    private UIImage weaponIcon;
    private UIImage skillIcon;
    private UIImage rangaIcon;
    private UIPanel leftPanel;
    private UIPanel formPanel;
    private UIPanel upgradePanel;
    private UILabel upgradeTitle;
    private UIButton predatorButton;
    private UIButton sageButton;
    private UIButton regenerationButton;
    private UIPanel menuBackground;
    private UILabel menuTitle;
    private UILabel menuSubtitle;
    private UIImage menuRimuru;
    private UIButton startButton;
    private UIPanel pausePanel;
    private UILabel pauseTitle;
    private UIButton resumeButton;
    private UIButton pauseRestartButton;
    private UIButton pauseButton;
    private UIPanel resultPanel;
    private UILabel resultTitle;
    private UILabel resultStats;
    private UIButton resultRestartButton;

    @Override
    public void start() {
        getGameObject().setVisible(false);
        setCameraZoom(1.22);
        setMusicVolume(0.34f);
        setSfxVolume(0.62f);
        playMusic("assets/music/tempest_forest_theme.wav", true);
        showMainMenu();
        log("TensuraGame pronto na tela inicial.");
    }

    @Override
    public void tick() {
        if (screenState == ScreenState.MENU) {
            layoutMenu();
            if (isKeyJustPressed("ENTER")) beginRun();
            return;
        }
        if (screenState == ScreenState.PAUSED) {
            layoutPauseOverlay();
            if (isKeyJustPressed("ESCAPE") || isKeyJustPressed("P")) resumeRun();
            return;
        }
        if (screenState == ScreenState.RESULT) {
            layoutResultOverlay();
            return;
        }
        if (simulation == null) return;
        consumeDebugUnlockFlag();
        if (isKeyJustPressed("ESCAPE") || isKeyJustPressed("P")) {
            pauseRun();
            return;
        }
        if (snapshot.pendingUpgrades() > 0) {
            if (isKeyJustPressed("1")) chooseUpgrade(UpgradeChoice.PREDATOR);
            if (isKeyJustPressed("2")) chooseUpgrade(UpgradeChoice.GREAT_SAGE);
            if (isKeyJustPressed("3")) chooseUpgrade(UpgradeChoice.REGENERATION);
        }
        if (isKeyJustPressed("0")) unlockDebugEvolution();
        if (isKeyJustPressed("F8")) simulation.grantExperience(4_000);

        snapshot = simulation.update(getDeltaTime(),
                // fromScreenAxes converte o eixo de TELA da engine (cima = -1) para o
                // mundo Y-para-cima da simulacao. Sem essa conversao, W/Seta-cima
                // moviam o Rimuru para baixo.
                RunInput.fromScreenAxes(getHorizontalAxis(), getVerticalAxis()));
        syncWorld();
        syncHud();
        processEvents(snapshot.events());
        cameraFollow(player, 0.16);
        sfxThrottle = Math.max(0, sfxThrottle - getDeltaTime());
        messageTimer = Math.max(0, messageTimer - getDeltaTime());
        messageLabel.setVisible(messageTimer > 0);
        if (snapshot.gameOver() || snapshot.victory()) showResult();
    }

    private void beginRun() {
        clearUI();
        for (GameObject visual : new ArrayList<>(visuals.values())) destroy(visual);
        visuals.clear();
        if (player != null) destroy(player);
        simulation = new RunSimulation(loadWeaponProgression());
        snapshot = simulation.snapshot();
        player = spawnRuntimeVisual("Rimuru", 0, 0);
        player.setZIndex(30);
        // Todos os clipes do jogador de uma vez (inclusive os das formas seguintes):
        // trocar de forma passa a ser so escolher outro clipe pelo nome.
        attachClips(player, CLIP_SLIME_IDLE, CLIP_SLIME_MOVE, CLIP_SLIME_HIT,
                CLIP_SLIME_PREDATOR_CAST, CLIP_HUMANOID_RUN, CLIP_DEMON_LORD_RUN);
        lastForm = null;
        createHud();
        createUpgradeOverlay();
        createPauseOverlay();
        createResultOverlay();
        screenState = ScreenState.PLAYING;
        showMessage("Grande Sabio: iniciando analise da Floresta de Jura", 4.0);
        syncWorld();
        syncHud();
    }

    private void showMainMenu() {
        clearUI();
        screenState = ScreenState.MENU;
        menuBackground = createPanel(0, 0, 960, 540);
        setUIColors(menuBackground, new Color(4, 18, 24, 230), null, new Color(40, 125, 142));
        menuTitle = createLabel("RIMURU SURVIVORS", 0, 0, 620, 72);
        menuTitle.setAlignment(UILabel.Alignment.CENTER);
        menuTitle.setFont("SansSerif", Font.BOLD, 42);
        menuTitle.setTextColor(new Color(216, 250, 255));
        menuTitle.setOutline(true, new Color(9, 47, 61), 2);
        menuSubtitle = createLabel("ECOS DA FLORESTA DE JURA", 0, 0, 460, 36);
        menuSubtitle.setAlignment(UILabel.Alignment.CENTER);
        menuSubtitle.setFont("SansSerif", Font.BOLD, 16);
        menuSubtitle.setTextColor(new Color(255, 222, 137));
        menuRimuru = createImage(SPRITES + "rimuru_slime_01.png", 0, 0, 160, 140);
        startButton = createButton("INICIAR", 0, 0, 240, 58);
        startButton.setColorScheme(new Color(35, 147, 168), new Color(50, 177, 198), new Color(24, 112, 132));
        startButton.setBorderColor(new Color(190, 244, 249));
        startButton.setBorderWidth(2);
        startButton.setOnClick(this::beginRun);
        layoutMenu();
    }

    private void syncWorld() {
        syncPlayer();
        activeIds.clear();
        for (WorldEntitySnapshot entity : snapshot.entities()) {
            activeIds.add(entity.id());
            // get/put em vez de computeIfAbsent: a lambda capturava 'entity', entao
            // alocava um objeto por entidade por tick mesmo quando o visual ja existia.
            GameObject visual = visuals.get(entity.id());
            if (visual == null) {
                visual = spawnRuntimeVisual(entity.kind().name(), entity.x(), entity.y());
                visuals.put(entity.id(), visual);
                if (entity.kind() == WorldEntityKind.RANGA) {
                    attachClips(visual, CLIP_RANGA_RUN, CLIP_RANGA_SUMMON, CLIP_RANGA_ATTACK);
                }
            }
            VisualSpec spec = specFor(entity.kind());
            visual.setWidth(spec.width());
            visual.setHeight(spec.height());
            visual.setX(entity.x() - spec.width() / 2.0);
            visual.setY(entity.y() - spec.height() / 2.0);
            visual.setRotation(entity.rotation());
            visual.setZIndex(spec.zIndex());
            if (entity.kind() == WorldEntityKind.RANGA && clip(CLIP_RANGA_RUN) != null) {
                // Ranga tem clipes proprios: quem escreve o sprite dele e o Animator.
                driveLocomotion(visual, CLIP_RANGA_RUN);
            } else {
                // Demais entidades — e o Ranga caso o clipe ainda nao exista, para um
                // asset em producao nunca deixar o lobo sem sprite na tela.
                setSpriteIfChanged(visual, spec.sprite(snapshot.elapsedSeconds()));
            }
        }

        // Sem stream()/toList() por tick: itera o keySet e reusa a lista de trabalho.
        staleIds.clear();
        for (Long id : visuals.keySet()) {
            if (!activeIds.contains(id)) staleIds.add(id);
        }
        for (Long id : staleIds) recycle(visuals.remove(id));
    }

    private void syncPlayer() {
        if (player == null) return;
        int width = snapshot.form() == RimuruForm.SLIME ? 48 : 46;
        int height = snapshot.form() == RimuruForm.SLIME ? 42 : 62;
        player.setWidth(width);
        player.setHeight(height);
        player.setX(snapshot.playerX() - width / 2.0);
        player.setY(snapshot.playerY() - height / 2.0);
        // O sprite do jogador agora vem do Animator (o motor aplica o quadro em
        // GameObject.tickAnimator). O script NAO escreve mais spritePath aqui: os
        // dois brigariam pelo mesmo campo a cada frame.
        String clipName = locomotionClipFor(snapshot.form(), snapshot.playerMoving());
        if (clip(clipName) != null) {
            driveLocomotion(player, clipName);
        } else {
            // Clipe ainda nao entregue: pose estatica. Sem isto, uma forma sem clipe
            // deixaria o Rimuru literalmente sem sprite na tela.
            setSpriteIfChanged(player, fallbackPlayerSprite(snapshot.form()));
        }
        if (lastForm != snapshot.form()) {
            lastForm = snapshot.form();
            player.setOpacity(1.0);
        }
    }


    private GameObject spawnRuntimeVisual(String name, double x, double y) {
        // 1) Reusa um visual aposentado antes de criar qualquer coisa.
        GameObject object = visualPool.poll();
        if (object == null) {
            object = getGame().instantiatePrefab("RuntimeVisual", x, y);
            if (object == null) {
                object = new GameObject(name, getGame(), x, y, 32, 32);
                getGame().addEntity(object);
            }
        } else {
            object.setX(x);
            object.setY(y);
        }
        object.setName(name);
        object.setTag("tensura_runtime");
        object.setVisible(true);
        object.setOpacity(1.0);
        return object;
    }

    /**
     * Aposenta um visual: some da tela e volta para o pool, pronto para o proximo
     * spawn. Acima do teto o objeto e destruido de fato — o pool nao pode virar um
     * vazamento com outro nome numa run muito longa.
     */
    private void recycle(GameObject visual) {
        if (visual == null) return;
        // Zera o Animator antes de devolver ao pool: o visual do Ranga tem clipes
        // anexados e, sem isto, o proximo spawn (um orbe, por exemplo) herdaria a
        // animacao de lobo e o Animator continuaria escrevendo o sprite dele.
        visual.setAnimator(null);
        if (visualPool.size() >= MAX_POOLED_VISUALS) {
            destroy(visual);
            return;
        }
        visual.setVisible(false);
        visualPool.push(visual);
    }

    private void setSpriteIfChanged(GameObject object, String path) {
        if (!path.equals(object.getSpritePath())) object.setSpritePath(path);
    }

    // ==================== Apresentacao (clipes) ====================

    /** Clipe do disco, carregado uma vez. Null quando o asset ainda nao existe. */
    private SpriteAnimation clip(String name) {
        if (clips.containsKey(name)) return clips.get(name);
        SpriteAnimation loaded = null;
        try {
            File file = AssetResolver.resolve("assets/animations/" + name + ".anim.json");
            if (file != null && file.exists()) loaded = AnimationIO.load(file);
        } catch (IOException exception) {
            loaded = null; // clipe ausente nao pode derrubar a partida
        }
        clips.put(name, loaded);
        return loaded;
    }

    /** Anexa os clipes existentes ao Animator do objeto (ignora os que faltarem). */
    private void attachClips(GameObject object, String... names) {
        if (object == null) return;
        Animator animator = object.getOrCreateAnimator();
        for (String name : names) {
            SpriteAnimation animation = clip(name);
            if (animation != null) animator.addAnimation(animation);
        }
    }

    /**
     * Seleciona a locomocao (repouso x deslocamento) sem cortar um clipe de acao.
     *
     * <p>Chamar play() num clipe em LOOP que ja e o atual o reiniciaria a cada tick
     * (elapsed = 0), congelando a animacao no primeiro quadro — por isso so agimos
     * quando o clipe desejado muda. Com waitForCurrent, se um one-shot (dano,
     * Predador) estiver rodando, a locomocao entra na fila e assume quando ele
     * terminar: o contrato pede que clipes de ataque nao substituam a locomocao
     * permanentemente.</p>
     */
    private void driveLocomotion(GameObject object, String clipName) {
        if (object == null || clipName == null) return;
        Animator animator = object.getOrCreateAnimator();
        if (animator.getAnimation(clipName) == null) return;
        if (!clipName.equals(animator.getCurrentName())) {
            animator.play(clipName, true);
        }
    }

    /** Dispara um clipe de acao imediatamente; a locomocao volta sozinha depois. */
    private void playOneShot(GameObject object, String clipName) {
        if (object == null) return;
        Animator animator = object.getOrCreateAnimator();
        // Forma sem o clipe (a fatia atual so tem acoes de Slime): sem acao visual,
        // mas som e dano seguem normais.
        if (animator.getAnimation(clipName) == null) return;
        animator.play(clipName);
    }

    /** Pose fixa por forma, usada so quando o clipe de locomocao nao existe. */
    private String fallbackPlayerSprite(RimuruForm form) {
        return switch (form) {
            case SLIME -> SPRITES + "rimuru_slime_01.png";
            case HUMANOID -> SPRITES + "rimuru_humanoid_01.png";
            case DEMON_LORD -> SPRITES + "rimuru_demon_lord_01.png";
        };
    }

    private String locomotionClipFor(RimuruForm form, boolean moving) {
        return switch (form) {
            case SLIME -> moving ? CLIP_SLIME_MOVE : CLIP_SLIME_IDLE;
            // Humanoid/Demon Lord ainda nao tem clipe de repouso proprio: o de corrida
            // cobre os dois estados ate o Codex entregar a fatia dessas formas.
            case HUMANOID -> CLIP_HUMANOID_RUN;
            case DEMON_LORD -> CLIP_DEMON_LORD_RUN;
        };
    }

    // VisualSpec e imutavel, mas specFor() montava uma instancia nova (com
    // concatenacao de string junto) para CADA entidade a CADA tick — 60x/s por
    // entidade, puro lixo para o GC. Pre-computados uma unica vez.
    private static final Map<WorldEntityKind, VisualSpec> SPECS = buildSpecs();

    // KATANA_CUT depende da forma atual do Rimuru: as duas variantes prontas.
    private static final VisualSpec KATANA_DEMON_LORD =
            new VisualSpec(SPRITES + "beelzebuth_blade.png", 42, 22, 20);
    private static final VisualSpec KATANA_PREDATOR =
            new VisualSpec(SPRITES + "predator_katana.png", 42, 22, 20);

    private static Map<WorldEntityKind, VisualSpec> buildSpecs() {
        Map<WorldEntityKind, VisualSpec> specs = new java.util.EnumMap<>(WorldEntityKind.class);
        specs.put(WorldEntityKind.GOBLIN, new VisualSpec(SPRITES + "goblin_scout.png", 32, 32, 10));
        specs.put(WorldEntityKind.ORC, new VisualSpec(SPRITES + "orc_warrior.png", 42, 42, 10));
        specs.put(WorldEntityKind.DIRE_WOLF, new VisualSpec(SPRITES + "dire_wolf.png", 40, 30, 10));
        specs.put(WorldEntityKind.FLAME_SPIRIT, new VisualSpec(SPRITES + "flame_spirit.png", 38, 44, 11));
        specs.put(WorldEntityKind.RED_REAPER, new VisualSpec(SPRITES + "red_reaper.png", 78, 78, 15));
        specs.put(WorldEntityKind.MAGICULE_ORB, new VisualSpec(SPRITES + "magicule_orb.png", 14, 14, 5));
        // ATENCAO: os ability_*.png sao ICONES SIMBOLICOS de interface (HUD, painel de
        // habilidades, upgrade, codex) — nao sao VFX nem sprites de ataque. Chegaram a
        // ser ligados aqui em 16/07/2026 e a direcao visual corrigiu: entidade de
        // mundo continua com estes sprites ate os VFX animados proprios existirem.
        // O formato ja denunciava: os ability_* sao 128x128 quadrados (canvas de
        // icone), enquanto o projetil e achatado.
        specs.put(WorldEntityKind.WATER_BLADE, new VisualSpec(SPRITES + "water_blade.png", 32, 14, 20));
        specs.put(WorldEntityKind.BLACK_LIGHTNING, new VisualSpec(SPRITES + "black_lightning.png", 42, 14, 21));
        specs.put(WorldEntityKind.PREDATOR_MAW, new VisualSpec(SPRITES + "predator_maw.png", 110, 110, 18));
        specs.put(WorldEntityKind.VOID_CUT, new VisualSpec(SPRITES + "azathoth_void_blade.png", 52, 26, 22));
        specs.put(WorldEntityKind.RANGA, new AnimatedVisualSpec(SPRITES + "ranga_", 48, 38, 25, 0.10));
        return specs;
    }

    private VisualSpec specFor(WorldEntityKind kind) {
        if (kind == WorldEntityKind.KATANA_CUT) {
            return snapshot.form() == RimuruForm.DEMON_LORD ? KATANA_DEMON_LORD : KATANA_PREDATOR;
        }
        return SPECS.get(kind);
    }

    private void createHud() {
        clearUI();
        leftPanel = createPanel(18, 18, 310, 126);
        setUIColors(leftPanel, new Color(10, 22, 29, 220), null, new Color(76, 202, 214));

        UILabel title = createLabel("RIMURU TEMPEST", 30, 24, 280, 24);
        title.setFont("SansSerif", Font.BOLD, 18);
        title.setTextColor(new Color(221, 250, 255));
        title.setShadowEnabled(true);

        healthBar = createProgressBar(30, 54, 278, 18);
        healthBar.setFillColor(new Color(68, 205, 132));
        healthBar.setEmptyColor(new Color(28, 47, 50));
        healthBar.setTextMode(UIProgressBar.TextMode.VALUE_MAX);

        experienceBar = createProgressBar(30, 78, 278, 12);
        experienceBar.setFillColor(new Color(72, 195, 238));
        experienceBar.setEmptyColor(new Color(24, 39, 56));

        statsLabel = createLabel("", 30, 94, 278, 32);
        statsLabel.setFont("SansSerif", Font.BOLD, 13);
        statsLabel.setTextColor(new Color(220, 235, 238));

        formPanel = createPanel(0, 18, 360, 92);
        setUIColors(formPanel, new Color(17, 15, 31, 220), null, new Color(185, 103, 255));
        weaponIcon = createImage(ICONS + "ability_hydrolance.png", 0, 30, 56, 56);
        weaponIcon.setPreserveAspect(true);
        // Habilidade da forma atual e status do Ranga: icones de interface, ao lado
        // da arma no painel de forma. O do Ranga so aparece quando ele esta em campo.
        skillIcon = createImage(ICONS + "ability_predator.png", 0, 34, 48, 48);
        skillIcon.setPreserveAspect(true);
        rangaIcon = createImage(ICONS + "ability_ranga_storm.png", 0, 34, 48, 48);
        rangaIcon.setPreserveAspect(true);
        rangaIcon.setVisible(false);
        formLabel = createLabel("", 0, 24, 280, 62);
        formLabel.setFont("SansSerif", Font.BOLD, 16);
        formLabel.setTextColor(new Color(242, 230, 255));
        formLabel.setMultiline(true);

        objectiveLabel = createLabel("SOBREVIVA E ANALISE A MORTE", 0, 22, 420, 30);
        objectiveLabel.setAlignment(UILabel.Alignment.CENTER);
        objectiveLabel.setFont("SansSerif", Font.BOLD, 15);
        objectiveLabel.setTextColor(new Color(255, 230, 147));
        objectiveLabel.setOutline(true, new Color(22, 14, 9), 1);

        messageLabel = createLabel("", 0, 0, 680, 54);
        messageLabel.setAlignment(UILabel.Alignment.CENTER);
        messageLabel.setFont("SansSerif", Font.BOLD, 20);
        messageLabel.setTextColor(new Color(224, 249, 255));
        messageLabel.setBackgroundColor(new Color(6, 17, 28, 210));
        messageLabel.setBorderColor(new Color(86, 210, 230));
        messageLabel.setBorderWidth(1);
        messageLabel.setVisible(false);

        pauseButton = createButton("II", 0, 0, 44, 36);
        pauseButton.setColorScheme(new Color(31, 57, 68), new Color(47, 82, 95), new Color(20, 42, 51));
        pauseButton.setBorderColor(new Color(130, 219, 228));
        pauseButton.setOnClick(this::pauseRun);
    }

    private void syncHud() {
        healthBar.setValue((float) snapshot.health(), (float) snapshot.maxHealth());
        experienceBar.setValue((float) snapshot.experience(), (float) snapshot.experienceToNextLevel());
        statsLabel.setText("Nv " + snapshot.level() + "  Arma " + snapshot.weaponLevel()
                + "/8  Sabio " + snapshot.passiveLevel() + "/5  Regen "
                + snapshot.regenerationLevel() + "  KOs " + snapshot.kills());
        formLabel.setText(formDisplayName() + "\n" + weaponDisplayName());
        weaponIcon.setImagePath(weaponIconPath());
        String skill = skillIconPath();
        skillIcon.setVisible(skill != null);
        if (skill != null) skillIcon.setImagePath(skill);
        // Ranga no HUD so enquanto ele esta em campo.
        rangaIcon.setVisible(snapshot.rangaSummoned());

        double width = Math.max(480, getGame().getWidth());
        double height = Math.max(360, getGame().getHeight());
        boolean compact = width < 900;
        if (compact) {
            formPanel.setSize(280, 92);
            formPanel.setPosition(width - 298, 18);
            weaponIcon.setPosition(width - 288, 34);
            formLabel.setSize(208, 62);
            formLabel.setPosition(width - 224, 24);
            objectiveLabel.setSize(320, 30);
            objectiveLabel.setPosition((width - 320) / 2.0, 154);
        } else {
            formPanel.setSize(360, 92);
            formPanel.setPosition(width - 378, 18);
            weaponIcon.setPosition(width - 366, 34);
            formLabel.setSize(280, 62);
            formLabel.setPosition(width - 298, 24);
            objectiveLabel.setSize(420, 30);
            objectiveLabel.setPosition((width - 420) / 2.0, 18);
        }
        // Icones de habilidade/Ranga numa fileira logo abaixo do painel de forma
        // (que termina em y=110), alinhados a direita como ele.
        skillIcon.setPosition(width - 118, 116);
        rangaIcon.setPosition(width - 64, 116);
        messageLabel.setPosition((width - 680) / 2.0, height - 92);
        pauseButton.setPosition(width - 62, height - 54);
        syncUpgradeOverlay(width, height);
    }

    private void createUpgradeOverlay() {
        upgradePanel = createPanel(0, 0, 760, 206);
        setUIColors(upgradePanel, new Color(7, 18, 27, 244), null, new Color(91, 211, 224));

        upgradeTitle = createLabel("GRANDE SABIO: ESCOLHA UMA EVOLUCAO", 0, 0, 720, 34);
        upgradeTitle.setAlignment(UILabel.Alignment.CENTER);
        upgradeTitle.setFont("SansSerif", Font.BOLD, 19);
        upgradeTitle.setTextColor(new Color(223, 250, 255));

        // Icones simbolicos nas escolhas do Grande Sabio — uso de interface, que e
        // exatamente para onde os ability_* foram feitos.
        predatorButton = createUpgradeButton("PREDADOR", new Color(39, 118, 166),
                () -> chooseUpgrade(UpgradeChoice.PREDATOR));
        predatorButton.setIconPath(ICONS + "ability_predator.png");
        sageButton = createUpgradeButton("GRANDE SABIO", new Color(92, 76, 166),
                () -> chooseUpgrade(UpgradeChoice.GREAT_SAGE));
        sageButton.setIconPath(ICONS + "ability_great_sage.png");
        // REGENERACAO ainda nao tem icone final: fica so com o texto (o botao lida
        // com iconPath nulo) ate o Codex entregar um.
        regenerationButton = createUpgradeButton("REGENERACAO", new Color(48, 139, 91),
                () -> chooseUpgrade(UpgradeChoice.REGENERATION));

        setUpgradeOverlayVisible(false);
    }

    private UIButton createUpgradeButton(String text, Color color, Runnable action) {
        UIButton button = createButton(text, 0, 0, 220, 92);
        button.setColorScheme(color, color.brighter(), color.darker());
        button.setBorderColor(new Color(191, 238, 244));
        button.setBorderWidth(1);
        button.setOnClick(action);
        return button;
    }

    private void chooseUpgrade(UpgradeChoice choice) {
        if (!simulation.chooseUpgrade(choice)) return;
        snapshot = simulation.snapshot();
        playSound(SOUNDS + "level_up.wav", 0.58f);
        syncHud();
    }

    private void unlockDebugEvolution() {
        simulation.grantExperience(6_000);
        while (simulation.chooseUpgrade(UpgradeChoice.PREDATOR)) { }
        while (simulation.chooseUpgrade(UpgradeChoice.GREAT_SAGE)) { }
        while (simulation.chooseUpgrade(UpgradeChoice.REGENERATION)) { }
        snapshot = simulation.snapshot();
        syncHud();
    }

    private void consumeDebugUnlockFlag() {
        File flag = AssetResolver.resolve("data/debug-unlock.flag");
        if (flag == null || !flag.isFile()) return;
        String mode = "unlock";
        try {
            mode = Files.readString(flag.toPath()).trim();
            Files.deleteIfExists(flag.toPath());
        } catch (IOException exception) {
            log("Nao foi possivel remover o sinalizador de depuracao: " + exception.getMessage());
        }
        unlockDebugEvolution();
        if ("victory".equalsIgnoreCase(mode)) {
            for (int i = 0; i < 7_200 && !simulation.snapshot().victory(); i++) {
                simulation.update(0.05, RunInput.NONE);
            }
            snapshot = simulation.snapshot();
        }
    }

    private void syncUpgradeOverlay(double width, double height) {
        boolean visible = snapshot.pendingUpgrades() > 0 && !snapshot.gameOver() && !snapshot.victory();
        setUpgradeOverlayVisible(visible);
        if (!visible) return;

        boolean compact = width < 800;
        double panelWidth = compact ? Math.max(450, width - 40) : 760;
        double panelHeight = compact ? 184 : 206;
        double buttonWidth = compact ? (panelWidth - 56) / 3.0 : 220;
        double buttonHeight = compact ? 78 : 92;
        double x = (width - panelWidth) / 2.0;
        double y = (height - panelHeight) / 2.0;
        upgradePanel.setSize(panelWidth, panelHeight);
        upgradePanel.setPosition(x, y);
        upgradeTitle.setSize(panelWidth - 40, 34);
        upgradeTitle.setPosition(x + 20, y + 16);
        predatorButton.setSize(buttonWidth, buttonHeight);
        sageButton.setSize(buttonWidth, buttonHeight);
        regenerationButton.setSize(buttonWidth, buttonHeight);
        predatorButton.setPosition(x + 14, y + 70);
        sageButton.setPosition(x + 28 + buttonWidth, y + 70);
        regenerationButton.setPosition(x + 42 + buttonWidth * 2, y + 70);
        predatorButton.setText("PREDADOR  " + snapshot.weaponLevel() + "/8");
        sageButton.setText("GRANDE SABIO  " + snapshot.passiveLevel() + "/5");
        regenerationButton.setText("REGENERACAO  " + snapshot.regenerationLevel() + "/8");
        predatorButton.setEnabled(snapshot.weaponLevel() < 8);
        sageButton.setEnabled(snapshot.passiveLevel() < 5);
        regenerationButton.setEnabled(snapshot.regenerationLevel() < 8);
    }

    private void setUpgradeOverlayVisible(boolean visible) {
        upgradePanel.setVisible(visible);
        upgradeTitle.setVisible(visible);
        predatorButton.setVisible(visible);
        sageButton.setVisible(visible);
        regenerationButton.setVisible(visible);
    }

    private void createPauseOverlay() {
        pausePanel = createPanel(0, 0, 420, 218);
        setUIColors(pausePanel, new Color(5, 17, 25, 246), null, new Color(96, 213, 224));
        pauseTitle = createLabel("PAUSA", 0, 0, 380, 48);
        pauseTitle.setAlignment(UILabel.Alignment.CENTER);
        pauseTitle.setFont("SansSerif", Font.BOLD, 28);
        pauseTitle.setTextColor(new Color(224, 249, 252));
        resumeButton = createButton("CONTINUAR", 0, 0, 176, 52);
        resumeButton.setColorScheme(new Color(35, 147, 168), new Color(50, 177, 198), new Color(24, 112, 132));
        resumeButton.setOnClick(this::resumeRun);
        pauseRestartButton = createButton("REINICIAR", 0, 0, 176, 52);
        pauseRestartButton.setColorScheme(new Color(92, 76, 166), new Color(116, 98, 194), new Color(68, 55, 128));
        pauseRestartButton.setOnClick(this::beginRun);
        setPauseOverlayVisible(false);
    }

    private void pauseRun() {
        if (screenState != ScreenState.PLAYING) return;
        screenState = ScreenState.PAUSED;
        setPauseOverlayVisible(true);
        layoutPauseOverlay();
    }

    private void resumeRun() {
        if (screenState != ScreenState.PAUSED) return;
        screenState = ScreenState.PLAYING;
        setPauseOverlayVisible(false);
    }

    private void layoutPauseOverlay() {
        double width = Math.max(480, getGame().getWidth());
        double height = Math.max(360, getGame().getHeight());
        double x = (width - 420) / 2.0;
        double y = (height - 218) / 2.0;
        pausePanel.setPosition(x, y);
        pauseTitle.setPosition(x + 20, y + 24);
        resumeButton.setPosition(x + 24, y + 126);
        pauseRestartButton.setPosition(x + 220, y + 126);
    }

    private void setPauseOverlayVisible(boolean visible) {
        pausePanel.setVisible(visible);
        pauseTitle.setVisible(visible);
        resumeButton.setVisible(visible);
        pauseRestartButton.setVisible(visible);
    }

    private void createResultOverlay() {
        resultPanel = createPanel(0, 0, 520, 252);
        setUIColors(resultPanel, new Color(5, 17, 25, 248), null, new Color(255, 211, 112));
        resultTitle = createLabel("", 0, 0, 480, 54);
        resultTitle.setAlignment(UILabel.Alignment.CENTER);
        resultTitle.setFont("SansSerif", Font.BOLD, 28);
        resultTitle.setTextColor(new Color(255, 226, 151));
        resultStats = createLabel("", 0, 0, 460, 66);
        resultStats.setAlignment(UILabel.Alignment.CENTER);
        resultStats.setFont("SansSerif", Font.BOLD, 16);
        resultStats.setTextColor(new Color(220, 241, 244));
        resultStats.setMultiline(true);
        resultRestartButton = createButton("NOVA PARTIDA", 0, 0, 220, 56);
        resultRestartButton.setColorScheme(new Color(35, 147, 168), new Color(50, 177, 198), new Color(24, 112, 132));
        resultRestartButton.setOnClick(this::beginRun);
        setResultOverlayVisible(false);
    }

    private void showResult() {
        if (screenState == ScreenState.RESULT) return;
        screenState = ScreenState.RESULT;
        setUpgradeOverlayVisible(false);
        resultTitle.setText(snapshot.victory() ? "VITORIA" : "ANALISE INTERROMPIDA");
        resultStats.setText("Nivel " + snapshot.level() + "   KOs " + snapshot.kills()
                + "\nForma final: " + formDisplayName());
        setResultOverlayVisible(true);
        layoutResultOverlay();
    }

    private void layoutResultOverlay() {
        double width = Math.max(520, getGame().getWidth());
        double height = Math.max(360, getGame().getHeight());
        double x = (width - 520) / 2.0;
        double y = (height - 252) / 2.0;
        resultPanel.setPosition(x, y);
        resultTitle.setPosition(x + 20, y + 24);
        resultStats.setPosition(x + 30, y + 90);
        resultRestartButton.setPosition(x + 150, y + 174);
    }

    private void setResultOverlayVisible(boolean visible) {
        resultPanel.setVisible(visible);
        resultTitle.setVisible(visible);
        resultStats.setVisible(visible);
        resultRestartButton.setVisible(visible);
    }

    private void layoutMenu() {
        double width = Math.max(480, getGame().getWidth());
        double height = Math.max(360, getGame().getHeight());
        menuBackground.setSize(width, height);
        menuTitle.setSize(Math.min(620, width - 40), 72);
        menuTitle.setPosition((width - menuTitle.getWidth()) / 2.0, Math.max(28, height * 0.10));
        menuSubtitle.setPosition((width - 460) / 2.0, Math.max(96, height * 0.24));
        menuRimuru.setPosition((width - 160) / 2.0, Math.max(145, height * 0.34));
        startButton.setPosition((width - 240) / 2.0, Math.min(height - 82, height * 0.75));
    }

    private String formDisplayName() {
        if (snapshot.azathothAwakened()) return "Rimuru - Deus do Vazio";
        return switch (snapshot.form()) {
            case SLIME -> "Rimuru - Slime";
            case HUMANOID -> "Rimuru - Humanoide";
            case DEMON_LORD -> snapshot.cielAwakened()
                    ? "Rimuru - Lorde Demonio / Ciel" : "Rimuru - Lorde Demonio";
        };
    }

    private String weaponDisplayName() {
        if (snapshot.azathothAwakened()) return "Azathoth, Deus do Vazio";
        if (snapshot.form() == RimuruForm.DEMON_LORD) return "Beelzebuth, Rei Glutao";
        if (snapshot.form() == RimuruForm.HUMANOID) return "Katana Predadora";
        return "Predador + Hidrolamina";
    }

    /**
     * Icone da arma no HUD.
     *
     * <p>Aqui e o lugar CERTO dos ability_*: sao icones simbolicos de interface. O
     * HUD vinha reaproveitando sprites de mundo como icone (predator_core.png) — o
     * inverso do erro oposto, que foi por os icones na arena.</p>
     *
     * <p>As formas sem icone final (katana, Beelzebuth, vazio) seguem com o sprite
     * antigo ate o Codex entregar os icones correspondentes.</p>
     */
    private String weaponIconPath() {
        if (snapshot.azathothAwakened()) return SPRITES + "azathoth_void_blade.png";
        if (snapshot.form() == RimuruForm.DEMON_LORD) return SPRITES + "beelzebuth_blade.png";
        if (snapshot.form() == RimuruForm.HUMANOID) return SPRITES + "predator_katana.png";
        // Slime: a arma primaria e a Hidrolamina (o proprio formLabel diz isso).
        return ICONS + "ability_hydrolance.png";
    }

    /** Icone da habilidade da forma atual, ou null quando ainda nao ha arte. */
    private String skillIconPath() {
        return switch (snapshot.form()) {
            case SLIME -> ICONS + "ability_predator.png";
            case HUMANOID -> ICONS + "ability_black_lightning.png";
            // Beelzebuth/Azathoth ainda nao tem icone final.
            case DEMON_LORD -> null;
        };
    }

    private void processEvents(List<RunEvent> events) {
        for (RunEvent event : events) {
            if (event.type() == RunEventType.ATTACK) {
                playAttackSound(event.detail());
                continue;
            }
            switch (event.type()) {
                case PREDATOR_CAST -> {
                    // Tipado pelo dominio: nao dependemos mais de casar a string
                    // "Predador" no detail para saber que clipe/som tocar.
                    playSound(SOUNDS + "predator.wav", 0.34f);
                    playOneShot(player, CLIP_SLIME_PREDATOR_CAST);
                }
                case RANGA_ATTACK -> {
                    playSound(SOUNDS + "ranga_bite.wav", 0.34f);
                    playOneShot(visuals.get(RANGA_ID), CLIP_RANGA_ATTACK);
                }
                case PLAYER_HIT -> {
                    playSound(SOUNDS + "rimuru_hit.wav", 0.35f);
                    playOneShot(player, CLIP_SLIME_HIT);
                }
                case LEVEL_UP -> {
                    playSound(SOUNDS + "level_up.wav", 0.58f);
                    showMessage("Grande Sabio: nivel " + event.detail() + " analisado", 1.6);
                }
                case FORM_CHANGED -> {
                    playSound(SOUNDS + "transformation.wav", 0.80f);
                    cameraShake(7);
                    showMessage("Evolucao concluida: " + formDisplayName(), 3.2);
                }
                case RANGA_SUMMONED -> {
                    playSound(SOUNDS + "ranga_call.wav", 0.72f);
                    // syncWorld ja rodou neste tick, entao o visual do Ranga existe.
                    playOneShot(visuals.get(RANGA_ID), CLIP_RANGA_SUMMON);
                    showMessage("Ranga respondeu ao chamado de Rimuru", 3.0);
                }
                case CIEL_AWAKENED -> showMessage("Grande Sabio evoluiu para Ciel", 3.2);
                case AZATHOTH_AWAKENED -> showMessage("Habilidade Suprema: Azathoth", 3.5);
                case BOSS_SPAWNED -> {
                    playSound(SOUNDS + "boss_warning.wav", 0.90f);
                    showMessage("Ciel: Morte Vermelha detectada", 4.0);
                }
                case GAME_OVER -> showMessage("Analise interrompida. Pressione R para reiniciar.", 999);
                case VICTORY -> {
                    stopMusic();
                    playSound(SOUNDS + "victory.wav", 0.92f);
                    showMessage("Azathoth rompeu o conceito da Morte", 999);
                }
                default -> { }
            }
        }
    }

    private void playAttackSound(String detail) {
        if (sfxThrottle > 0) return;
        String sound = detail.contains("Ranga") ? "ranga_bite.wav"
                : detail.contains("Relampago") ? "black_lightning.wav"
                : detail.contains("Predador") || detail.contains("Beelzebuth") ? "predator.wav"
                : detail.contains("Katana") || detail.contains("Azathoth") ? "katana_slash.wav"
                : "water_blade.wav";
        playSound(SOUNDS + sound, 0.34f);
        sfxThrottle = 0.12;
    }

    private void showMessage(String message, double seconds) {
        messageLabel.setText(message);
        messageLabel.setVisible(true);
        messageTimer = seconds;
    }

    private WeaponProgression loadWeaponProgression() {
        File file = AssetResolver.resolve("data/rimuru-progression.json");
        try {
            JSONObject root = new JSONObject(Files.readString(file.toPath()));
            JSONArray levelsJson = root.getJSONObject("weapon").getJSONArray("levels");
            List<WeaponLevelStats> levels = new ArrayList<>();
            for (int i = 0; i < levelsJson.length(); i++) {
                JSONObject level = levelsJson.getJSONObject(i);
                levels.add(new WeaponLevelStats(
                        level.getInt("level"), level.optDouble("damage", 0),
                        level.optDouble("cooldown", 0), level.optInt("amount", 0),
                        level.optDouble("area", 0), level.optDouble("speed", 0),
                        level.optInt("pierce", 0), level.optDouble("slowSeconds", 0),
                        level.optDouble("returnDamage", 0), level.optDouble("slowCap", 0),
                        level.optDouble("damageMultiplier", 1.0), level.optString("summon", null)));
            }
            return new WeaponProgression(levels);
        } catch (IOException exception) {
            throw new IllegalStateException("Falha ao carregar a progressao de Rimuru: " + file, exception);
        }
    }

    private enum ScreenState {
        MENU,
        PLAYING,
        PAUSED,
        RESULT
    }

    private static class VisualSpec {
        private final String path;
        private final int width;
        private final int height;
        private final int zIndex;

        private VisualSpec(String path, int width, int height, int zIndex) {
            this.path = path;
            this.width = width;
            this.height = height;
            this.zIndex = zIndex;
        }

        String path() { return path; }
        int width() { return width; }
        int height() { return height; }
        int zIndex() { return zIndex; }
        String sprite(double elapsed) { return path; }
    }

    private static final class AnimatedVisualSpec extends VisualSpec {
        private final double frameDuration;
        // Caminhos dos quadros prontos: sprite() e chamado a cada tick e montava a
        // string do caminho na hora (path + "0" + frame + ".png").
        private final String[] frames;

        private AnimatedVisualSpec(String path, int width, int height, int zIndex, double frameDuration) {
            super(path, width, height, zIndex);
            this.frameDuration = frameDuration;
            this.frames = new String[] {
                path + "01.png", path + "02.png", path + "03.png", path + "04.png"
            };
        }

        @Override
        String sprite(double elapsed) {
            return frames[(int) (elapsed / frameDuration) % frames.length];
        }
    }
}
