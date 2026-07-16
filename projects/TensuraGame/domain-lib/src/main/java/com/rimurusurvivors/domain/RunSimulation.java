package com.rimurusurvivors.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Simulacao deterministica do combate principal. Nao conhece a Ignis: entrada,
 * render, som e camera sao responsabilidades do adaptador do projeto.
 */
public final class RunSimulation {

    private static final double ARENA_LIMIT = 1_900.0;
    private static final double PLAYER_RADIUS = 16.0;
    private static final double REAPER_SPAWN_SECONDS = 300.0;

    // Teto de orbes simultaneos na arena e distancia em que dois orbes viram um so.
    // Ambos preservam a experiencia total (ver spawnOrb): limitam quantos OBJETOS
    // representam a mesma experiencia, nao quanta experiencia existe.
    private static final int MAX_ORBS = 120;
    private static final double ORB_MERGE_DISTANCE = 18;

    private final WeaponProgression progression;
    private final RimuruRunState rimuru = new RimuruRunState();
    private final Random random;
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Orb> orbs = new ArrayList<>();
    private final List<RunEvent> events = new ArrayList<>();

    private long nextId = 1;
    private double elapsed;
    private double playerX;
    private double playerY;
    private boolean playerMoving;
    private double health = 120.0;
    private double maxHealth = 120.0;
    private double spawnTimer;
    private double attackTimer;
    private double skillTimer;
    private double rangaTimer;
    private double rangaX = -36.0;
    private double rangaY = 20.0;
    private int level = 1;
    private int experience;
    private int experienceToNext = 6;
    private int kills;
    private int weaponLevel = 1;
    private int passiveLevel = 1;
    private int regenerationLevel = 1;
    private int pendingUpgrades;
    private boolean reaperSpawned;
    private boolean gameOver;
    private boolean victory;

    public RunSimulation(WeaponProgression progression) {
        this(progression, 0x52494D55L);
    }

    public RunSimulation(WeaponProgression progression, long seed) {
        if (progression == null) {
            throw new IllegalArgumentException("Weapon progression is required.");
        }
        this.progression = progression;
        this.random = new Random(seed);
    }

    public RunSnapshot update(double deltaSeconds, RunInput input) {
        events.clear();
        if (deltaSeconds <= 0 || gameOver || victory) {
            return snapshot();
        }
        if (pendingUpgrades > 0) {
            return snapshot();
        }

        double dt = Math.min(deltaSeconds, 0.05);
        elapsed += dt;
        movePlayer(dt, input == null ? RunInput.NONE : input.normalized());
        regenerate(dt);
        updateProgression();
        spawnEnemies(dt);
        updateEnemies(dt);
        updateRanga(dt);
        updateAttacks(dt);
        updateProjectiles(dt);
        updateOrbs(dt);
        checkEndState();
        return snapshot();
    }

    /** Utilitario de teste e de depuracao do editor para validar as transformacoes. */
    public void grantExperience(int amount) {
        if (amount <= 0) return;
        experience += amount;
        resolveLevels();
    }

    public boolean chooseUpgrade(UpgradeChoice choice) {
        if (pendingUpgrades <= 0 || choice == null) return false;
        boolean applied = switch (choice) {
            case PREDATOR -> increaseWeaponLevel();
            case GREAT_SAGE -> increasePassiveLevel();
            case REGENERATION -> increaseRegenerationLevel();
        };
        if (!applied) return false;
        pendingUpgrades--;
        if (!hasAvailableUpgrade()) pendingUpgrades = 0;
        updateProgression();
        events.add(new RunEvent(RunEventType.UPGRADE_SELECTED, choice.name(), playerX, playerY));
        return true;
    }

