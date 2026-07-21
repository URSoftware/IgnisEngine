import com.ignis.core.IgnisScript;
import com.rimurusurvivors.domain.CampaignSaveCodec;
import com.rimurusurvivors.domain.CampaignSnapshot;
import com.rimurusurvivors.domain.SaveMigrationChain;
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

    private CampaignSaveCodec campaignCodec;
    private AtomicCampaignSaveStore store;

    @Override
    public void start() {
        campaignCodec = new CampaignSaveCodec();
        store = new AtomicCampaignSaveStore(
                UserDataPaths.defaultSaveDirectory(),
                new CampaignSaveDocumentProcessor(
                        new SaveMigrationChain(
                                CampaignSnapshot.CURRENT_SCHEMA_VERSION, List.of()),
                        campaignCodec));

        onSceneSignal(SIGNAL_SAVE_REQUEST, this::saveCampaign);
        onSceneSignal(SIGNAL_LOAD_REQUEST, payload -> loadCampaign());
        log("CampaignSaveDirector pronto em " + store.saveDirectory());
    }

    private void saveCampaign(Object payload) {
        if (!(payload instanceof CampaignSnapshot snapshot)) {
            fail("Pedido de save sem CampaignSnapshot valido.");
            return;
        }
        try {
            store.save(CAMPAIGN_SLOT, campaignCodec.encode(snapshot));
            sceneDispatcher.enqueue(SIGNAL_SAVED, snapshot);
        } catch (IOException | RuntimeException exception) {
            fail("Falha ao salvar campanha: " + exception.getMessage());
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
            CampaignSnapshot snapshot = campaignCodec.decode(result.document().orElseThrow());
            sceneDispatcher.enqueue(SIGNAL_LOADED, snapshot);
        } catch (RuntimeException exception) {
            fail("Falha ao carregar campanha: " + exception.getMessage());
        }
    }

    private void fail(String message) {
        log(message);
        sceneDispatcher.enqueue(SIGNAL_SAVE_FAILED, message);
    }
}
