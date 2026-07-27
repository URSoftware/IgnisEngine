import com.ignis.core.GameObject;
import com.ignis.core.IgnisScript;
import com.ignis.core.ui.UILabel;
import com.rimurusurvivors.domain.CampaignSnapshot;

import java.awt.Color;
import java.awt.Font;
import java.util.Set;

/**
 * Adaptador idempotente da GoblinVillageScene. A cena possui os atores e props;
 * este script apenas projeta o snapshot de campanha sobre os mesmos objetos.
 */
public final class GoblinVillageBootstrap extends IgnisScript {

    private static final double CAMERA_ZOOM = 1.95;
    private static final String SIGNAL_LOAD_REQUEST = "TENSURA_CAMPAIGN_LOAD_REQUEST";
    private static final String SIGNAL_LOADED = "TENSURA_CAMPAIGN_LOADED";
    private static final String SIGNAL_LOAD_EMPTY = "TENSURA_CAMPAIGN_LOAD_EMPTY";

    private static final String PLAYER = "Rimuru";
    private static final String RANGA = "Ranga";
    private static final String PROJECT_BOARD = "VillageProjectBoard";
    private static final String WATCH_POST = "VillageWatchPost";

    private static final String RANGA_SPRITE =
            "assets/sprites/creatures/dire_wolf_leader/dire_wolf_leader_idle_down.png";
    private static final String BOARD_EMPTY = "assets/sprites/buildings/tempest_project_board_empty.png";
    private static final String BOARD_AVAILABLE =
            "assets/sprites/buildings/tempest_project_board_important.png";
    private static final String BOARD_COMPLETE =
            "assets/sprites/buildings/tempest_project_board_complete.png";
    private static final String WATCH_BASE = "assets/sprites/buildings/tempest_watch_post_00.png";
    private static final String WATCH_BUILT = "assets/sprites/buildings/tempest_watch_post_100.png";

    private UILabel locationLabel;
    private UILabel objectiveLabel;
    private boolean initialized;

    @Override
    public void start() {
        setCameraPosition(320, 256);
        setCameraZoom(CAMERA_ZOOM);
        setMusicVolume(0.28f);
        setSfxVolume(0.6f);
        playMusic("assets/music/tempest_forest_theme.wav", true);

        onSceneSignal(SIGNAL_LOADED, payload -> {
            if (payload instanceof CampaignSnapshot snapshot) {
                applySnapshot(snapshot);
            }
        });
        onSceneSignal(SIGNAL_LOAD_EMPTY, payload -> applyMilestones(Set.of()));
        sceneDispatcher.enqueue(SIGNAL_LOAD_REQUEST, null);
        log("GoblinVillageBootstrap: aguardando snapshot da campanha.");
    }

    @Override
    public void tick() {
        if (!initialized) return;
        layoutHud();
    }

    private void applySnapshot(CampaignSnapshot snapshot) {
        applyMilestones(snapshot.completedMilestones());
        GameObject player = findObject(PLAYER);
        if (player != null) {
            // Coordenadas pertencem ao espaco local indicado por areaId. Um save da
            // floresta nao pode deslocar o jogador para fora do enquadramento da aldeia.
            if (snapshot.areaId().startsWith("goblin_village")) {
                player.setX(clamp(snapshot.playerX(), 32, 608) - player.getWidth() / 2.0);
                player.setY(clamp(snapshot.playerY(), 32, 480) - player.getHeight() / 2.0);
            }
            player.setVisible(true);
            player.setOpacity(1);
        }
        log("GoblinVillageBootstrap: snapshot aplicado em estado " + visualState(snapshot.completedMilestones())
                + ".");
    }

    private void applyMilestones(Set<String> milestones) {
        boolean named = milestones.contains("ranga_naming_complete");
        boolean projectAvailable = milestones.contains("tempest_first_project_available");
        boolean projectComplete = milestones.contains("tempest_first_project_complete");

        setActorVisible(PLAYER, true);
        setActorVisible(RANGA, named);
        setSprite(RANGA, RANGA_SPRITE);
        setSprite(PROJECT_BOARD, projectComplete ? BOARD_COMPLETE
                : projectAvailable ? BOARD_AVAILABLE : BOARD_EMPTY);
        setSprite(WATCH_POST, projectComplete ? WATCH_BUILT : WATCH_BASE);
        buildHud(named, projectAvailable, projectComplete);
        initialized = true;
    }

    private void buildHud(boolean named, boolean projectAvailable, boolean projectComplete) {
        clearUI();
        locationLabel = createLabel("ALDEIA GOBLIN", 0, 0, 280, 34);
        locationLabel.setAlignment(UILabel.Alignment.CENTER);
        locationLabel.setFont("SansSerif", Font.BOLD, 18);
        locationLabel.setTextColor(new Color(224, 248, 218));
        locationLabel.setOutline(true, new Color(16, 48, 32), 2);

        String objective = projectComplete
                ? "Posto de vigia concluido. Tempest esta mais segura."
                : projectAvailable
                    ? "O Conselho aguarda uma decisao no quadro de projetos."
                    : named
                        ? "Ranga e a matilha agora protegem a aldeia."
                        : "Fale com o Anciao Goblin para conhecer a aldeia.";
        objectiveLabel = createLabel(objective, 0, 0, 560, 28);
        objectiveLabel.setAlignment(UILabel.Alignment.CENTER);
        objectiveLabel.setFont("SansSerif", Font.BOLD, 13);
        objectiveLabel.setTextColor(new Color(255, 232, 166));
        layoutHud();
    }

    private void layoutHud() {
        double width = Math.max(480, getGame().getWidth());
        locationLabel.setPosition((width - locationLabel.getWidth()) / 2.0, 16);
        objectiveLabel.setPosition((width - objectiveLabel.getWidth()) / 2.0, 52);
    }

    private void setActorVisible(String name, boolean visible) {
        GameObject object = findObject(name);
        if (object == null) return;
        object.setVisible(visible);
        object.setOpacity(1);
    }

    private void setSprite(String name, String path) {
        GameObject object = findObject(name);
        if (object != null) object.setSpritePath(path);
    }

    private static String visualState(Set<String> milestones) {
        if (milestones.contains("tempest_first_project_complete")) return "first_project_complete";
        if (milestones.contains("tempest_first_project_available")) return "first_project_available";
        if (milestones.contains("ranga_naming_complete")) return "ranga_named";
        return "pre_naming";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
