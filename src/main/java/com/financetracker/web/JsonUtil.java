package com.financetracker.web;

import com.financetracker.model.Transaction;
import java.time.LocalDate;
import java.util.*;
import java.io.IOException;
import java.io.Reader;

public final class JsonUtil {
    private JsonUtil() { }

    public static Map<String, String> readObject(Reader reader) throws IOException {
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[1024];
        int count;
        while ((count = reader.read(buffer)) != -1) {
            if (body.length() + count > 16_384) throw new IllegalArgumentException("Request body is too large.");
            body.append(buffer, 0, count);
        }
        return parseSimpleObject(body.toString());
    }

    // All request bodies in this application are flat JSON objects.
    public static Map<String, String> parseSimpleObject(String json) {
        if (json == null || json.length() > 16_384) throw new IllegalArgumentException("Invalid JSON request.");
        Cursor cursor = new Cursor(json);
        Map<String, String> result = new HashMap<>();
        cursor.expect('{');
        if (!cursor.take('}')) {
            do {
                String key = cursor.string();
                cursor.expect(':');
                String value = cursor.value();
                if (result.containsKey(key)) throw new IllegalArgumentException("Duplicate JSON field.");
                result.put(key, value);
            } while (cursor.take(','));
            cursor.expect('}');
        }
        cursor.whitespace();
        if (cursor.position != json.length()) throw new IllegalArgumentException("Invalid JSON request.");
        return result;
    }

    private static final class Cursor {
        private final String input;
        private int position;

        private Cursor(String input) { this.input = input; }
        private void whitespace() { while (position < input.length() && Character.isWhitespace(input.charAt(position))) position++; }
        private boolean take(char expected) {
            whitespace();
            if (position < input.length() && input.charAt(position) == expected) { position++; return true; }
            return false;
        }
        private void expect(char expected) { if (!take(expected)) throw new IllegalArgumentException("Invalid JSON request."); }
        private String value() {
            whitespace();
            if (position < input.length() && input.charAt(position) == '"') return string();
            int start = position;
            while (position < input.length() && ",} \t\r\n".indexOf(input.charAt(position)) < 0) position++;
            String literal = input.substring(start, position);
            if (literal.equals("null")) return null;
            if (literal.equals("true") || literal.equals("false")) throw new IllegalArgumentException("Fields must contain text or numbers.");
            if (literal.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) return literal;
            throw new IllegalArgumentException("Invalid JSON request.");
        }
        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char character = input.charAt(position++);
                if (character == '"') return result.toString();
                if (character < 0x20) throw new IllegalArgumentException("Invalid JSON request.");
                if (character != '\\') { result.append(character); continue; }
                if (position == input.length()) break;
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (position + 4 > input.length()) throw new IllegalArgumentException("Invalid JSON request.");
                        try { result.append((char) Integer.parseInt(input.substring(position, position + 4), 16)); }
                        catch (NumberFormatException exception) { throw new IllegalArgumentException("Invalid JSON request."); }
                        position += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid JSON request.");
                }
            }
            throw new IllegalArgumentException("Invalid JSON request.");
        }
    }

    public static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String || value instanceof LocalDate) return "\"" + escape(value.toString()) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Transaction t) return "{\"id\":\"" + t.getId() + "\",\"type\":\"" + t.getType() + "\",\"category\":\"" + escape(t.getCategory()) + "\",\"amount\":" + t.getAmount() + ",\"description\":\"" + escape(t.getDescription()) + "\",\"date\":\"" + t.getDate() + "\"}";
        if (value instanceof Map<?, ?> map) { StringJoiner joiner = new StringJoiner(",", "{", "}"); map.forEach((key, item) -> joiner.add(toJson(key.toString()) + ":" + toJson(item))); return joiner.toString(); }
        if (value instanceof Iterable<?> items) { StringJoiner joiner = new StringJoiner(",", "[", "]"); items.forEach(item -> joiner.add(toJson(item))); return joiner.toString(); }
        return toJson(value.toString());
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '"' || character == '\\') result.append('\\').append(character);
            else if (character < 0x20) result.append(String.format("\\u%04x", (int) character));
            else result.append(character);
        }
        return result.toString();
    }
}
