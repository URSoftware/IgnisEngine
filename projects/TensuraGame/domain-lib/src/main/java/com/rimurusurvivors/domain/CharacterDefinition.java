package com.rimurusurvivors.domain;

/** Porta de CharacterDefinition (mods/vampire-survivors-rimuru/src/RimuruContent.cs). */
public record CharacterDefinition(
        String id,
        String displayName,
        String startingWeaponId,
        String passiveName,
        String passiveSummary) {
}