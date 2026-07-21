package com.rimurusurvivors.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/** Documento neutro entre o dominio e qualquer formato externo de persistencia. */
public record SaveDocument(int schemaVersion, Map<String, String> fields) {

    public SaveDocument {
        if (schemaVersion < 0) {
            throw new IllegalArgumentException("Save schema version must be non-negative.");
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        if (fields != null) {
            fields.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("Save field names must not be blank.");
                }
                if (value == null) {
                    throw new IllegalArgumentException("Save field values must not be null.");
                }
                copy.put(key, value);
            });
        }
        fields = Map.copyOf(copy);
    }

    public String requireField(String name) {
        String value = fields.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Required save field is missing: " + name);
        }
        return value;
    }
}
