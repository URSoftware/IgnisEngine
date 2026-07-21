package com.rimurusurvivors.persistence;

import java.nio.file.Path;

/** Resolves writable game data outside the versioned project directory. */
public final class UserDataPaths {

    private UserDataPaths() {
    }

    public static Path defaultSaveDirectory() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "TensuraGame", "saves").toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".tensura-game", "saves")
                .toAbsolutePath().normalize();
    }
}
