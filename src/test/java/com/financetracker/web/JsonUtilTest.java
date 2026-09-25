package com.financetracker.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class JsonUtilTest {
    @Test void preservesNullAndRejectsDuplicateNullFields() {
        assertTrue(JsonUtil.parseSimpleObject("{\"description\":null}").containsKey("description"));
        assertEquals(null, JsonUtil.parseSimpleObject("{\"description\":null}").get("description"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseSimpleObject("{\"description\":null,\"description\":\"x\"}"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseSimpleObject("{\"description\":true}"));
    }

    @Test void boundsRequestReading() throws Exception {
        assertEquals("12", JsonUtil.readObject(new StringReader("{\"amount\":12}")).get("amount"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.readObject(new StringReader(" ".repeat(16_385))));
    }
    @Test void parsesEscapedTextAndNumbers() {
        Map<String, String> values = JsonUtil.parseSimpleObject("{\"description\":\"Lunch, coffee: \\\"today\\\"\\nnext\",\"amount\":12.50}");
        assertEquals("Lunch, coffee: \"today\"\nnext", values.get("description"));
        assertEquals("12.50", values.get("amount"));
    }

    @Test void rejectsMalformedAndDuplicateFields() {
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseSimpleObject("not JSON"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseSimpleObject("{\"amount\":1,\"amount\":2}"));
        assertThrows(IllegalArgumentException.class, () -> JsonUtil.parseSimpleObject("{\"amount\":1,}"));
    }

    @Test void escapesControlCharactersInResponses() {
        String json = JsonUtil.toJson(Map.of("description", "One\nTwo\tThree"));
        assertTrue(json.contains("\\u000a"));
        assertTrue(json.contains("\\u0009"));
        assertEquals("One\nTwo\tThree", JsonUtil.parseSimpleObject(json).get("description"));
    }
}
