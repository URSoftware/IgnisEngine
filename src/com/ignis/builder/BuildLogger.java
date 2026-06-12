package com.ignis.builder;

/**
 * Progress sink for build operations. Implemented by the editor's build
 * dialog (UI log) and by a console fallback, keeping the builder module
 * fully decoupled from Swing.
 */
public interface BuildLogger {

    void log(String message);

    default void error(String message) {
        log("[ERROR] " + message);
    }

    /** Console implementation used when no UI is attached. */
    BuildLogger CONSOLE = new BuildLogger() {
        @Override
        public void log(String message) {
            System.out.println("[Builder] " + message);
        }

        @Override
        public void error(String message) {
            System.err.println("[Builder] " + message);
        }
    };
}
