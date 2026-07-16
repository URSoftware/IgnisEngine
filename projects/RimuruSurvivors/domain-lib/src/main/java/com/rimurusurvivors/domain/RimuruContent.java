package com.rimurusurvivors.domain;

/**
 * Porta de RimuruContent (mods/vampire-survivors-rimuru/src/RimuruContent.cs).
 * Dados fixos de personagem/arma/evolucao/passiva do design original — sem
 * dependencia de engine, so constantes.
 */
public final class RimuruContent {

    private RimuruContent() {
    }

    public static final CharacterDefinition CHARACTER = new CharacterDefinition(
            "rimuru_tempest",
            "Rimuru Tempest",
            "predator_katana",
            "Grande Sabio",
            "+20% Area, +15% Experiencia e -10% Recarga.");

    public static final WeaponDefinition WEAPON = new WeaponDefinition(
            "predator_katana",
            "Predador: Lamina de Rimuru",
            "Katana de energia azul que encadeia cortes e acumula analise contra o alvo.",
            8,
            "great_sage");

    public static final WeaponDefinition EVOLUTION = new WeaponDefinition(
            "beelzebuth_demon_lord",
            "Rei Glutao Beelzebuth",
            "A forma Lorde Demonio abre cortes dimensionais, puxa inimigos e aplica dano continuo.",
            8,
            "great_sage");

    public static final WeaponDefinition THIRD_EVOLUTION = new WeaponDefinition(
            "azathoth_reaper_severance",
            "Azathoth, Void God",
            "Ciel guia cortes do vazio e abre uma janela real para derrotar a Morte.",
            8,
            "ciel");

    public static final PassiveDefinition GREAT_SAGE = new PassiveDefinition(
            "great_sage",
            "Grande Sabio",
            5,
            "analyze_killer_family_and_grant_run_immunity");

    public static final PassiveDefinition CIEL = new PassiveDefinition(
            "ciel",
            "Ciel",
            1,
            "copy_ability_and_counterstrike_analyzed_target");
}