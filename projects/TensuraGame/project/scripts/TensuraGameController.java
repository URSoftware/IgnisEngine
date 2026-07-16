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

    private final Map<Long, GameObject> visuals = new HashMap<>();
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
                new RunInput(getHorizontalAxis(), getVerticalAxis()));
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
        Set<Long> activeIds = new HashSet<>();
        for (WorldEntitySnapshot entity : snapshot.entities()) {
            activeIds.add(entity.id());
            GameObject visual = visuals.computeIfAbsent(entity.id(), ignored ->
                    spawnRuntimeVisual(entity.kind().name(), entity.x(), entity.y()));
            VisualSpec spec = specFor(entity.kind());
            visual.setWidth(spec.width());
            visual.setHeight(spec.height());
            visual.setX(entity.x() - spec.width() / 2.0);
            visual.setY(entity.y() - spec.height() / 2.0);
            visual.setRotation(entity.rotation());
            visual.setZIndex(spec.zIndex());
            setSpriteIfChanged(visual, spec.sprite(snapshot.elapsedSeconds()));
        }

        List<Long> stale = visuals.keySet().stream()
                .filter(id -> !activeIds.contains(id))
                .toList();
        for (Long id : stale) destroy(visuals.remove(id));
    }

    private void syncPlayer() {
        if (player == null) return;
        int frame = (int) (snapshot.elapsedSeconds() / frameDuration(snapshot.form())) % 4 + 1;
        String formName = switch (snapshot.form()) {
            case SLIME -> "slime";
            case HUMANOID -> "humanoid";
            case DEMON_LORD -> "demon_lord";
        };
        int width = snapshot.form() == RimuruForm.SLIME ? 48 : 46;
        int height = snapshot.form() == RimuruForm.SLIME ? 42 : 62;
        player.setWidth(width);
        player.setHeight(height);
        player.setX(snapshot.playerX() - width / 2.0);
        player.setY(snapshot.playerY() - height / 2.0);
        setSpriteIfChanged(player, SPRITES + "rimuru_" + formName + "_0" + frame + ".png");
        if (lastForm != snapshot.form()) {
            lastForm = snapshot.form();
            player.setOpacity(1.0);
        }
    }

    private double frameDuration(RimuruForm form) {
        return switch (form) {
            case SLIME -> 0.16;
            case HUMANOID -> 0.11;
            case DEMON_LORD -> 0.09;
        };
    }

    private GameObject spawnRuntimeVisual(String name, double x, double y) {
        GameObject object = getGame().instantiatePrefab("RuntimeVisual", x, y);
        if (object == null) {
            object = new GameObject(name, getGame(), x, y, 32, 32);
            getGame().addEntity(object);
        }
        object.setName(name);
        object.setTag("tensura_runtime");
        object.setVisible(true);
        object.setOpacity(1.0);
        return object;
    }

    private void setSpriteIfChanged(GameObject object, String path) {
        if (!path.equals(object.getSpritePath())) object.setSpritePath(path);
    }

    private VisualSpec specFor(WorldEntityKind kind) {
        return switch (kind) {
            case GOBLIN -> new VisualSpec(SPRITES + "goblin_scout.png", 32, 32, 10);
            case ORC -> new VisualSpec(SPRITES + "orc_warrior.png", 42, 42, 10);
            case DIRE_WOLF -> new VisualSpec(SPRITES + "dire_wolf.png", 40, 30, 10);
            case FLAME_SPIRIT -> new VisualSpec(SPRITES + "flame_spirit.png", 38, 44, 11);
            case RED_REAPER -> new VisualSpec(SPRITES + "red_reaper.png", 78, 78, 15);
            case MAGICULE_ORB -> new VisualSpec(SPRITES + "magicule_orb.png", 14, 14, 5);
            case WATER_BLADE -> new VisualSpec(SPRITES + "water_blade.png", 32, 14, 20);
            case KATANA_CUT -> new VisualSpec(snapshot.form() == RimuruForm.DEMON_LORD
                    ? SPRITES + "beelzebuth_blade.png" : SPRITES + "predator_katana.png", 42, 22, 20);
            case BLACK_LIGHTNING -> new VisualSpec(SPRITES + "black_lightning.png", 42, 14, 21);
            case PREDATOR_MAW -> new VisualSpec(SPRITES + "predator_maw.png", 110, 110, 18);
            case VOID_CUT -> new VisualSpec(SPRITES + "azathoth_void_blade.png", 52, 26, 22);
            case RANGA -> new AnimatedVisualSpec(SPRITES + "ranga_", 48, 38, 25, 0.10);
        };
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
        weaponIcon = createImage(SPRITES + "predator_core.png", 0, 30, 56, 56);
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

        predatorButton = createUpgradeButton("PREDADOR", new Color(39, 118, 166),
                () -> chooseUpgrade(UpgradeChoice.PREDATOR));
        sageButton = createUpgradeButton("GRANDE SABIO", new Color(92, 76, 166),
                () -> chooseUpgrade(UpgradeChoice.GREAT_SAGE));
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

    private String weaponIconPath() {
        if (snapshot.azathothAwakened()) return SPRITES + "azathoth_void_blade.png";
        if (snapshot.form() == RimuruForm.DEMON_LORD) return SPRITES + "beelzebuth_blade.png";
        if (snapshot.form() == RimuruForm.HUMANOID) return SPRITES + "predator_katana.png";
        return SPRITES + "predator_core.png";
    }

    private void processEvents(List<RunEvent> events) {
        for (RunEvent event : events) {
            if (event.type() == RunEventType.ATTACK) {
                playAttackSound(event.detail());
                continue;
            }
            switch (event.type()) {
                case PLAYER_HIT -> playSound(SOUNDS + "rimuru_hit.wav", 0.35f);
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

        private AnimatedVisualSpec(String path, int width, int height, int zIndex, double frameDuration) {
            super(path, width, height, zIndex);
            this.frameDuration = frameDuration;
        }

        @Override
        String sprite(double elapsed) {
            int frame = (int) (elapsed / frameDuration) % 4 + 1;
            return path() + "0" + frame + ".png";
        }
    }
}
