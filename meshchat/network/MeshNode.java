package meshchat.network;

import meshchat.model.Message;
import meshchat.model.Peer;
import meshchat.routing.MessageRouter;
import meshchat.ui.MessageListener;
import meshchat.util.DeviceIdentity;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

public class MeshNode {
    public static final int TCP_PORT = DiscoveryService.TCP_PORT;

    private final DeviceIdentity identity;
    private final MessageRouter router = new MessageRouter();
    private final Map<String, PeerConnection> connections = new ConcurrentHashMap<>();
    private final Set<String> connectingPeers = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private MessageListener messageListener;
    private DiscoveryService discovery;
    private ServerSocket serverSocket;

    public MeshNode(DeviceIdentity identity) {
        this.identity = identity;
    }

    public void setMessageListener(MessageListener listener) {
        this.messageListener = listener;
    }

    public void start() {
        startTcpServer();
        startDiscovery();
        startConnectionMaintenance();
        System.out.println("[NODE] MeshNode started. Device: " + identity.deviceId());
    }

    private void startTcpServer() {
        Thread.ofVirtual().start(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                System.out.println("[SERVER] TCP server listening on port " + TCP_PORT);
                if (messageListener != null) {
                    messageListener.onStatusUpdate("Listening on port " + TCP_PORT);
                }

                while (!serverSocket.isClosed()) {
                    try {
                        Socket client = serverSocket.accept();
                        System.out.println("[SERVER] Incoming connection from: " + client.getInetAddress().getHostAddress());
                        handleNewConnection(client);
                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            System.err.println("[SERVER] Accept error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[SERVER] Failed to start server: " + e.getMessage());
                if (messageListener != null) {
                    messageListener.onStatusUpdate("ERROR: Port " + TCP_PORT + " in use!");
                }
            }
        });
    }

    private void startDiscovery() {
        discovery = new DiscoveryService(identity.deviceId(), this::onPeerDiscovered);
        discovery.start();
    }

