package com.ignis.builder;

/**
 * Platforms supported by the Builder.
 *
 * Each target maps to a build strategy:
 * - JAVA: JVM-compatible distribution (jar + per-platform launcher)
 * - CPP:  native C++ project export for platforms without JVM support
 */
public enum BuildTarget {

    WINDOWS("Windows", Strategy.JAVA, true),
    LINUX("Linux", Strategy.JAVA, true),
    MACOS("macOS", Strategy.JAVA, true),
    XBOX("Xbox", Strategy.CPP, true),
    PLAYSTATION("PlayStation", Strategy.CPP, true),
    NINTENDO_SWITCH("Nintendo Switch", Strategy.CPP, false);

    /** Compilation strategy used for the target. */
    public enum Strategy {
        JAVA, CPP
    }

    private final String displayName;
    private final Strategy strategy;
    private final boolean enabled;

    BuildTarget(String displayName, Strategy strategy, boolean enabled) {
        this.displayName = displayName;
        this.strategy = strategy;
        this.enabled = enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    /** Targets marked as future planning (e.g. Nintendo Switch) are disabled. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Folder-safe identifier, e.g. "nintendo_switch". */
    public String id() {
        return name().toLowerCase();
    }
}
