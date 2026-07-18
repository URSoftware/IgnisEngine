package com.rimurusurvivors.domain;

/**
 * Arms a manual narrative advance only after the input has been released and a
 * minimum reading time has elapsed.
 */
public final class ManualAdvanceGate {

    private final double minimumSeconds;
    private double elapsedSeconds;
    private boolean armed;
    private boolean consumed;

    public ManualAdvanceGate(double minimumSeconds) {
        if (!Double.isFinite(minimumSeconds) || minimumSeconds < 0) {
            throw new IllegalArgumentException("Minimum time must be finite and non-negative.");
        }
        this.minimumSeconds = minimumSeconds;
    }

    public boolean update(double deltaSeconds, boolean inputDown, boolean inputJustPressed) {
        if (consumed) return false;
        if (Double.isFinite(deltaSeconds) && deltaSeconds > 0) {
            elapsedSeconds += deltaSeconds;
        }
        if (!inputDown) armed = true;
        if (!armed || elapsedSeconds < minimumSeconds || !inputJustPressed) return false;
        consumed = true;
        return true;
    }

    public double elapsedSeconds() {
        return elapsedSeconds;
    }

    public boolean isArmed() {
        return armed;
    }
}
