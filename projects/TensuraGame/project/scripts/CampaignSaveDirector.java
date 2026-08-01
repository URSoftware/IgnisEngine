import com.ignis.core.IgnisScript;
import com.rimurusurvivors.domain.CampaignCommand;
import com.rimurusurvivors.domain.CampaignSaveCodec;
import com.rimurusurvivors.domain.CampaignSaveMigrations;
import com.rimurusurvivors.domain.CampaignSnapshot;
import com.rimurusurvivors.domain.CampaignState;
import com.rimurusurvivors.domain.TownCommand;
import com.rimurusurvivors.persistence.AtomicCampaignSaveStore;
import com.rimurusurvivors.persistence.CampaignSaveDocumentProcessor;
import com.rimurusurvivors.persistence.SaveLoadResult;
import com.rimurusurvivors.persistence.UserDataPaths;

import java.io.IOException;
import java.util.List;

/**
 * Filesystem adapter for the campaign save. Gameplay talks to this service only
 * through TENSURA_* signals, so no director imports another project script.
 */
public final class CampaignSaveDirector extends IgnisScript {

    private static final int CAMPAIGN_SLOT = 1;
    private static final String SIGNAL_SAVE_REQUEST = "TENSURA_CAMPAIGN_SAVE_REQUEST";
    private static final String SIGNAL_LOAD_REQUEST = "TENSURA_CAMPAIGN_LOAD_REQUEST";
    private static final String SIGNAL_SAVED = "TENSURA_CAMPAIGN_SAVED";
    private static final String SIGNAL_LOADED = "TENSURA_CAMPAIGN_LOADED";
    private static final String SIGNAL_LOAD_EMPTY = "TENSURA_CAMPAIGN_LOAD_EMPTY";
    private static final String SIGNAL_SAVE_WARNING = "TENSURA_CAMPAIGN_SAVE_WARNING";
    private static final String SIGNAL_SAVE_FAILED = "TENSURA_CAMPAIGN_SAVE_FAILED";
    private static final String SIGNAL_COMMAND = "TENSURA_CAMPAIGN_COMMAND";
    private static final String SIGNAL_STATE_LOADED = "TENSURA_CAMPAIGN_STATE_LOADED";
    private static final String SIGNAL_STATE_CHANGED = "TENSURA_CAMPAIGN_STATE_CHANGED";
    private static final String SIGNAL_NEW_GAME = "TENSURA_CAMPAIGN_NEW_GAME";
    private static final String SIGNAL_NEW_GAME_READY =
            "TENSURA_CAMPAIGN_NEW_GAME_READY";
    private static final String SIGNAL_TOWN_COMMAND = "TENSURA_TOWN_COMMAND";
    private static final String SIGNAL_TOWN_COMMAND_REJECTED =
            "TENSURA_TOWN_COMMAND_REJECTED";

    private CampaignSaveCodec campaignCodec;
    private AtomicCampaignSaveStore store;
    private CampaignState campaignState;

    @Override
    public void start() {
        campaignCodec = new CampaignSaveCodec();
        store = new AtomicCampaignSaveStore(
                UserDataPaths.defaultSaveDirectory(),
                new CampaignSaveDocumentProcessor(
                        CampaignSaveMigrations.currentChain(),
                        campaignCodec));

        onSceneSignal(SIGNAL_SAVE_REQUEST, this::saveCampaign);
        onSceneSignal(SIGNAL_LOAD_REQUEST, payload -> loadCampaign());
        onSceneSignal(SIGNAL_COMMAND, this::applyCampaignCommand);
        onSceneSignal(SIGNAL_TOWN_COMMAND, this::applyTownCommand);
        onSceneSignal(SIGNAL_NEW_GAME, this::beginNewCampaign);
        log("CampaignSaveDirector pronto em " + store.saveDirectory());
    }

    private void beginNewCampaign(Object payload) {
        if (!(payload instanceof CampaignSnapshot initialSnapshot)) {
            fail("Pedido de Novo Jogo sem CampaignSnapshot inicial valido.");
            return;
        }
        CampaignState candidate;
        try {
            candidate = CampaignState.fromSnapshot(initialSnapshot);
        } catch (RuntimeException exception) {
            fail("Estado inicial de Novo Jogo recusado: " + exception.getMessage());
            return;
        }
        if (!persistNewCampaign(candidate)) {
            return;
        }
        sceneDispatcher.enqueue(SIGNAL_STATE_CHANGED, campaignState);
        sceneDispatcher.enqueue(SIGNAL_NEW_GAME_READY, campaignState.checkpoint());
        log("CampaignSaveDirector: nova campanha persistida e pronta.");
    }

