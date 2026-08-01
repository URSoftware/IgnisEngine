package com.rimurusurvivors.domain;

/** Estado imutavel de um projeto do Conselho. */
public record TownProjectState(String projectId, TownProjectStatus status) {

    public TownProjectState {
        if (projectId == null || projectId.isBlank() || status == null) {
            throw new IllegalArgumentException("Town project id and status are required.");
        }
    }

    public TownProjectState makeAvailable() {
        return status == TownProjectStatus.LOCKED
                ? new TownProjectState(projectId, TownProjectStatus.AVAILABLE)
                : this;
    }

    public TownProjectState start() {
        if (status == TownProjectStatus.IN_PROGRESS
                || status == TownProjectStatus.COMPLETED) {
            return this;
        }
        if (status != TownProjectStatus.AVAILABLE) {
            throw new IllegalStateException("Locked town project cannot start: " + projectId);
        }
        return new TownProjectState(projectId, TownProjectStatus.IN_PROGRESS);
    }

    public TownProjectState complete() {
        if (status == TownProjectStatus.COMPLETED) {
            return this;
        }
        if (status != TownProjectStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Town project must be in progress before completion: " + projectId);
        }
        return new TownProjectState(projectId, TownProjectStatus.COMPLETED);
    }
}
