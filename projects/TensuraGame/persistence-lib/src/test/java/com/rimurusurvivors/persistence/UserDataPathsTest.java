package com.rimurusurvivors.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserDataPathsTest {

    @Test
    void defaultSaveDirectoryIsAbsoluteAndGameScoped() {
        Path path = UserDataPaths.defaultSaveDirectory();

        assertTrue(path.isAbsolute());
        assertEquals("saves", path.getFileName().toString());
        assertTrue(Set.of("TensuraGame", ".tensura-game")
                .contains(path.getParent().getFileName().toString()));
    }
}
