package com.rimurusurvivors.domain;

/** Recursos agregados de Tempest, sem inventario de unidades individuais. */
public record TownResourceBundle(
        int wood,
        int stone,
        int food,
        int cloth,
        int metal,
        int magicules) {

    public static final TownResourceBundle EMPTY =
            new TownResourceBundle(0, 0, 0, 0, 0, 0);

    public TownResourceBundle {
        if (wood < 0 || stone < 0 || food < 0
                || cloth < 0 || metal < 0 || magicules < 0) {
            throw new IllegalArgumentException("Town resources must not be negative.");
        }
    }

    public TownResourceBundle add(TownResourceBundle other) {
        requireBundle(other);
        return new TownResourceBundle(
                Math.addExact(wood, other.wood),
                Math.addExact(stone, other.stone),
                Math.addExact(food, other.food),
                Math.addExact(cloth, other.cloth),
                Math.addExact(metal, other.metal),
                Math.addExact(magicules, other.magicules));
    }

    public boolean covers(TownResourceBundle cost) {
        requireBundle(cost);
        return wood >= cost.wood
                && stone >= cost.stone
                && food >= cost.food
                && cloth >= cost.cloth
                && metal >= cost.metal
                && magicules >= cost.magicules;
    }

    public TownResourceBundle subtract(TownResourceBundle cost) {
        if (!covers(cost)) {
            throw new IllegalStateException("Town has insufficient resources.");
        }
        return new TownResourceBundle(
                wood - cost.wood,
                stone - cost.stone,
                food - cost.food,
                cloth - cost.cloth,
                metal - cost.metal,
                magicules - cost.magicules);
    }

    private static void requireBundle(TownResourceBundle bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("Town resource bundle is required.");
        }
    }
}