    private boolean persistNewCampaign(CampaignState candidate) {
        try {
            store.save(CAMPAIGN_SLOT, campaignCodec.encodeState(candidate));
        } catch (IOException | RuntimeException exception) {
            fail("Falha ao iniciar Novo Jogo: " + exception.getMessage());
            return false;
        }
        campaignState = candidate;
        return true;
    }

    private void saveCampaign(Object payload) {
        if (!(payload instanceof CampaignSnapshot snapshot)) {
            fail("Pedido de save sem CampaignSnapshot valido.");
            return;
        }
        CampaignState candidate;
        try {
            candidate = campaignState == null
                    ? CampaignState.fromSnapshot(snapshot)
                    : campaignState.withCheckpoint(snapshot);
        } catch (RuntimeException exception) {
            fail("Checkpoint recusado: " + exception.getMessage());
            return;
        }
        if (persist(candidate, "Falha ao salvar campanha: ")) {
            sceneDispatcher.enqueue(SIGNAL_SAVED, campaignState.checkpoint());
            sceneDispatcher.enqueue(SIGNAL_STATE_CHANGED, campaignState);
        }
    }

    private void loadCampaign() {
        try {
            SaveLoadResult result = store.load(CAMPAIGN_SLOT);
            for (String warning : result.warnings()) {
                log("Save: " + warning);
                sceneDispatcher.enqueue(SIGNAL_SAVE_WARNING, warning);
            }
            if (!result.found()) {
                sceneDispatcher.enqueue(SIGNAL_LOAD_EMPTY, List.copyOf(result.warnings()));
                return;
            }
            CampaignState loadedState = campaignCodec.decodeState(
                    result.document().orElseThrow());
            campaignState = loadedState;
            sceneDispatcher.enqueue(SIGNAL_STATE_LOADED, loadedState);
            sceneDispatcher.enqueue(SIGNAL_LOADED, loadedState.checkpoint());
        } catch (RuntimeException exception) {
            fail("Falha ao carregar campanha: " + exception.getMessage());
        }
    }

    private void applyCampaignCommand(Object payload) {
        if (!(payload instanceof CampaignCommand command)) {
            fail("Comando de campanha invalido.");
            return;
        }
        if (campaignState == null) {
            fail("Comando de campanha recebido antes de carregar ou iniciar um save.");
            return;
        }
        CampaignState candidate;
        try {
            candidate = campaignState.apply(command);
        } catch (RuntimeException exception) {
            fail("Comando de campanha recusado: " + exception.getMessage());
            return;
        }
        if (persist(candidate, "Falha ao aplicar comando de campanha: ")) {
            sceneDispatcher.enqueue(SIGNAL_STATE_CHANGED, campaignState);
        }
    }

    private void applyTownCommand(Object payload) {
        if (!(payload instanceof TownCommand command)) {
            rejectTownCommand("Comando de Tempest invalido.");
            return;
        }
        if (campaignState == null) {
            rejectTownCommand("Comando de Tempest recebido antes de carregar ou iniciar um save.");
            return;
        }
        CampaignState candidate;
        try {
            candidate = campaignState.apply(command);
        } catch (RuntimeException exception) {
            // Recusa nao mutante: memoria e disco continuam no estado anterior.
            rejectTownCommand("Comando de Tempest recusado: " + exception.getMessage());
            return;
        }
        if (persist(candidate, "Falha ao persistir comando de Tempest: ")) {
            sceneDispatcher.enqueue(SIGNAL_STATE_CHANGED, campaignState);
        }
    }

    /** Grava o candidato uma unica vez e so promove o estado vivo depois do disco aceitar. */
    private boolean persist(CampaignState candidate, String failurePrefix) {
        if (candidate.equals(campaignState)) {
            return true;
        }
        try {
            store.save(CAMPAIGN_SLOT, campaignCodec.encodeState(candidate));
        } catch (IOException | RuntimeException exception) {
            fail(failurePrefix + exception.getMessage());
            return false;
        }
        campaignState = candidate;
        return true;
    }

    private void rejectTownCommand(String message) {
        log(message);
        sceneDispatcher.enqueue(SIGNAL_TOWN_COMMAND_REJECTED, message);
    }

    private void fail(String message) {
        log(message);
        sceneDispatcher.enqueue(SIGNAL_SAVE_FAILED, message);
    }
}
