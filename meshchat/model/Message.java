package meshchat.model;

import meshchat.util.SimpleJson;


public record Message(
    String id,
    String from,
    String to,
    int ttl,
    String text,
    long timestamp
) {
    public static final int DEFAULT_TTL = 10;

    public static Message fromJson(String json) {
        SimpleJson.JsonObject obj = SimpleJson.parse(json);
        return new Message(
            obj.getString("id"),
            obj.getString("from"),
            obj.getString("to"),
            obj.getInt("ttl"),
            obj.getString("text"),
            obj.getLong("timestamp")
        );
    }

    public String toJson() {
        return new SimpleJson.JsonObject()
            .put("id", id)
            .put("from", from)
            .put("to", to)
            .put("ttl", ttl)
            .put("text", text)
            .put("timestamp", timestamp)
            .toString();
    }

    public Message withDecrementedTtl() {
        return new Message(id, from, to, ttl - 1, text, timestamp);
    }
}
