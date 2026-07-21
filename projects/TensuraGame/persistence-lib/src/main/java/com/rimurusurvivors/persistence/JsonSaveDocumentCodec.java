package com.rimurusurvivors.persistence;

import com.rimurusurvivors.domain.SaveDocument;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONException;
import org.json.JSONObject;

/** Converts the neutral domain document to and from the on-disk JSON envelope. */
public final class JsonSaveDocumentCodec {

    private static final String SCHEMA_VERSION = "schemaVersion";
    private static final String FIELDS = "fields";

    public String encode(SaveDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Save document is required.");
        }
        JSONObject fields = new JSONObject();
        new TreeMap<>(document.fields()).forEach(fields::put);
        return new JSONObject()
                .put(SCHEMA_VERSION, document.schemaVersion())
                .put(FIELDS, fields)
                .toString(2);
    }

    public SaveDocument decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Save JSON must not be blank.");
        }
        try {
            JSONObject root = new JSONObject(json);
            Object rawSchemaVersion = root.get(SCHEMA_VERSION);
            if (!(rawSchemaVersion instanceof Number number)
                    || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() != number.intValue()) {
                throw new IllegalArgumentException("Save schema version must be an integer.");
            }
            int schemaVersion = number.intValue();
            JSONObject jsonFields = root.getJSONObject(FIELDS);
            Map<String, String> fields = new LinkedHashMap<>();
            jsonFields.keySet().stream().sorted().forEach(key -> {
                Object value = jsonFields.get(key);
                if (!(value instanceof String text)) {
                    throw new IllegalArgumentException("Save field must be a string: " + key);
                }
                fields.put(key, text);
            });
            return new SaveDocument(schemaVersion, fields);
        } catch (JSONException exception) {
            throw new IllegalArgumentException("Invalid save JSON.", exception);
        }
    }
}
