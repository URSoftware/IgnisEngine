package com.rimurusurvivors.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Porta completa de RimuruRunState (mods/vampire-survivors-rimuru/src/RimuruRuntimeRules.cs).
 * Logica pura, sem nenhuma dependencia do IgnisEngine — assim como o original nao dependia
 * do Unity. A fatia vertical so chama InitializeForm/TryUnlockHumanoid indiretamente (fica
 * sempre Slime); os metodos de Humanoid/DemonLord/Ciel/Azathoth existem e compilam desde
 * o dia 1 para nao virar porta de logica nova quando essa parte entrar depois.
 *
 * <p>O original em C# compara enemyFamilyId com StringComparer.OrdinalIgnoreCase; aqui isso
 * e replicado normalizando toda chave de familia para minusculo antes de guardar/consultar.
 */
public final class RimuruRunState {

    private static final Set<String> REAPER_FAMILIES = new HashSet<>();
    static {
        REAPER_FAMILIES.add("red_reaper");
        REAPER_FAMILIES.add("death");
    }

    private static String normalizeFamilyId(String enemyFamilyId) {
        return enemyFamilyId.toLowerCase(java.util.Locale.ROOT);
    }

    private final Set<String> immuneEnemyFamilies = new HashSet<>();
    private final Map<String, CopiedEnemyAbility> copiedAbilities = new HashMap<>();

    private RimuruForm form = RimuruForm.SLIME;
    private boolean isCiel;
    private boolean rangaSummoned;
    private int tempestCompanionCount;
    private boolean hasAzathoth;
    private int revivalsAnalyzed;

    public RimuruForm getForm() {
        return form;
    }

    public boolean isDemonLord() {
        return form == RimuruForm.DEMON_LORD;
    }

    public boolean isCiel() {
        return isCiel;
    }

    public boolean isRangaSummoned() {
        return rangaSummoned;
    }

    public int getTempestCompanionCount() {
        return tempestCompanionCount;
    }

    public boolean hasAzathoth() {
        return hasAzathoth;
    }

    public int getRevivalsAnalyzed() {
        return revivalsAnalyzed;
    }

    public Set<String> getImmuneEnemyFamilies() {
        return Collections.unmodifiableSet(immuneEnemyFamilies);
    }

    public Map<String, CopiedEnemyAbility> getCopiedAbilities() {
        return Collections.unmodifiableMap(copiedAbilities);
    }

    public void initializeForm(RimuruForm form) {
        this.form = form;
    }

    public boolean tryUnlockHumanoid(int characterLevel) {
        if (form != RimuruForm.SLIME || characterLevel < 20) {
            return false;
        }
        form = RimuruForm.HUMANOID;
        return true;
    }

    public boolean trySummonRanga(int predatorLevel) {
        if (rangaSummoned || predatorLevel < 4) {
            return false;
        }
        rangaSummoned = true;
        return true;
    }

    public boolean trySummonTempestCompanions(int characterLevel) {
        if (form == RimuruForm.SLIME || tempestCompanionCount > 0 || characterLevel < 35) {
            return false;
        }
        tempestCompanionCount = 2;
        return true;
    }

    public boolean tryEvolveDemonLord(int weaponLevel, int passiveLevel, boolean treasureOpened) {
        if (isDemonLord() || form == RimuruForm.SLIME || weaponLevel < 8 || passiveLevel < 5 || !treasureOpened) {
            return false;
        }
        form = RimuruForm.DEMON_LORD;
        return true;
    }

    public boolean tryEvolveDemonLordStable(int characterLevel, int weaponLevel, int passiveLevel) {
        if (characterLevel < 40) {
            return false;
        }
        return tryEvolveDemonLord(weaponLevel, passiveLevel, true);
    }

    public boolean tryAwakenCielFromCombatAnalysis(int characterLevel, int beelzebuthLevel) {
        if (!isDemonLord() || isCiel || characterLevel < 55 || beelzebuthLevel < 6) {
            return false;
        }
        isCiel = true;
        return true;
    }

    public boolean tryEvolveAzathoth(int beelzebuthLevel, boolean treasureOpened) {
        if (!isDemonLord() || !isCiel || !rangaSummoned || hasAzathoth || beelzebuthLevel < 8 || !treasureOpened) {
            return false;
        }
        hasAzathoth = true;
        return true;
    }

    public boolean tryEvolveAzathothStable(int characterLevel, int beelzebuthLevel) {
        if (characterLevel < 60) {
            return false;
        }
        return tryEvolveAzathoth(beelzebuthLevel, true);
    }

    public RevivalAnalysisResult analyzeRevival(String enemyFamilyId, String enemyAbilityId) {
        if (enemyFamilyId == null || enemyFamilyId.isBlank()) {
            throw new IllegalArgumentException("Enemy family id is required.");
        }
        if (enemyAbilityId == null || enemyAbilityId.isBlank()) {
            throw new IllegalArgumentException("Enemy ability id is required.");
        }

        String familyKey = normalizeFamilyId(enemyFamilyId);
        revivalsAnalyzed++;
        boolean firstAnalysis = immuneEnemyFamilies.add(familyKey);
        if (isDemonLord()) {
            isCiel = true;
            copiedAbilities.putIfAbsent(
                    familyKey, new CopiedEnemyAbility(enemyFamilyId, enemyAbilityId, true));
        }

        return new RevivalAnalysisResult(enemyFamilyId, firstAnalysis, true, isCiel, isCiel);
    }

    public boolean isImmuneTo(String enemyFamilyId) {
        return immuneEnemyFamilies.contains(normalizeFamilyId(enemyFamilyId));
    }

    public boolean canCounterstrike(String enemyFamilyId) {
        return isCiel && copiedAbilities.containsKey(normalizeFamilyId(enemyFamilyId));
    }

    public boolean canApplyReaperSeverance(String enemyFamilyId) {
        return hasAzathoth && REAPER_FAMILIES.contains(normalizeFamilyId(enemyFamilyId));
    }

    public boolean canExecuteDeath(String enemyFamilyId, float healthRatio) {
        return canApplyReaperSeverance(enemyFamilyId) && healthRatio > 0 && healthRatio <= 0.01f;
    }
}