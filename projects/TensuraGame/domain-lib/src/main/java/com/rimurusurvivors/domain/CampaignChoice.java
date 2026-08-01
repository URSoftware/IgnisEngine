package com.rimurusurvivors.domain;

/** Escolhas narrativas tipadas que podem alterar flags da campanha. */
public enum CampaignChoice {
    ASSESS_VILLAGE_NEEDS("choice.assess_village_needs"),
    PRIORITIZE_VILLAGE_DEFENSE("choice.prioritize_village_defense"),
    SEEK_DWARGON_ARTISANS("choice.seek_dwargon_artisans");

    private final String flagId;

    CampaignChoice(String flagId) {
        this.flagId = flagId;
    }

    public String flagId() {
        return flagId;
    }
}
