package com.rimurusurvivors.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Simulacao deterministica de batalha por turnos entre Rimuru e o Lider dos Lobos.
 */
public final class BattleSimulation {

    public static final int RIMURU_MAX_HP = 100;
    public static final int RIMURU_MAX_MAGICULES = 50;
    public static final int LEADER_MAX_HP = 200;
    public static final int LEADER_MAX_MORALE = 100;
    public static final int WATER_BLADE_COST = 10;

    private final Set<VillagePreparation> preparations;
    private final RandomSource randomSource;

    private int rimuruHp;
    private int rimuruMagicules;
    private int leaderHp;
    private int leaderMaxHp;
    private int leaderMorale;

    private int turnCount;
    private LeaderPhase leaderPhase;
    private BattleIntention currentIntention;
    private BattleOutcome outcome;

    private boolean analyzed;
    private final Set<String> revealedInformation = new HashSet<>();

    public BattleSimulation(Set<VillagePreparation> preparations, RandomSource randomSource) {
        if (preparations == null || preparations.size() != 2) {
            throw new IllegalArgumentException("Batalha exige exatamente 2 preparacoes defensivas da aldeia.");
        }
        this.preparations = Collections.unmodifiableSet(new HashSet<>(preparations));
        this.randomSource = Objects.requireNonNull(randomSource, "RandomSource e obrigatorio.");

        this.rimuruHp = RIMURU_MAX_HP;
        this.rimuruMagicules = RIMURU_MAX_MAGICULES;
        this.leaderMaxHp = LEADER_MAX_HP;
        this.leaderMorale = LEADER_MAX_MORALE;
        this.turnCount = 1;
        this.leaderPhase = LeaderPhase.PROBING;
        this.outcome = BattleOutcome.IN_PROGRESS;

        // Efeito da preparacao CONTROLLED_BAIT: reduz HP inicial do lider em 20
        int initialHp = LEADER_MAX_HP;
        if (this.preparations.contains(VillagePreparation.CONTROLLED_BAIT)) {
            initialHp -= 20;
        }
        this.leaderHp = initialHp;
        this.currentIntention = buildIntentionForPhase(LeaderPhase.PROBING);
    }

    public BattleActionResult executeCommand(BattleCommand command, ReactionTiming timing) {
        if (outcome != BattleOutcome.IN_PROGRESS) {
            return BattleActionResult.invalid("A batalha ja foi encerrada.");
        }
        if (command == null) {
            return BattleActionResult.invalid("Comando nao pode ser nulo.");
        }
        if (timing == null) {
            timing = ReactionTiming.NONE;
        }

        // 1. Validacao de requisitos de comandos sem consumir turno
        if (command == BattleCommand.WATER_BLADE && rimuruMagicules < WATER_BLADE_COST) {
            return BattleActionResult.invalid("Magicules insuficientes para Hidrolamina.");
        }
        if (command == BattleCommand.PREDATOR && !isPredatorAvailable()) {
            return BattleActionResult.invalid("Requisito nao atendido: Moral do lider precisa estar em ruptura (<=30) ou HP critico.");
        }
        if (command == BattleCommand.NEGOTIATE && !isNegotiationAvailable()) {
            return BattleActionResult.invalid("Requisito nao atendido: O lider ainda nao entrou em ruptura de moral.");
        }

        // 2. Comando ANALYZE: revela informacoes sem alterar RNG nem consumir turno
        if (command == BattleCommand.ANALYZE) {
            this.analyzed = true;
            revealedInformation.add("Intencao: " + currentIntention.actionName());
            revealedInformation.add("Fase: " + leaderPhase.title());
            revealedInformation.add("Fraqueza: Hidrolamina causa dano elevado de moral.");
            revealedInformation.add("Condicao de Rendicao: Romper moral para desbloquear Negociar.");
            return BattleActionResult.info(
                    "Grande Sabio: Analise concluida. Intencao do lider: " + currentIntention.actionName(),
                    BattleCommand.ANALYZE);
        }

        // 3. Execucao de comandos de turno
        switch (command) {
            case WATER_BLADE -> {
                rimuruMagicules -= WATER_BLADE_COST;
                int damage = 35 + (int) (randomSource.nextDouble() * 10);
                int moraleLoss = 20;
                leaderHp = Math.max(0, leaderHp - damage);
                leaderMorale = Math.max(0, leaderMorale - moraleLoss);
            }
            case DEFEND -> {
                int moraleImpact = (int) (25 * timing.moraleImpactMultiplier());
                if (preparations.contains(VillagePreparation.LIGHT_FLANK)) {
                    moraleImpact += 10;
                }
                leaderMorale = Math.max(0, leaderMorale - moraleImpact);
            }
            case GOBLIN_SUPPORT -> {
                int damage = 15;
                int moraleLoss = 10;
                if (preparations.contains(VillagePreparation.REINFORCE_ENTRANCE)) {
                    damage += 10;
                }
                leaderHp = Math.max(0, leaderHp - damage);
                leaderMorale = Math.max(0, leaderMorale - moraleLoss);
            }
            case PREDATOR -> {
                leaderHp = 0;
                leaderMorale = 0;
                outcome = BattleOutcome.VICTORY_SUBDUED;
                return BattleActionResult.success("Predador ativado: o lider dos lobos foi subjugado.", command);
            }
            case NEGOTIATE -> {
                outcome = BattleOutcome.VICTORY_SURRENDER;
                return BattleActionResult.success("Negociacao bem-sucedida: o herdeiro Ranga e a matilha se rendem e prestam lealdade.", command);
            }
            default -> { }
        }

        // Check de vitoria por HP zero
        if (leaderHp <= 0) {
            outcome = BattleOutcome.VICTORY_SUBDUED;
            return BattleActionResult.success("O lider dos lobos foi derrotado em combate.", command);
        }

        // 4. Contra-ataque do Lider
        if (currentIntention.requiresReaction() && currentIntention.baseDamage() > 0) {
            double timingMultiplier = (command == BattleCommand.DEFEND) ? timing.damageMultiplier() : 1.0;
            int incomingDamage = (int) (currentIntention.baseDamage() * timingMultiplier);

            if (currentIntention.phase() == LeaderPhase.PACK_PRESSURE && preparations.contains(VillagePreparation.REINFORCE_ENTRANCE)) {
                incomingDamage = (int) (incomingDamage * 0.7);
            }

            rimuruHp = Math.max(0, rimuruHp - incomingDamage);
            if (rimuruHp <= 0) {
                outcome = BattleOutcome.DEFEAT;
                return BattleActionResult.success("Rimuru foi derrotado.", command);
            }
        }

        // 5. Atualizacao de fase do Lider
        updateLeaderPhase();
        turnCount++;
        currentIntention = buildIntentionForPhase(leaderPhase);

        return BattleActionResult.success("Turno " + (turnCount - 1) + " concluido.", command);
    }

