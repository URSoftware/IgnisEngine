package com.rimurusurvivors.domain;

import java.util.Comparator;
import java.util.List;

/**
 * Porta de GreatSageAimAssist (mods/vampire-survivors-rimuru/src/RimuruRuntimeRules.cs).
 * O pipeline LINQ (Where/Select/OrderBy/ThenByDescending/ThenBy/FirstOrDefault) vira
 * filter/min com um Comparator composto — mesma prioridade de escolha: vida mais baixa
 * primeiro, depois ameaca (true primeiro), depois distancia mais perto.
 */
public final class GreatSageAimAssist {

    private GreatSageAimAssist() {
    }

    private record Candidate(TargetSnapshot target, double distance) {
    }

    public static TargetSnapshot chooseTarget(
            List<TargetSnapshot> targets, Vec2 origin, double maxDistance, boolean includeBosses) {
        Comparator<Candidate> priority = Comparator
                .comparingDouble((Candidate c) -> c.target().healthRatio())
                .thenComparing((Candidate c) -> c.target().isThreat(), Comparator.reverseOrder())
                .thenComparingDouble(Candidate::distance);

        return targets.stream()
                .filter(target -> (includeBosses || !target.isBoss()) && target.healthRatio() > 0)
                .map(target -> new Candidate(target, Vec2.distance(origin, target.position())))
                .filter(candidate -> candidate.distance() <= maxDistance)
                .min(priority)
                .map(Candidate::target)
                .orElse(null);
    }

    public static Vec2 predictIntercept(TargetSnapshot target, Vec2 origin, double projectileSpeed) {
        if (projectileSpeed <= 0) {
            return target.position();
        }

        double distance = Vec2.distance(origin, target.position());
        double lead = Math.min(Math.max(distance / projectileSpeed, 0.0), 0.45);
        return target.position().plus(target.velocity().scale(lead));
    }
}