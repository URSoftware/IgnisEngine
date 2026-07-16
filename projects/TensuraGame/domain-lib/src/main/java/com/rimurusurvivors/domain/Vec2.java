package com.rimurusurvivors.domain;

/**
 * Substitui System.Numerics.Vector2 do mod C# original (RimuruRuntimeRules.cs).
 * Java nao tem um tipo de vetor 2D embutido; mantido minimo e sem dependencia do motor
 * de proposito, pra essa camada continuar pura como o original.
 */
public record Vec2(double x, double y) {

    public static double distance(Vec2 a, Vec2 b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    public Vec2 plus(Vec2 other) {
        return new Vec2(x + other.x, y + other.y);
    }

    public Vec2 scale(double factor) {
        return new Vec2(x * factor, y * factor);
    }
}