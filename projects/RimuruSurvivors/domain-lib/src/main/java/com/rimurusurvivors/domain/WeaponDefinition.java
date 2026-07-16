package com.rimurusurvivors.domain;

/** Porta de WeaponDefinition (mods/vampire-survivors-rimuru/src/RimuruContent.cs). */
public record WeaponDefinition(
        String id,
        String displayName,
        String summary,
        int maxLevel,
        String evolutionPassiveId) {
}