    private void startConnectionMaintenance() {
        scheduler.scheduleAtFixedRate(() -> {
            connections.entrySet().removeIf(entry -> {
                boolean dead = !entry.getValue().isConnected();
                if (dead) {
                    System.out.println("[NODE] Removing dead connection: " + entry.getKey().substring(0, 8));
                    if (messageListener != null) {
                        messageListener.onPeerDisconnected(entry.getKey());
                    }
                }
                return dead;
            });
            connectingPeers.removeIf(id -> connections.containsKey(id));
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void onPeerDiscovered(String peerId, String host) {
        if (connections.containsKey(peerId)) return;
        if (connectingPeers.contains(peerId)) return;

        connectingPeers.add(peerId);
        Thread.ofVirtual().start(() -> connectToPeer(peerId, host, TCP_PORT));
    }

    private void connectToPeer(String peerId, String host, int port) {
        try {
            System.out.println("[CLIENT] Connecting to " + peerId.substring(0, 8) + "... @ " + host + ":" + port);
            Socket socket = new Socket(host, port);
            handleNewConnection(socket);
        } catch (IOException e) {
            System.err.println("[CLIENT] Failed to connect to " + host + ": " + e.getMessage());
            connectingPeers.remove(peerId);
        }
    }

    private void handleNewConnection(Socket socket) {
        try {
            PeerConnection conn = new PeerConnection(socket);
            String remoteHost = conn.getRemoteHost();

            // Send handshake: our identity
            Message handshake = new Message(
                UUID.randomUUID().toString(),
                identity.deviceId(),
                "HANDSHAKE",
                0,
                identity.deviceId(),
                System.currentTimeMillis()
            );
            conn.send(handshake);

            conn.startReading(
                json -> handleIncomingData(json, conn),
                () -> {
                    if (conn.getPeer() != null) {
                        String pid = conn.getPeer().deviceId();
                        connections.remove(pid);
                        connectingPeers.remove(pid);
                        System.out.println("[NODE] Peer disconnected: " + pid.substring(0, 8));
                        if (messageListener != null) {
                            messageListener.onPeerDisconnected(pid);
                        }
                    }
                }
            );

        } catch (IOException e) {
            System.err.println("[NODE] Error handling connection: " + e.getMessage());
        }
    }

    private void handleIncomingData(String json, PeerConnection conn) {
        try {
            Message msg = Message.fromJson(json);

            // Handshake message
            if ("HANDSHAKE".equals(msg.to())) {
                String peerId = msg.from();
                Peer peer = new Peer(peerId, conn.getRemoteHost(), TCP_PORT);
                conn.setPeer(peer);

                // Avoid duplicate connections
                if (connections.containsKey(peerId)) {
                    System.out.println("[NODE] Duplicate connection for " + peerId.substring(0, 8) + ", closing old");
                    PeerConnection old = connections.get(peerId);
                    if (old != conn) {
                        old.close();
                    }
                }

                connections.put(peerId, conn);
                connectingPeers.remove(peerId);
                System.out.println("[NODE] Peer registered: " + peerId.substring(0, 8) + " @ " + peer.host());
                if (messageListener != null) {
                    messageListener.onPeerConnected(peer);
                }
                return;
            }

            // Regular message
            MessageRouter.Action action = router.route(msg, identity.deviceId());
            switch (action) {
                case DELIVER -> {
                    System.out.println("[NODE] *** MESSAGE FOR ME from " + msg.from().substring(0, 8) + ": " + msg.text());
                    if (messageListener != null) {
                        messageListener.onMessageReceived(msg);
                    }
                }
                case FORWARD -> {
                    Message forwarded = msg.withDecrementedTtl();
                    System.out.println("[NODE] Forwarding message " + msg.id().substring(0, 8) + " (ttl " + msg.ttl() + " → " + forwarded.ttl() + ")");
                    broadcast(forwarded, conn); // Forward to all except sender
                    if (messageListener != null) {
                        messageListener.onMessageRelayed(msg);
                    }
                }
                case DROP -> {} // Already logged in router
            }
        } catch (Exception e) {
            System.err.println("[NODE] Error processing message: " + e.getMessage());
        }
    }

    public void sendMessage(String toDeviceId, String text) {
        Message msg = new Message(
            UUID.randomUUID().toString(),
            identity.deviceId(),
            toDeviceId,
            Message.DEFAULT_TTL,
            text,
            System.currentTimeMillis()
        );

        // Mark as seen so we don't process our own message if it loops back
        router.markSeen(msg.id());

        System.out.println("[NODE] Sending message to " + toDeviceId.substring(0, 8) + ": " + text);
        broadcast(msg, null);

        if (messageListener != null) {
            messageListener.onMessageSent(msg);
        }
    }

    private void broadcast(Message msg, PeerConnection exclude) {
        for (PeerConnection conn : connections.values()) {
            if (conn != exclude && conn.isConnected()) {
                conn.send(msg);
            }
        }
    }

    /**
     * Manually connect to a peer by IP address (fallback when UDP discovery is blocked).
     */
    public void connectManually(String host) {
        System.out.println("[NODE] Manual connect to: " + host);
        Thread.ofVirtual().start(() -> {
            try {
                Socket socket = new Socket(host, TCP_PORT);
                System.out.println("[NODE] Manual connection established to: " + host);
                handleNewConnection(socket);
            } catch (IOException e) {
                System.err.println("[NODE] Manual connect failed to " + host + ": " + e.getMessage());
                if (messageListener != null) {
                    messageListener.onStatusUpdate("Connect failed: " + host + " - " + e.getMessage());
                }
            }
        });
    }

    public Map<String, PeerConnection> getConnections() {
        return Collections.unmodifiableMap(connections);
    }

    public void stop() {
        discovery.stop();
        scheduler.shutdown();
        connections.values().forEach(PeerConnection::close);
        connections.clear();
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }
}
