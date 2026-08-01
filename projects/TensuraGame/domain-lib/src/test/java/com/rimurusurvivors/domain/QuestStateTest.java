package com.rimurusurvivors.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestStateTest {

    private final QuestDefinition definition = new QuestDefinition(
            "quest.test",
            Set.of(),
            Set.of("story.ready"),
            List.of(new QuestObjective("objective.collect", 2)),
            Set.of("story.rewarded"));

    @Test
    void stateMachineRequiresPreconditionsAndActivation() {
        QuestState locked = QuestState.locked(definition.id());

        assertSame(
                locked,
                locked.refreshAvailability(
                        definition,
                        Map.of(definition.id(), locked),
                        new NarrativeFlags("test", Set.of())));
        assertThrows(IllegalStateException.class, locked::accept);

        QuestState available = locked.refreshAvailability(
                definition,
                Map.of(definition.id(), locked),
                new NarrativeFlags("test", Set.of("story.ready")));
        QuestState active = available.accept();
        QuestState partial = active.advance(definition, "objective.collect", 1);
        QuestState completed = partial.advance(definition, "objective.collect", 1);

        assertEquals(QuestStatus.AVAILABLE, available.status());
        assertEquals(QuestStatus.ACTIVE, partial.status());
        assertEquals(QuestStatus.COMPLETED, completed.status());
    }

    @Test
    void completedProgressIsCappedAndIdempotent() {
        QuestState completed = new QuestState(
                definition.id(),
                QuestStatus.ACTIVE,
                QuestProgress.empty())
                .advance(definition, "objective.collect", 10);

        QuestState repeated = completed.advance(definition, "objective.collect", 1);

        assertEquals(2, completed.progress().count("objective.collect"));
        assertSame(completed, repeated);
    }

    @Test
    void unknownObjectivesAndInvalidProgressFailFast() {
        QuestState active = new QuestState(
                definition.id(), QuestStatus.ACTIVE, QuestProgress.empty());

        assertThrows(
                IllegalArgumentException.class,
                () -> active.advance(definition, "objective.unknown", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QuestProgress(Map.of("objective.collect", -1)));
    }
}
