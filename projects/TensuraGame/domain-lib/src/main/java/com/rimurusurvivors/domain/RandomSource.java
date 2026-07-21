package com.rimurusurvivors.domain;

import java.util.Random;

/**
 * Provedor deterministico de valores aleatorios para o dominio de batalha.
 */
public interface RandomSource {

    double nextDouble();

    int nextInt(int bound);

    static RandomSource seeded(long seed) {
        Random random = new Random(seed);
        return new RandomSource() {
            @Override
            public double nextDouble() {
                return random.nextDouble();
            }

            @Override
            public int nextInt(int bound) {
                return random.nextInt(bound);
            }
        };
    }

    static RandomSource fixed(double value) {
        return new RandomSource() {
            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return (int) (value * bound);
            }
        };
    }
}
