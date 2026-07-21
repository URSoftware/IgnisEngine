package com.rimurusurvivors.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rimurusurvivors.domain.CampaignSaveCodec;
import com.rimurusurvivors.domain.CampaignSnapshot;
import com.rimurusurvivors.domain.SaveDocument;
import com.rimurusurvivors.domain.SaveMigrationChain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AtomicCampaignSaveStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAndLoadsValidatedPrimary() throws IOException {
        AtomicCampaignSaveStore store = store(() -> { });
        SaveDocument expected = document("cave_gallery", 10, 20, Set.of("awakened"));

        store.save(1, expected);

        SaveLoadResult loaded = store.load(1);
        assertTrue(loaded.found());
        assertEquals(SaveLoadResult.Source.PRIMARY, loaded.source());
        assertEquals(expected, loaded.document().orElseThrow());
        assertTrue(loaded.warnings().isEmpty());
        assertFalse(Files.exists(temporaryDirectory.resolve("slot-1.tmp")));
    }

    @Test
    void failedCommitLeavesPreviousPrimaryByteForByteIntact() throws IOException {
        AtomicCampaignSaveStore healthyStore = store(() -> { });
        healthyStore.save(1, document("cave_gallery", 10, 20, Set.of("awakened")));
        Path primary = temporaryDirectory.resolve("slot-1.json");
        byte[] previousBytes = Files.readAllBytes(primary);

        AtomicCampaignSaveStore failingStore = store(() -> {
            throw new IOException("simulated interruption before atomic replacement");
        });

        assertThrows(
                IOException.class,
                () -> failingStore.save(
                        1, document("jura_forest_approach", 30, 40, Set.of("awakened"))));
        assertArrayEquals(previousBytes, Files.readAllBytes(primary));
        assertFalse(Files.exists(temporaryDirectory.resolve("slot-1.tmp")));
    }

    @Test
    void validPreviousPrimaryBecomesValidatedBackup() throws IOException {
        AtomicCampaignSaveStore store = store(() -> { });
        SaveDocument first = document("cave_gallery", 10, 20, Set.of("awakened"));
        SaveDocument second = document("jura_forest_approach", 30, 40, Set.of("awakened"));

        store.save(1, first);
        store.save(1, second);
        Files.writeString(
                temporaryDirectory.resolve("slot-1.json"), "broken", StandardCharsets.UTF_8);

        SaveLoadResult loaded = store.load(1);
        assertEquals(SaveLoadResult.Source.BACKUP, loaded.source());
        assertEquals(first, loaded.document().orElseThrow());
        assertEquals(1, loaded.warnings().size());
    }

    @Test
    void backupFailureAbortsWithoutReplacingOrMislabelingValidPrimary() throws IOException {
        AtomicCampaignSaveStore store = store(() -> { });
        SaveDocument first = document("cave_gallery", 10, 20, Set.of("awakened"));
        store.save(1, first);
        Path primary = temporaryDirectory.resolve("slot-1.json");
        byte[] previousBytes = Files.readAllBytes(primary);
        Path blockedBackup = temporaryDirectory.resolve("slot-1.bak.tmp");
        Files.createDirectory(blockedBackup);
        Files.writeString(blockedBackup.resolve("keep.txt"), "do not replace");

        assertThrows(
                IOException.class,
                () -> store.save(
                        1, document("jura_forest_approach", 30, 40, Set.of("awakened"))));

        assertArrayEquals(previousBytes, Files.readAllBytes(primary));
        assertFalse(Files.exists(temporaryDirectory.resolve("slot-1.corrupt")));
    }

    @Test
    void doubleCorruptionReturnsEmptyWithoutChangingEvidence() throws IOException {
        AtomicCampaignSaveStore store = store(() -> { });
        Path primary = temporaryDirectory.resolve("slot-1.json");
        Path backup = temporaryDirectory.resolve("slot-1.bak");
        Files.writeString(primary, "broken-primary", StandardCharsets.UTF_8);
        Files.writeString(backup, "broken-backup", StandardCharsets.UTF_8);
        byte[] primaryEvidence = Files.readAllBytes(primary);
        byte[] backupEvidence = Files.readAllBytes(backup);

        SaveLoadResult loaded = store.load(1);

        assertEquals(SaveLoadResult.Source.NONE, loaded.source());
        assertFalse(loaded.found());
        assertEquals(2, loaded.warnings().size());
        assertArrayEquals(primaryEvidence, Files.readAllBytes(primary));
        assertArrayEquals(backupEvidence, Files.readAllBytes(backup));
    }

    @Test
    void replacingCorruptPrimaryPreservesASeparateEvidenceFile() throws IOException {
        AtomicCampaignSaveStore store = store(() -> { });
        Path primary = temporaryDirectory.resolve("slot-1.json");
        byte[] corruptBytes = "corrupt-evidence".getBytes(StandardCharsets.UTF_8);
        Files.write(primary, corruptBytes);

        SaveDocument replacement = document("cave_awakening", 4, 5, Set.of());
        store.save(1, replacement);

        assertArrayEquals(
                corruptBytes, Files.readAllBytes(temporaryDirectory.resolve("slot-1.corrupt")));
        assertEquals(replacement, store.load(1).document().orElseThrow());
    }

    @Test
    void rejectsInvalidSlotAndInvalidCampaignBeforeWriting() {
        AtomicCampaignSaveStore store = store(() -> { });
        assertThrows(IllegalArgumentException.class, () -> store.load(0));
        SaveDocument semanticallyInvalid = new SaveDocument(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION,
                java.util.Map.of("campaign.areaId", "cave_gallery"));
        assertThrows(IllegalArgumentException.class, () -> store.save(1, semanticallyInvalid));
        assertFalse(Files.exists(temporaryDirectory.resolve("slot-1.json")));
    }

    private AtomicCampaignSaveStore store(AtomicCampaignSaveStore.BeforeCommitHook hook) {
        SaveDocumentProcessor processor = new CampaignSaveDocumentProcessor(
                new SaveMigrationChain(CampaignSnapshot.CURRENT_SCHEMA_VERSION, List.of()),
                new CampaignSaveCodec());
        return new AtomicCampaignSaveStore(
                temporaryDirectory, new JsonSaveDocumentCodec(), processor, hook);
    }

    private static SaveDocument document(
            String areaId, double x, double y, Set<String> milestones) {
        return new CampaignSaveCodec().encode(new CampaignSnapshot(
                CampaignSnapshot.CURRENT_SCHEMA_VERSION, areaId, x, y, milestones));
    }
}