    public RunSnapshot snapshot() {
        List<WorldEntitySnapshot> visible = new ArrayList<>();
        for (Enemy enemy : enemies) visible.add(enemy.snapshot());
        for (Projectile projectile : projectiles) visible.add(projectile.snapshot());
        for (Orb orb : orbs) visible.add(orb.snapshot());
        if (rimuru.isRangaSummoned()) {
            visible.add(new WorldEntitySnapshot(0, WorldEntityKind.RANGA, rangaX, rangaY, 0, 1, false));
        }
        return new RunSnapshot(
                elapsed, playerX, playerY, health, maxHealth, level, experience,
                experienceToNext, kills, weaponLevel, passiveLevel, regenerationLevel,
                pendingUpgrades, rimuru.getForm(), playerMoving,
                rimuru.isRangaSummoned(), rimuru.isCiel(), rimuru.hasAzathoth(),
                gameOver, victory, List.copyOf(visible), List.copyOf(events));
    }

    private void movePlayer(double dt, RunInput input) {
        double speed = switch (rimuru.getForm()) {
            case SLIME -> 145.0;
            case HUMANOID -> 175.0;
            case DEMON_LORD -> 195.0;
        };
        // Locomocao e ESTADO continuo, nao um pulso: fica no snapshot e nao na lista
        // de eventos. Emitir SLIME_MOVING/SLIME_IDLE a cada tick geraria lixo 60x/s
        // — justo o que o trabalho de estabilidade removeu. O apresentador le este
        // booleano e escolhe entre o clipe de andar e o de repouso.
        playerMoving = input.horizontal() != 0 || input.vertical() != 0;
        playerX = clamp(playerX + input.horizontal() * speed * dt, -ARENA_LIMIT, ARENA_LIMIT);
        playerY = clamp(playerY + input.vertical() * speed * dt, -ARENA_LIMIT, ARENA_LIMIT);
    }

    private void regenerate(double dt) {
        double regeneration = switch (rimuru.getForm()) {
            case SLIME -> 1.8;
            case HUMANOID -> 2.4;
            case DEMON_LORD -> 4.0;
        } + (regenerationLevel - 1) * 0.9;
        health = Math.min(maxHealth, health + regeneration * dt);
    }

    private void updateProgression() {
        RimuruForm before = rimuru.getForm();
        rimuru.tryUnlockHumanoid(level);

        if (rimuru.trySummonRanga(weaponLevel)) {
            events.add(new RunEvent(RunEventType.RANGA_SUMMONED, "Ranga", playerX, playerY));
        }
        rimuru.tryEvolveDemonLordStable(level, weaponLevel, passiveLevel);
        if (rimuru.tryAwakenCielFromCombatAnalysis(level, weaponLevel)) {
            events.add(new RunEvent(RunEventType.CIEL_AWAKENED, "Ciel", playerX, playerY));
        }
        if (rimuru.tryEvolveAzathothStable(level, weaponLevel)) {
            events.add(new RunEvent(RunEventType.AZATHOTH_AWAKENED, "Azathoth", playerX, playerY));
        }
        if (before != rimuru.getForm()) {
            maxHealth += rimuru.isDemonLord() ? 80 : 35;
            health = maxHealth;
            events.add(new RunEvent(RunEventType.FORM_CHANGED, rimuru.getForm().name(), playerX, playerY));
        }
    }

    private void spawnEnemies(double dt) {
        spawnTimer -= dt;
        if (spawnTimer <= 0 && enemies.size() < 110) {
            int count = 1 + Math.min(4, (int) (elapsed / 75));
            for (int i = 0; i < count; i++) spawnEnemy(chooseEnemyKind(), false);
            spawnTimer = Math.max(0.24, 1.15 - elapsed / 420.0);
        }
        if (!reaperSpawned && elapsed >= REAPER_SPAWN_SECONDS) {
            reaperSpawned = true;
            spawnEnemy(WorldEntityKind.RED_REAPER, true);
            events.add(new RunEvent(RunEventType.BOSS_SPAWNED, "Morte Vermelha", playerX, playerY));
        }
    }