    private void updateLeaderPhase() {
        if (leaderMorale <= 30 || leaderHp <= 40) {
            leaderPhase = LeaderPhase.MORAL_BREAKDOWN;
        } else if (turnCount >= 4 || leaderHp <= 120) {
            leaderPhase = LeaderPhase.DECISIVE_STRIKE;
        } else if (turnCount >= 2) {
            leaderPhase = LeaderPhase.PACK_PRESSURE;
        }
    }

    private BattleIntention buildIntentionForPhase(LeaderPhase phase) {
        return switch (phase) {
            case PROBING -> new BattleIntention(
                    phase, "Sondagem da Matilha", 12, false, "O lider observa o alinhamento da defesa.");
            case PACK_PRESSURE -> new BattleIntention(
                    phase, "Chamado dos Lobos", 22, true, "Lobos de apoio flanqueiam a palicada.");
            case DECISIVE_STRIKE -> new BattleIntention(
                    phase, "Investida Selvagem", 35, true, "O lider salta diretamente contra Rimuru!");
            case MORAL_BREAKDOWN -> new BattleIntention(
                    phase, "Hesitacao e Recuo", 0, false, "O lider abaixa as orelhas e recua a matilha.");
        };
    }

    public boolean isPredatorAvailable() {
        return leaderPhase == LeaderPhase.MORAL_BREAKDOWN || leaderMorale <= 30 || leaderHp <= 40;
    }

    public boolean isNegotiationAvailable() {
        return leaderPhase == LeaderPhase.MORAL_BREAKDOWN || leaderMorale <= 30;
    }

    // Getters de estado imutavel

    public Set<VillagePreparation> preparations() {
        return preparations;
    }

    public int rimuruHp() {
        return rimuruHp;
    }

    public int rimuruMagicules() {
        return rimuruMagicules;
    }

    public int leaderHp() {
        return leaderHp;
    }

    public int leaderMaxHp() {
        return leaderMaxHp;
    }

    public int leaderMorale() {
        return leaderMorale;
    }

    public int turnCount() {
        return turnCount;
    }

    public LeaderPhase leaderPhase() {
        return leaderPhase;
    }

    public BattleIntention currentIntention() {
        return currentIntention;
    }

    public BattleOutcome outcome() {
        return outcome;
    }

    public boolean analyzed() {
        return analyzed;
    }

    public Set<String> revealedInformation() {
        return Collections.unmodifiableSet(revealedInformation);
    }
}
