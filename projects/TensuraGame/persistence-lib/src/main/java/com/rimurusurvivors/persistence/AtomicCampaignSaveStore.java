package com.rimurusurvivors.persistence;

import com.rimurusurvivors.domain.SaveDocument;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores campaign slots without truncating the active save before a replacement is complete.
 */
public final class AtomicCampaignSaveStore {

    private final Path saveDirectory;
    private final JsonSaveDocumentCodec jsonCodec;
    private final SaveDocumentProcessor processor;
    private final BeforeCommitHook beforeCommit;

    public AtomicCampaignSaveStore(Path saveDirectory, SaveDocumentProcessor processor) {
        this(saveDirectory, new JsonSaveDocumentCodec(), processor, () -> { });
    }

    AtomicCampaignSaveStore(
            Path saveDirectory,
            JsonSaveDocumentCodec jsonCodec,
            SaveDocumentProcessor processor,
            BeforeCommitHook beforeCommit) {
        if (saveDirectory == null || jsonCodec == null || processor == null || beforeCommit == null) {
            throw new IllegalArgumentException("Save store dependencies are required.");
        }
        this.saveDirectory = saveDirectory.toAbsolutePath().normalize();
        this.jsonCodec = jsonCodec;
        this.processor = processor;
        this.beforeCommit = beforeCommit;
    }

    public void save(int slotNumber, SaveDocument document) throws IOException {
        SlotPaths paths = slotPaths(slotNumber);
        SaveDocument validated = processor.process(document);
        byte[] encoded = jsonCodec.encode(validated).getBytes(StandardCharsets.UTF_8);
        Files.createDirectories(saveDirectory);

        try {
            writeAndForce(paths.temporary(), encoded);
            readValidated(paths.temporary());
            prepareExistingPrimary(paths);
            beforeCommit.run();
            moveReplacing(paths.temporary(), paths.primary());
        } finally {
            Files.deleteIfExists(paths.temporary());
            Files.deleteIfExists(paths.backupTemporary());
        }
    }

    public SaveLoadResult load(int slotNumber) {
        SlotPaths paths = slotPaths(slotNumber);
        List<String> warnings = new ArrayList<>();

        if (Files.exists(paths.primary())) {
            try {
                return new SaveLoadResult(
                        java.util.Optional.of(readValidated(paths.primary())),
                        SaveLoadResult.Source.PRIMARY,
                        warnings);
            } catch (IOException | RuntimeException exception) {
                warnings.add("Primary save is invalid: " + exception.getClass().getSimpleName());
            }
        }

        if (Files.exists(paths.backup())) {
            try {
                return new SaveLoadResult(
                        java.util.Optional.of(readValidated(paths.backup())),
                        SaveLoadResult.Source.BACKUP,
                        warnings);
            } catch (IOException | RuntimeException exception) {
                warnings.add("Backup save is invalid: " + exception.getClass().getSimpleName());
            }
        }

        return new SaveLoadResult(
                java.util.Optional.empty(), SaveLoadResult.Source.NONE, warnings);
    }

    public Path saveDirectory() {
        return saveDirectory;
    }

    private void prepareExistingPrimary(SlotPaths paths) throws IOException {
        if (!Files.exists(paths.primary())) {
            return;
        }
        try {
            readValidated(paths.primary());
        } catch (IOException | RuntimeException exception) {
            preserveCorruptEvidence(paths);
            return;
        }
        Files.copy(
                paths.primary(), paths.backupTemporary(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
        forceExistingFile(paths.backupTemporary());
        readValidated(paths.backupTemporary());
        moveReplacing(paths.backupTemporary(), paths.backup());
    }

    private void preserveCorruptEvidence(SlotPaths paths) throws IOException {
        int suffix = 0;
        Path evidence;
        do {
            String extension = suffix == 0 ? ".corrupt" : ".corrupt-" + suffix;
            evidence = saveDirectory.resolve(paths.baseName() + extension);
            suffix++;
        } while (Files.exists(evidence));
        Files.copy(paths.primary(), evidence);
        forceExistingFile(evidence);
    }

    private SaveDocument readValidated(Path path) throws IOException {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        return processor.process(jsonCodec.decode(json));
    }

    private static void writeAndForce(Path path, byte[] bytes) throws IOException {
        try (FileChannel channel = FileChannel.open(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void forceExistingFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private SlotPaths slotPaths(int slotNumber) {
        if (slotNumber < 1) {
            throw new IllegalArgumentException("Save slot number must be positive.");
        }
        String baseName = "slot-" + slotNumber;
        return new SlotPaths(
                baseName,
                saveDirectory.resolve(baseName + ".json"),
                saveDirectory.resolve(baseName + ".tmp"),
                saveDirectory.resolve(baseName + ".bak"),
                saveDirectory.resolve(baseName + ".bak.tmp"));
    }

    @FunctionalInterface
    interface BeforeCommitHook {
        void run() throws IOException;
    }

    private record SlotPaths(
            String baseName,
            Path primary,
            Path temporary,
            Path backup,
            Path backupTemporary) {
    }
}
