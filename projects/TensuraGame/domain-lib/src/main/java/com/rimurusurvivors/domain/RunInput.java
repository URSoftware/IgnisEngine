package com.rimurusurvivors.domain;

/**
 * Direcao desejada pelo jogador no frame atual, em coordenadas de <b>mundo</b>:
 * {@code vertical} positivo aponta para CIMA, como o eixo Y da simulacao e do mundo
 * da Ignis (a camera aplica o flip no render).
 */
public record RunInput(double horizontal, double vertical) {

    public static final RunInput NONE = new RunInput(0, 0);

    /**
     * Converte os eixos de <b>tela</b> da engine para o mundo.
     *
     * <p>{@code Input.getVerticalAxis()} segue a convencao de tela, onde Y cresce
     * para BAIXO: cima = -1, baixo = +1. A simulacao usa mundo Y-para-cima. Passar o
     * eixo de tela direto para {@code new RunInput(...)} — que era o que o adaptador
     * fazia — deixava W/Seta-cima empurrando o Rimuru para baixo.</p>
     *
     * <p>Este e o unico ponto de conversao tela -> mundo do jogo. O horizontal tem a
     * mesma direcao nos dois espacos e passa intacto.</p>
     */
    public static RunInput fromScreenAxes(int horizontal, int screenVertical) {
        return new RunInput(horizontal, -screenVertical);
    }

    public RunInput normalized() {
        double length = Math.sqrt(horizontal * horizontal + vertical * vertical);
        if (length <= 1.0 || length == 0.0) {
            return this;
        }
        return new RunInput(horizontal / length, vertical / length);
    }
}
