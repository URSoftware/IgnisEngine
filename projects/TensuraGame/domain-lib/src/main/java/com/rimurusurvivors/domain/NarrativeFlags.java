package com.rimurusurvivors.domain;

import java.util.LinkedHashSet;
import java.util.Set;

/** Capitulo atual e fatos narrativos semanticamente persistidos. */
public record NarrativeFlags(String chapterId, Set<String> values) {

    public NarrativeFlags {
        if (chapterId == null || chapterId.isBlank()) {
            throw new IllegalArgumentException("Narrative chapter id is required.");
        }
        values = immutableIds(values, "Narrative flag");
    }

    public boolean contains(String flagId) {
        return values.contains(flagId);
    }

    public NarrativeFlags withFlag(String flagId) {
        if (flagId == null || flagId.isBlank()) {
            throw new IllegalArgumentException("Narrative flag id is required.");
        }
        if (values.contains(flagId)) {
            return this;
        }
        LinkedHashSet<String> updated = new LinkedHashSet<>(values);
        updated.add(flagId);
        return new NarrativeFlags(chapterId, updated);
    }

    public NarrativeFlags withFlags(Set<String> flagIds) {
        NarrativeFlags updated = this;
        if (flagIds != null) {
            for (String flagId : flagIds) {
                updated = updated.withFlag(flagId);
            }
        }
        return updated;
    }

    public NarrativeFlags without(String flagId) {
        if (!values.contains(flagId)) {
            return this;
        }
        LinkedHashSet<String> updated = new LinkedHashSet<>(values);
        updated.remove(flagId);
        return new NarrativeFlags(chapterId, updated);
    }

    public NarrativeFlags withChapter(String newChapterId) {
        return chapterId.equals(newChapterId) ? this : new NarrativeFlags(newChapterId, values);
    }

    static Set<String> immutableIds(Set<String> ids, String label) {
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        if (ids != null) {
            for (String id : ids) {
                if (id == null || id.isBlank()) {
                    throw new IllegalArgumentException(label + " ids must not be blank.");
                }
                copy.add(id);
            }
        }
        return Set.copyOf(copy);
    }
}
