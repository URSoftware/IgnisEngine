package com.rimurusurvivors.domain;

/** Porta de PassiveDefinition (mods/vampire-survivors-rimuru/src/RimuruContent.cs). */
public record PassiveDefinition(
        String id,
        String displayName,
        int maxLevel,
        String revivalBehavior) {
}