package com.rimurusurvivors.persistence;

import com.rimurusurvivors.domain.SaveDocument;
import java.util.List;
import java.util.Optional;

/** Result of a non-destructive load attempt, including fallback source and warnings. */
public record SaveLoadResult(
        Optional<SaveDocument> document,
        Source source,
        List<String> warnings) {

    public enum Source {
        PRIMARY,
        BACKUP,
        NONE
    }

    public SaveLoadResult {
        document = document == null ? Optional.empty() : document;
        if (source == null) {
            throw new IllegalArgumentException("Save source is required.");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        if (source == Source.NONE && document.isPresent()) {
            throw new IllegalArgumentException("A missing save cannot contain a document.");
        }
        if (source != Source.NONE && document.isEmpty()) {
            throw new IllegalArgumentException("A loaded save must contain a document.");
        }
    }

    public boolean found() {
        return document.isPresent();
    }
}