    private WorldEntityKind chooseEnemyKind() {
        double roll = random.nextDouble();
        if (elapsed > 150 && roll < 0.18) return WorldEntityKind.FLAME_SPIRIT;
        if (elapsed > 70 && roll < 0.42) return WorldEntityKind.ORC;
        if (elapsed > 25 && roll < 0.68) return WorldEntityKind.DIRE_WOLF;
        return WorldEntityKind.GOBLIN;
    }

    private void spawnEnemy(WorldEntityKind kind, boolean boss) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = boss ? 520.0 : 390.0 + random.nextDouble() * 120.0;
        double x = playerX + Math.cos(angle) * distance;
        double y = playerY + Math.sin(angle) * distance;
        enemies.add(Enemy.create(nextId++, kind, x, y, elapsed, boss));
    }

    private void updateEnemies(double dt) {
        for (Enemy enemy : enemies) {
            double dx = playerX - enemy.x;
            double dy = playerY - enemy.y;
            double distance = Math.max(0.001, Math.sqrt(dx * dx + dy * dy));
            enemy.x += dx / distance * enemy.speed * dt;
            enemy.y += dy / distance * enemy.speed * dt;
            enemy.contactCooldown = Math.max(0, enemy.contactCooldown - dt);

            if (distance <= PLAYER_RADIUS + enemy.radius && enemy.contactCooldown == 0) {
                double resistance = switch (rimuru.getForm()) {
                    case SLIME -> 0.72;
                    case HUMANOID -> 0.62;
                    case DEMON_LORD -> 0.38;
                };
                health -= enemy.damage * resistance;
                enemy.contactCooldown = 0.75;
                events.add(new RunEvent(RunEventType.PLAYER_HIT, enemy.familyId, playerX, playerY));
            }
        }
    }

    private void updateRanga(double dt) {
        if (!rimuru.isRangaSummoned()) return;
        double targetX = playerX - 42;
        double targetY = playerY + 24;
        rangaX += (targetX - rangaX) * Math.min(1, dt * 7.0);
        rangaY += (targetY - rangaY) * Math.min(1, dt * 7.0);
        rangaTimer -= dt;
        if (rangaTimer > 0) return;
        Enemy target = nearestEnemy(rangaX, rangaY, 260);
        if (target != null) {
            double multiplier = rimuru.hasAzathoth() ? 1.5 : 1.0;
            damageEnemy(target, (18 + level * 0.8) * multiplier);
            events.add(new RunEvent(RunEventType.RANGA_ATTACK, "Ranga: Presa Tempestuosa", target.x, target.y));
        }
        rangaTimer = 0.72;
    }

    private void updateAttacks(double dt) {
        attackTimer -= dt;
        skillTimer -= dt;
        if (attackTimer <= 0) {
            firePrimaryWeapon();
            double baseCooldown = progression.statsAtLevel(weaponLevel).cooldown();
            attackTimer = Math.max(0.22, baseCooldown * formCooldownMultiplier());
        }
        if (skillTimer <= 0) {
            fireFormSkill();
            skillTimer = switch (rimuru.getForm()) {
                case SLIME -> 3.2;
                case HUMANOID -> 2.7;
                case DEMON_LORD -> rimuru.hasAzathoth() ? 1.1 : 1.8;
            };
        }
    }

    private double formCooldownMultiplier() {
        if (rimuru.hasAzathoth()) return 0.32;
        if (rimuru.isDemonLord()) return 0.50;
        if (rimuru.getForm() == RimuruForm.HUMANOID) return 0.72;
        return 0.88;
    }

    private void firePrimaryWeapon() {
        Enemy target = chooseSageTarget(540);
        if (target == null) return;
        WeaponProgression.ResolvedStats stats = progression.statsAtLevel(weaponLevel);
        int amount = Math.max(1, stats.amount());
        for (int i = 0; i < amount; i++) {
            double spread = (i - (amount - 1) / 2.0) * 0.10;
            double angle = Math.atan2(target.y - playerY, target.x - playerX) + spread;
            WorldEntityKind kind = primaryProjectileKind();
            double speed = kind == WorldEntityKind.VOID_CUT ? 510 : 360 + stats.speed() * 150;
            double damage = stats.damage() * formDamageMultiplier();
            int pierce = 1 + Math.max(0, stats.pierce());
            projectiles.add(new Projectile(nextId++, kind, playerX, playerY,
                    Math.cos(angle) * speed, Math.sin(angle) * speed, damage, pierce, 1.8));
        }
        events.add(new RunEvent(RunEventType.ATTACK, primaryAttackName(), playerX, playerY));
    }

    private WorldEntityKind primaryProjectileKind() {
        if (rimuru.hasAzathoth()) return WorldEntityKind.VOID_CUT;
        if (rimuru.isDemonLord()) return WorldEntityKind.KATANA_CUT;
        if (rimuru.getForm() == RimuruForm.HUMANOID) return WorldEntityKind.KATANA_CUT;
        return WorldEntityKind.WATER_BLADE;
    }

    private String primaryAttackName() {
        if (rimuru.hasAzathoth()) return "Azathoth: Corte do Vazio";
        if (rimuru.isDemonLord()) return "Beelzebuth: Lamina Devoradora";
        if (rimuru.getForm() == RimuruForm.HUMANOID) return "Katana de Agua";
        return "Hidrolamina";
    }

    private double formDamageMultiplier() {
        if (rimuru.hasAzathoth()) return 4.8;
        if (rimuru.isDemonLord()) return 2.4;
        if (rimuru.getForm() == RimuruForm.HUMANOID) return 1.45;
        return 1.0;
    }

    private void fireFormSkill() {
        switch (rimuru.getForm()) {
            case SLIME -> predatorPulse();
            case HUMANOID -> blackLightning();
            case DEMON_LORD -> beelzebuthPulse();
        }
    }

    private void predatorPulse() {
        projectiles.add(new Projectile(nextId++, WorldEntityKind.PREDATOR_MAW,
                playerX, playerY, 0, 0, 24 + level, 99, 0.32));
        damageEnemiesInRadius(playerX, playerY, 105, 24 + level, false);
        events.add(new RunEvent(RunEventType.PREDATOR_CAST, "Predador", playerX, playerY));
    }

    private void blackLightning() {
        enemies.stream()
                .sorted(Comparator.comparingDouble(e -> distance(playerX, playerY, e.x, e.y)))
                .limit(4)
                .forEach(enemy -> {
                    projectiles.add(new Projectile(nextId++, WorldEntityKind.BLACK_LIGHTNING,
                            enemy.x, enemy.y, 0, 0, 0, 0, 0.18));
                    damageEnemy(enemy, 42 + level * 1.3);
                });
        events.add(new RunEvent(RunEventType.ATTACK, "Relampago Negro", playerX, playerY));
    }

    private void beelzebuthPulse() {
        double radius = rimuru.hasAzathoth() ? 260 : 190;
        double damage = rimuru.hasAzathoth() ? 95 + level * 2.0 : 62 + level * 1.4;
        damageEnemiesInRadius(playerX, playerY, radius, damage, true);
        projectiles.add(new Projectile(nextId++, rimuru.hasAzathoth()
                ? WorldEntityKind.VOID_CUT : WorldEntityKind.PREDATOR_MAW,
                playerX, playerY, 0, 0, 0, 0, 0.45));
        events.add(new RunEvent(RunEventType.ATTACK,
                rimuru.hasAzathoth() ? "Azathoth: Colapso do Vazio" : "Beelzebuth", playerX, playerY));
    }

    private void damageEnemiesInRadius(double x, double y, double radius, double damage, boolean pull) {
        for (Enemy enemy : enemies) {
            double distance = distance(x, y, enemy.x, enemy.y);
            if (distance > radius) continue;
            damageEnemy(enemy, damage);
            if (pull && distance > 1) {
                enemy.x += (x - enemy.x) / distance * 22;
                enemy.y += (y - enemy.y) / distance * 22;
            }
        }
    }

    private void updateProjectiles(double dt) {
        Iterator<Projectile> iterator = projectiles.iterator();
        while (iterator.hasNext()) {
            Projectile projectile = iterator.next();
            projectile.x += projectile.vx * dt;
            projectile.y += projectile.vy * dt;
            projectile.life -= dt;
            if (projectile.damage > 0 && (projectile.vx != 0 || projectile.vy != 0)) {
                for (Enemy enemy : enemies) {
                    if (projectile.hitIds.contains(enemy.id)) continue;
                    if (distance(projectile.x, projectile.y, enemy.x, enemy.y) > enemy.radius + 12) continue;
                    projectile.hitIds.add(enemy.id);
                    damageEnemy(enemy, projectile.damage);
                    projectile.pierce--;
                    if (projectile.pierce <= 0) break;
                }
            }
            if (projectile.life <= 0 || projectile.pierce <= 0) iterator.remove();
        }
        removeDefeatedEnemies();
    }

    private void damageEnemy(Enemy enemy, double damage) {
        if (enemy.health <= 0) return;
        if (enemy.kind == WorldEntityKind.RED_REAPER && !rimuru.hasAzathoth()) {
            damage *= 0.015;
        }
        if (enemy.kind == WorldEntityKind.RED_REAPER && rimuru.hasAzathoth()) {
            damage += enemy.maxHealth * 0.03;
        }
        enemy.health -= damage;
        if (enemy.kind == WorldEntityKind.RED_REAPER
                && rimuru.canExecuteDeath(enemy.familyId, (float) Math.max(0, enemy.health / enemy.maxHealth))) {
            enemy.health = 0;
        }
    }

    private void removeDefeatedEnemies() {
        Iterator<Enemy> iterator = enemies.iterator();
        while (iterator.hasNext()) {
            Enemy enemy = iterator.next();
            if (enemy.health > 0) continue;
            iterator.remove();
            kills++;
            spawnOrb(enemy.x, enemy.y, enemy.experience);
            events.add(new RunEvent(RunEventType.ENEMY_DEFEATED, enemy.familyId, enemy.x, enemy.y));
            if (enemy.kind == WorldEntityKind.RED_REAPER) victory = true;
        }
    }

    /**
     * Cria o orbe de um inimigo derrotado, coalescendo com um orbe vizinho quando
     * houver e respeitando o teto de orbes na arena.
     *
     * <p>Orbes so somem quando o jogador encosta neles (raio de coleta 20); os que
     * caem longe do ima ficam na arena para sempre. Numa run longa isso crescia sem
     * limite — e cada orbe custa um GameObject visual no motor, um dos fatores da
     * pressao de memoria que derrubou o editor em 15/07/2026.</p>
     *
     * <p><b>O valor total e sempre conservado:</b> coalescer soma o valor no orbe que
     * fica. O jogador nao perde experiencia — muda so quantos objetos representam a
     * mesma experiencia na tela.</p>
     */
    void spawnOrb(double x, double y, int value) {
        for (Orb orb : orbs) {
            double dx = orb.x - x;
            double dy = orb.y - y;
            if (dx * dx + dy * dy <= ORB_MERGE_DISTANCE * ORB_MERGE_DISTANCE) {
                orb.value += value;
                return;
            }
        }
        orbs.add(new Orb(nextId++, x, y, value));
        enforceOrbCap();
    }

    /** Quantos orbes existem na arena (o que o teto limita). */
    int orbCount() {
        return orbs.size();
    }

    /**
     * Experiencia total ainda no chao, somando todos os orbes. E o invariante que o
     * teto e a coalescencia NAO podem violar: eles mudam quantos objetos existem,
     * nunca quanta experiencia eles valem.
     */
    int totalOrbValue() {
        int total = 0;
        for (Orb orb : orbs) total += orb.value;
        return total;
    }

    /**
     * Mantem o numero de orbes no teto: o orbe mais distante do jogador (o que menos
     * chance tem de ser coletado logo) cede o valor ao vizinho mais proximo dele.
     */
    private void enforceOrbCap() {
        while (orbs.size() > MAX_ORBS) {
            int farthestIndex = 0;
            double farthestDistance = -1;
            for (int i = 0; i < orbs.size(); i++) {
                Orb orb = orbs.get(i);
                double dx = playerX - orb.x;
                double dy = playerY - orb.y;
                double distance = dx * dx + dy * dy;
                if (distance > farthestDistance) {
                    farthestDistance = distance;
                    farthestIndex = i;
                }
            }
            Orb victim = orbs.remove(farthestIndex);

            int hostIndex = -1;
            double hostDistance = Double.MAX_VALUE;
            for (int i = 0; i < orbs.size(); i++) {
                Orb orb = orbs.get(i);
                double dx = orb.x - victim.x;
                double dy = orb.y - victim.y;
                double distance = dx * dx + dy * dy;
                if (distance < hostDistance) {
                    hostDistance = distance;
                    hostIndex = i;
                }
            }
            // Com MAX_ORBS >= 1 e size > MAX_ORBS sempre sobra pelo menos um host.
            orbs.get(hostIndex).value += victim.value;
        }
    }

    private void updateOrbs(double dt) {
        double magnet = 95 + passiveLevel * 25;
        Iterator<Orb> iterator = orbs.iterator();
        while (iterator.hasNext()) {
            Orb orb = iterator.next();
            double dx = playerX - orb.x;
            double dy = playerY - orb.y;
            double distance = Math.max(0.001, Math.sqrt(dx * dx + dy * dy));
            if (distance < magnet) {
                double speed = 150 + (magnet - distance) * 4;
                orb.x += dx / distance * speed * dt;
                orb.y += dy / distance * speed * dt;
            }
            if (distance <= 20) {
                experience += orb.value;
                iterator.remove();
            }
        }
        resolveLevels();
    }

    private void resolveLevels() {
        while (experience >= experienceToNext) {
            experience -= experienceToNext;
            level++;
            if (hasAvailableUpgrade()) pendingUpgrades++;
            experienceToNext = 5 + (int) Math.round(level * 2.2);
            health = Math.min(maxHealth, health + 18);
            events.add(new RunEvent(RunEventType.LEVEL_UP, Integer.toString(level), playerX, playerY));
        }
        updateProgression();
    }

    private boolean increaseWeaponLevel() {
        if (weaponLevel >= progression.maxLevel()) return false;
        weaponLevel++;
        return true;
    }

    private boolean increasePassiveLevel() {
        if (passiveLevel >= 5) return false;
        passiveLevel++;
        return true;
    }

    private boolean increaseRegenerationLevel() {
        if (regenerationLevel >= 8) return false;
        regenerationLevel++;
        maxHealth += 6;
        health = Math.min(maxHealth, health + 14);
        return true;
    }

    private boolean hasAvailableUpgrade() {
        return weaponLevel < progression.maxLevel() || passiveLevel < 5 || regenerationLevel < 8;
    }

    private Enemy chooseSageTarget(double range) {
        List<TargetSnapshot> targets = enemies.stream()
                .filter(enemy -> enemy.health > 0)
                .map(enemy -> new TargetSnapshot(
                        enemy.familyId, new Vec2(enemy.x, enemy.y), new Vec2(0, 0),
                        (float) (enemy.health / enemy.maxHealth), enemy.threat, enemy.boss))
                .toList();
        TargetSnapshot selected = GreatSageAimAssist.chooseTarget(
                targets, new Vec2(playerX, playerY), range, true);
        if (selected == null) return null;
        return enemies.stream()
                .filter(enemy -> enemy.familyId.equals(selected.enemyFamilyId()))
                .min(Comparator.comparingDouble(enemy -> distance(enemy.x, enemy.y,
                        selected.position().x(), selected.position().y())))
                .orElse(null);
    }

    private Enemy nearestEnemy(double x, double y, double maxDistance) {
        return enemies.stream()
                .filter(enemy -> enemy.health > 0 && distance(x, y, enemy.x, enemy.y) <= maxDistance)
                .min(Comparator.comparingDouble(enemy -> distance(x, y, enemy.x, enemy.y)))
                .orElse(null);
    }

    private void checkEndState() {
        if (health <= 0) {
            health = 0;
            gameOver = true;
            events.add(new RunEvent(RunEventType.GAME_OVER, "Rimuru foi derrotado", playerX, playerY));
        } else if (victory) {
            events.add(new RunEvent(RunEventType.VICTORY, "A Morte foi derrotada", playerX, playerY));
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double distance(double ax, double ay, double bx, double by) {
        double dx = ax - bx;
        double dy = ay - by;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static final class Enemy {
        private final long id;
        private final WorldEntityKind kind;
        private final String familyId;
        private final double maxHealth;
        private final double speed;
        private final double damage;
        private final double radius;
        private final int experience;
        private final boolean threat;
        private final boolean boss;
        private double x;
        private double y;
        private double health;
        private double contactCooldown;

        private Enemy(long id, WorldEntityKind kind, String familyId, double x, double y,
                double maxHealth, double speed, double damage, double radius,
                int experience, boolean threat, boolean boss) {
            this.id = id;
            this.kind = kind;
            this.familyId = familyId;
            this.x = x;
            this.y = y;
            this.maxHealth = maxHealth;
            this.health = maxHealth;
            this.speed = speed;
            this.damage = damage;
            this.radius = radius;
            this.experience = experience;
            this.threat = threat;
            this.boss = boss;
        }

        private static Enemy create(long id, WorldEntityKind kind, double x, double y,
                double elapsed, boolean boss) {
            double scaling = 1.0 + elapsed / 220.0;
            return switch (kind) {
                case GOBLIN -> new Enemy(id, kind, "goblin", x, y, 28 * scaling, 58, 8, 14, 2, false, false);
                case DIRE_WOLF -> new Enemy(id, kind, "dire_wolf", x, y, 42 * scaling, 88, 11, 15, 3, true, false);
                case ORC -> new Enemy(id, kind, "orc", x, y, 85 * scaling, 43, 16, 20, 5, true, false);
                case FLAME_SPIRIT -> new Enemy(id, kind, "flame_spirit", x, y, 130 * scaling, 52, 20, 19, 8, true, false);
                case RED_REAPER -> new Enemy(id, kind, "red_reaper", x, y, 24_000, 75, 90, 34, 250, true, true);
                default -> throw new IllegalArgumentException("Unsupported enemy kind: " + kind);
            };
        }

        private WorldEntitySnapshot snapshot() {
            return new WorldEntitySnapshot(id, kind, x, y, 0,
                    Math.max(0, health / maxHealth), boss);
        }
    }

    private static final class Projectile {
        private final long id;
        private final WorldEntityKind kind;
        private final double vx;
        private final double vy;
        private final double damage;
        private final List<Long> hitIds = new ArrayList<>();
        private double x;
        private double y;
        private int pierce;
        private double life;

        private Projectile(long id, WorldEntityKind kind, double x, double y,
                double vx, double vy, double damage, int pierce, double life) {
            this.id = id;
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.damage = damage;
            this.pierce = pierce;
            this.life = life;
        }

        private WorldEntitySnapshot snapshot() {
            double rotation = Math.toDegrees(Math.atan2(vy, vx));
            return new WorldEntitySnapshot(id, kind, x, y, rotation, 1, false);
        }
    }

    private static final class Orb {
        private final long id;
        // Nao e final: orbes coalescem (um absorve o valor do outro) para o numero de
        // orbes na arena nao crescer sem limite numa run longa. Ver spawnOrb.
        private int value;
        private double x;
        private double y;

        private Orb(long id, double x, double y, int value) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.value = value;
        }

        private WorldEntitySnapshot snapshot() {
            return new WorldEntitySnapshot(id, WorldEntityKind.MAGICULE_ORB, x, y, 0, 1, false);
        }
    }
}
