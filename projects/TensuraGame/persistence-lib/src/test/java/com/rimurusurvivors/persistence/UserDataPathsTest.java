package com.rimurusurvivors.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserDataPathsTest {

    private static final String LOCAL_APP_DATA = Path.of("C:", "AppData").toString();
    private static final String USER_HOME = Path.of("C:", "Users", "qa").toString();
    private static final Path ISOLATED = Path.of("D:", "TensuraGame-qa", "saves");

    @Test
    void defaultSaveDirectoryIsAbsoluteAndGameScoped() {
        Path path = UserDataPaths.defaultSaveDirectory();

        assertTrue(path.isAbsolute());
        assertEquals("saves", path.getFileName().toString());
        assertTrue(Set.of("TensuraGame", ".tensura-game")
                .contains(path.getParent().getFileName().toString()));
    }

    @Test
    void systemPropertyWinsOverEveryOtherSource() {
        Path resolved = UserDataPaths.resolveSaveDirectory(
                ISOLATED.toString(),
                Path.of("E:", "ignorado").toString(),
                LOCAL_APP_DATA,
                USER_HOME);

        assertEquals(ISOLATED, resolved);
    }

    @Test
    void environmentWinsOverTheUserDataFallback() {
        Path resolved = UserDataPaths.resolveSaveDirectory(
                null, ISOLATED.toString(), LOCAL_APP_DATA, USER_HOME);

        assertEquals(ISOLATED, resolved);
    }

    @Test
    void blankOverridesAreIgnoredAndFallBackToLocalAppData() {
        Path resolved = UserDataPaths.resolveSaveDirectory(
                "   ", "", LOCAL_APP_DATA, USER_HOME);

        assertEquals(Path.of(LOCAL_APP_DATA, "TensuraGame", "saves"), resolved);
    }

    @Test
    void userHomeIsTheLastResortWhenLocalAppDataIsAbsent() {
        Path resolved = UserDataPaths.resolveSaveDirectory(null, null, null, USER_HOME);

        assertEquals(Path.of(USER_HOME, ".tensura-game", "saves"), resolved);
    }

    @Test
    void overrideIsNormalizedToAnAbsolutePath() {
        Path resolved = UserDataPaths.resolveSaveDirectory(
                Path.of("D:", "qa", "..", "TensuraGame-qa", "saves").toString(),
                null,
                LOCAL_APP_DATA,
                USER_HOME);

        assertTrue(resolved.isAbsolute());
        assertEquals(ISOLATED, resolved);
    }

    @Test
    void missingEverySourceFailsLoudlyInsteadOfGuessing() {
        assertThrows(
                IllegalStateException.class,
                () -> UserDataPaths.resolveSaveDirectory(null, null, null, null));
    }

    @Test
    void defaultSaveDirectoryHonorsTheSystemPropertyAtRuntime() {
        String previous = System.getProperty(UserDataPaths.SAVE_DIR_PROPERTY);
        System.setProperty(UserDataPaths.SAVE_DIR_PROPERTY, ISOLATED.toString());
        try {
            assertEquals(ISOLATED, UserDataPaths.defaultSaveDirectory());
        } finally {
            if (previous == null) {
                System.clearProperty(UserDataPaths.SAVE_DIR_PROPERTY);
            } else {
                System.setProperty(UserDataPaths.SAVE_DIR_PROPERTY, previous);
            }
        }
    }
}
