package meshchat.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal JSON builder/parser - no external dependencies.
 * Only handles flat key-value objects (strings, numbers, longs).
 */
public class SimpleJson {

    public static class JsonObject {
        private final Map<String, Object> map = new LinkedHashMap<>();

        public JsonObject put(String key, String value) {
            map.put(key, value);
            return this;
        }

        public JsonObject put(String key, long value) {
            map.put(key, value);
            return this;
        }

        public JsonObject put(String key, int value) {
            map.put(key, value);
            return this;
        }

        public String getString(String key) {
            Object v = map.get(key);
            return v == null ? null : String.valueOf(v);
        }

        public int getInt(String key) {
            Object v = map.get(key);
            if (v instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(v));
        }

        public long getLong(String key) {
            Object v = map.get(key);
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(v));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escape(e.getKey())).append("\":");
                Object v = e.getValue();
                if (v instanceof String s) {
                    sb.append("\"").append(escape(s)).append("\"");
                } else {
                    sb.append(v);
                }
            }
            sb.append("}");
            return sb.toString();
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }

    public static JsonObject parse(String json) {
        JsonObject obj = new JsonObject();
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            throw new IllegalArgumentException("Not a JSON object: " + json);
        }
        json = json.substring(1, json.length() - 1).trim();

        // Simple tokenizer for flat JSON objects
        int i = 0;
        while (i < json.length()) {
            // skip whitespace
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (i >= json.length()) break;
            if (json.charAt(i) == ',') { i++; continue; }

            // read key
            if (json.charAt(i) != '"') break;
            int keyStart = i + 1;
            i = findEndQuote(json, keyStart);
            String key = unescape(json.substring(keyStart, i));
            i++; // skip closing quote

            // skip whitespace and colon
            while (i < json.length() && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ':')) i++;

            // read value
            if (i >= json.length()) break;
            if (json.charAt(i) == '"') {
                int valStart = i + 1;
                i = findEndQuote(json, valStart);
                String val = unescape(json.substring(valStart, i));
                i++; // skip closing quote
                obj.put(key, val);
            } else {
                // number
                int valStart = i;
                while (i < json.length() && json.charAt(i) != ',' && json.charAt(i) != '}') i++;
                String numStr = json.substring(valStart, i).trim();
                if (numStr.contains(".")) {
                    obj.put(key, (long) Double.parseDouble(numStr));
                } else {
                    obj.put(key, Long.parseLong(numStr));
                }
            }
        }
        return obj;
    }

    private static int findEndQuote(String s, int start) {
        int i = start;
        while (i < s.length()) {
            if (s.charAt(i) == '\\') {
                i += 2;
            } else if (s.charAt(i) == '"') {
                return i;
            } else {
                i++;
            }
        }
        throw new IllegalArgumentException("Unterminated string at " + start);
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}
