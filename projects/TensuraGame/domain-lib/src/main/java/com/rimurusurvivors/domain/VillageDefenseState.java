package com.rimurusurvivors.domain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Gerencia o estado de escolha das preparacoes defensivas da Aldeia Goblin.
 *
 * <p>O jogador pode interagir com ate 3 pontos defensivos no mapa da aldeia e
 * selecionar EXATAMENTE 2 preparacoes antes da invasao da matilha dos lobos.
 *
 * <p>Classe imutavel de dominio, pura e sem dependencias de motor.
 */
public final class VillageDefenseState {

    public static final int MAX_PREPARATIONS = 2;

    private final Set<VillagePreparation> selectedPreparations;

    public VillageDefenseState() {
        this.selectedPreparations = Collections.emptySet();
    }

    public VillageDefenseState(Set<VillagePreparation> selected) {
        Objects.requireNonNull(selected, "selected preparations cannot be null");
        if (selected.size() > MAX_PREPARATIONS) {
            throw new IllegalArgumentException("Maximum preparations exceeded: " + selected.size());
        }
        this.selectedPreparations = Collections.unmodifiableSet(new LinkedHashSet<>(selected));
    }

    public Set<VillagePreparation> selectedPreparations() {
        return selectedPreparations;
    }

    public int count() {
        return selectedPreparations.size();
    }

    public boolean isComplete() {
        return selectedPreparations.size() == MAX_PREPARATIONS;
    }

    public boolean isSelected(VillagePreparation preparation) {
        return selectedPreparations.contains(preparation);
    }

    public VillageDefenseState togglePreparation(VillagePreparation preparation) {
        Objects.requireNonNull(preparation, "preparation cannot be null");
        Set<VillagePreparation> updated = new LinkedHashSet<>(selectedPreparations);
        if (updated.contains(preparation)) {
            updated.remove(preparation);
        } else {
            if (updated.size() >= MAX_PREPARATIONS) {
                return this;
            }
            updated.add(preparation);
        }
        return new VillageDefenseState(updated);
    }

    public String[] toPayload() {
        return selectedPreparations.stream()
                .map(Enum::name)
                .toArray(String[]::new);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VillageDefenseState that)) return false;
        return Objects.equals(selectedPreparations, that.selectedPreparations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selectedPreparations);
    }

    @Override
    public String toString() {
        return "VillageDefenseState{selected=" + selectedPreparations + '}';
    }
}
