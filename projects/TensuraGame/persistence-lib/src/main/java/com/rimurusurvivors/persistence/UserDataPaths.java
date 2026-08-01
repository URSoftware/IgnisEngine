package com.rimurusurvivors.persistence;

import java.nio.file.Path;

/** Resolves writable game data outside the versioned project directory. */
public final class UserDataPaths {

    /** Override de maior precedencia, usado pelo launcher de QA isolado. */
    public static final String SAVE_DIR_PROPERTY = "tensura.save.dir";

    /** Override herdado pelo processo filho quando o launcher exporta a variavel. */
    public static final String SAVE_DIR_ENV = "TENSURA_SAVE_DIR";

    private UserDataPaths() {
    }

    public static Path defaultSaveDirectory() {
        return resolveSaveDirectory(
                System.getProperty(SAVE_DIR_PROPERTY),
                System.getenv(SAVE_DIR_ENV),
                System.getenv("LOCALAPPDATA"),
                System.getProperty("user.home"));
    }

    /**
     * Precedencia: propriedade de sistema, variavel de ambiente e por fim o diretorio de
     * dados do usuario. Recebe as quatro fontes por parametro porque o ambiente do
     * processo nao e ajustavel em teste; quem le o ambiente real e defaultSaveDirectory.
     */
    static Path resolveSaveDirectory(
            String property, String environment, String localAppData, String userHome) {
        Path override = absoluteOrNull(property);
        if (override != null) {
            return override;
        }
        override = absoluteOrNull(environment);
        if (override != null) {
            return override;
        }
        if (localAppData != null && !localAppData.isBlank()) {
            return normalize(Path.of(localAppData.trim(), "TensuraGame", "saves"));
        }
        if (userHome == null || userHome.isBlank()) {
            throw new IllegalStateException(
                    "Sem diretorio de save: defina " + SAVE_DIR_PROPERTY
                            + ", " + SAVE_DIR_ENV + ", LOCALAPPDATA ou user.home.");
        }
        return normalize(Path.of(userHome.trim(), ".tensura-game", "saves"));
    }

    private static Path absoluteOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return normalize(Path.of(raw.trim()));
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
