package com.rimurusurvivors.domain;

/** Direcao normalizada desejada pelo jogador no frame atual. */
public record RunInput(double horizontal, double vertical) {

    public static final RunInput NONE = new RunInput(0, 0);

    public RunInput normalized() {
        double length = Math.sqrt(horizontal * horizontal + vertical * vertical);
        if (length <= 1.0 || length == 0.0) {
            return this;
        }
        return new RunInput(horizontal / length, vertical / length);
    }
}
