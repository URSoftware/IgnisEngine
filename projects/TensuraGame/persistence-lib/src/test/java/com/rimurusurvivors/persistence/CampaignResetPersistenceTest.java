package com.rimurusurvivors.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rimurusurvivors.domain.CampaignSaveCodec;
import com.rimurusurvivors.domain.CampaignSaveMigrations;
import com.rimurusurvivors.domain.CampaignSnapshot;
import com.rimurusurvivors.domain.CampaignState;
import com.rimurusurvivors.domain.SaveDocument;
import com.rimurusurvivors.domain.TownProjects;
import com.rimurusurvivors.domain.TownResourceBundle;
import com.rimurusurvivors.domain.ReturnReport;
import com.rimurusurvivors.domain.TownCommand;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reset de Novo Jogo no disco. Só fixtures em diretório temporário: o save real do
 * usuário nunca é lido nem reescrito por estes testes.
 */
class CampaignResetPersistenceTest {

    @TempDir
    Path temporaryDirectory;

    private final CampaignSaveCodec codec = new CampaignSaveCodec();

    @Test
    void persistedResetReplacesAnAdvancedCampaignWithoutInheritance() throws IOException {
        AtomicCampaignSaveStore store = store(() -> { });
        store.save(1, codec.encodeState(advancedCampaign()));

        store.save(1, codec.encodeState(CampaignState.fromSnapshot(initialSnapshot())));

        CampaignState reloaded = codec.decodeState(
                store.load(1).document().orElseThrow());
        assertEquals("cave_awakening", reloaded.checkpoint().areaId());
        assertTrue(reloaded.checkpoint().completedMilestones().isEmpty());
        assertFalse(reloaded.narrativeFlags().contains("ranga_naming_complete"));
        assertTrue(reloaded.townState().specialists().isEmpty());
        assertEquals(TownResourceBundle.EMPTY, reloaded.townState().resources());
    }

    @Test
    void failedResetKeepsThePreviousCampaignOnDisk() throws IOException {
        AtomicCampaignSaveStore healthyStore = store(() -> { });
        healthyStore.save(1, codec.encodeState(advancedCampaign()));
        Path primary = temporaryDirectory.resolve("slot-1.json");
        byte[] previousBytes = Files.readAllBytes(primary);

        AtomicCampaignSaveStore failingStore = store(() -> {
            throw new IOException("simulated interruption before atomic replacement");
        });
        SaveDocument reset = codec.encodeState(
                CampaignState.fromSnapshot(initialSnapshot()));

        assertThrows(IOException.class, () -> failingStore.save(1, reset));

        assertArrayEquals(previousBytes, Files.readAllBytes(primary));
        assertFalse(Files.exists(temporaryDirectory.resolve("slot-1.tmp")));
        CampaignState survivor = codec.decodeState(
                healthyStore.load(1).document().orElseThrow());
        assertEquals("goblin_village", survivor.checkpoint().areaId());
        assertTrue(survivor.townState().specialists().contains(TownProjects.GOBLIN_BUILDER));
    }

    private CampaignState advancedCampaign() {
        return CampaignState
                .fromSnapshot(new CampaignSnapshot(
                        CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                        "goblin_village",
                        304,
                        340,
                        Set.of(
                                "awakening_complete",
                                "goblin_contact_complete",
                                "dire_wolf_duel_complete",
                                "ranga_naming_complete")))
                .apply(new TownCommand.ApplyReturnReport(new ReturnReport(
                        "forest_return_after_ranga",
                        new TownResourceBundle(40, 24, 8, 4, 4, 2),
                        Set.of("discovery_jura_timber"),
                        Set.of(TownProjects.GOBLIN_BUILDER),
                        2)));
    }

    private static CampaignSnapshot initialSnapshot() {
        return new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                "cave_awakening",
                80,
                208,
                Set.of());
    }

    private AtomicCampaignSaveStore store(AtomicCampaignSaveStore.BeforeCommitHook hook) {
        SaveDocumentProcessor processor = new CampaignSaveDocumentProcessor(
                CampaignSaveMigrations.currentChain(),
                new CampaignSaveCodec());
        return new AtomicCampaignSaveStore(
                temporaryDirectory, new JsonSaveDocumentCodec(), processor, hook);
    }
}
