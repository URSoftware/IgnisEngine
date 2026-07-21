package com.rimurusurvivors.domain;

/**
 * Classifica o desvio de tempo de uma reacao defensiva em uma {@link ReactionTiming}.
 *
 * <p>O desvio e medido em segundos a partir do instante ideal do telegraph: valor
 * negativo indica reacao antecipada, positivo indica reacao atrasada e zero indica
 * reacao no ponto exato. As faixas sao configuraveis para atender ao modo historia
 * (janelas ampliadas) sem alterar a regra de dano, que continua em {@link ReactionTiming}.
 *
 * <p>Os limites pertencem sempre a faixa mais precisa: um desvio exatamente igual ao
 * raio perfeito e {@code PERFECT}, exatamente igual ao raio bom e {@code GOOD} e
 * exatamente igual ao raio ativo ainda e {@code EARLY}/{@code LATE}. Isso torna as
 * fronteiras deterministicas e testaveis no primeiro e no ultimo instante de cada faixa.
 */
public final class ReactionWindow {

    private static final double DEFAULT_PERFECT_RADIUS = 0.05;
    private static final double DEFAULT_GOOD_RADIUS = 0.12;
    private static final double DEFAULT_ACTIVE_RADIUS = 0.30;
    private static final double STORY_MODE_SCALE = 2.0;

    private final double perfectRadius;
    private final double goodRadius;
    private final double activeRadius;

    public ReactionWindow(double perfectRadius, double goodRadius, double activeRadius) {
        if (!Double.isFinite(perfectRadius) || !Double.isFinite(goodRadius) || !Double.isFinite(activeRadius)) {
            throw new IllegalArgumentException("Os raios da janela de reacao devem ser finitos.");
        }
        if (perfectRadius <= 0.0) {
            throw new IllegalArgumentException("O raio perfeito deve ser positivo.");
        }
        if (goodRadius < perfectRadius) {
            throw new IllegalArgumentException("O raio bom nao pode ser menor que o raio perfeito.");
        }
        if (activeRadius < goodRadius) {
            throw new IllegalArgumentException("O raio ativo nao pode ser menor que o raio bom.");
        }
        this.perfectRadius = perfectRadius;
        this.goodRadius = goodRadius;
        this.activeRadius = activeRadius;
    }

    /** Janela padrao do modo estrategico. */
    public static ReactionWindow standard() {
        return new ReactionWindow(DEFAULT_PERFECT_RADIUS, DEFAULT_GOOD_RADIUS, DEFAULT_ACTIVE_RADIUS);
    }

    /** Janela ampliada para acessibilidade/modo historia, preservando as proporcoes. */
    public static ReactionWindow storyMode() {
        return standard().scaled(STORY_MODE_SCALE);
    }

    /** Retorna uma janela com todos os raios multiplicados pelo fator informado. */
    public ReactionWindow scaled(double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            throw new IllegalArgumentException("O fator de escala deve ser positivo e finito.");
        }
        return new ReactionWindow(perfectRadius * factor, goodRadius * factor, activeRadius * factor);
    }

    /**
     * Classifica uma reacao ocorrida com o desvio informado (em segundos, com sinal).
     * Um desvio fora do raio ativo conta como ausencia de reacao ({@code NONE}).
     */
    public ReactionTiming classify(double offsetSeconds) {
        if (!Double.isFinite(offsetSeconds)) {
            return ReactionTiming.NONE;
        }
        double magnitude = Math.abs(offsetSeconds);
        if (magnitude <= perfectRadius) {
            return ReactionTiming.PERFECT;
        }
        if (magnitude <= goodRadius) {
            return ReactionTiming.GOOD;
        }
        if (magnitude <= activeRadius) {
            return offsetSeconds < 0.0 ? ReactionTiming.EARLY : ReactionTiming.LATE;
        }
        return ReactionTiming.NONE;
    }

    /** Classifica considerando explicitamente se houve entrada; sem reacao e sempre {@code NONE}. */
    public ReactionTiming classifyPress(boolean reacted, double offsetSeconds) {
        return reacted ? classify(offsetSeconds) : ReactionTiming.NONE;
    }

    public double perfectRadius() {
        return perfectRadius;
    }

    public double goodRadius() {
        return goodRadius;
    }

    public double activeRadius() {
        return activeRadius;
    }
}
