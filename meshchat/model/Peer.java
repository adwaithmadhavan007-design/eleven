package meshchat.model;

public record Peer(String deviceId, String host, int port) {
    @Override
    public String toString() {
        return deviceId.substring(0, 8) + "... @ " + host + ":" + port;
    }

    public String shortId() {
        return deviceId.length() > 8 ? deviceId.substring(0, 8) + "..." : deviceId;
    }
}
