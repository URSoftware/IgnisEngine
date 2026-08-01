package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class DelegatedExpeditionTest {

    @Test
    void expeditionResolvesOnlyAtItsNarrativeMilestone() {
        DelegatedExpedition planned = expedition();
        DelegatedExpedition active = planned.begin(false);

        assertSame(active, active.resolveAtMilestone("unrelated_milestone"));

        DelegatedExpedition completed =
                active.resolveAtMilestone("next_adventure_complete");

        assertEquals(DelegatedExpeditionStatus.COMPLETED, completed.status());
        assertTrue(completed.demonstrated());
    }

    @Test
    void automaticRepeatRequiresOneCompletedDemonstration() {
        DelegatedExpedition planned = expedition();
        assertThrows(IllegalStateException.class, () -> planned.begin(true));

        DelegatedExpedition repeated = planned
                .begin(false)
                .resolveAtMilestone("next_adventure_complete")
                .repeatAutomatically();

        assertEquals(DelegatedExpeditionStatus.IN_PROGRESS, repeated.status());
        assertTrue(repeated.demonstrated());
    }

    private static DelegatedExpedition expedition() {
        return DelegatedExpedition.planned(
                "goblin_timber_route",
                "ranga",
                "collect_known_timber",
                "next_adventure_complete",
                new TownResourceBundle(6, 0, 1, 0, 0, 0),
                Set.of("route_north_timber"));
    }
}
