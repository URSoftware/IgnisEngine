package com.rimurusurvivors.persistence;

import com.rimurusurvivors.domain.SaveDocument;

/** Validates a document and migrates it to the version accepted by the current game. */
@FunctionalInterface
public interface SaveDocumentProcessor {

    SaveDocument process(SaveDocument document);
}
