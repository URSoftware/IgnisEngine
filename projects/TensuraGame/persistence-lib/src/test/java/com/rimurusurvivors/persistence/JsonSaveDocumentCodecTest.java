package com.rimurusurvivors.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rimurusurvivors.domain.SaveDocument;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonSaveDocumentCodecTest {

    private final JsonSaveDocumentCodec codec = new JsonSaveDocumentCodec();

    @Test
    void roundTripsNeutralDocument() {
        SaveDocument source = new SaveDocument(1, Map.of("campaign.areaId", "cave_gallery"));

        assertEquals(source, codec.decode(codec.encode(source)));
    }

    @Test
    void rejectsBlankMalformedAndNonStringFields() {
        assertThrows(IllegalArgumentException.class, () -> codec.decode(" "));
        assertThrows(IllegalArgumentException.class, () -> codec.decode("not-json"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("{\"schemaVersion\":\"1\",\"fields\":{}}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("{\"schemaVersion\":1.5,\"fields\":{}}"));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("{\"schemaVersion\":1,\"fields\":{\"bad\":2}}"));
    }
}
