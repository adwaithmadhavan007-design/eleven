package meshchat.routing;

import meshchat.model.Message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class MessageRouter {
    private static final int MAX_SEEN = 1000;

    // LRU set of seen message IDs to suppress duplicates
    private final Set<String> seenMessageIds = Collections.newSetFromMap(
        new LinkedHashMap<>(MAX_SEEN, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MAX_SEEN;
            }
        }
    );

    public enum Action { DELIVER, FORWARD, DROP }

    public synchronized Action route(Message msg, String myDeviceId) {
        if (seenMessageIds.contains(msg.id())) {
            System.out.println("[ROUTER] DROP (duplicate): " + msg.id());
            return Action.DROP;
        }
        seenMessageIds.add(msg.id());

        if (msg.to().equals(myDeviceId)) {
            System.out.println("[ROUTER] DELIVER to self: " + msg.id() + " from=" + msg.from().substring(0, 8));
            return Action.DELIVER;
        }

        if (msg.ttl() > 0) {
            System.out.println("[ROUTER] FORWARD (ttl=" + msg.ttl() + "): " + msg.id() + " → " + msg.to().substring(0, 8));
            return Action.FORWARD;
        }

        System.out.println("[ROUTER] DROP (ttl=0): " + msg.id());
        return Action.DROP;
    }

    public synchronized void markSeen(String messageId) {
        seenMessageIds.add(messageId);
    }
}
