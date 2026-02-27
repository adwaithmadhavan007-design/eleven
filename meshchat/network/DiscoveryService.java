package meshchat.network;

import java.net.*;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Peer discovery using UDP broadcast.
 *
 * Key fixes for Windows LAN:
 *  - Enumerates ALL network interfaces and broadcasts on each one individually
 *  - Uses SO_REUSEADDR on listener socket (required on Windows)
 *  - Sends subnet-directed broadcasts derived from each interface's IP
 *  - Supports manual peer connection as fallback
 *  - Logs all IPs so users can manually connect if UDP is blocked
 */
public class DiscoveryService {
    public static final int DISCOVERY_PORT = 45679;
    public static final int TCP_PORT = 45678;
    private static final int BROADCAST_INTERVAL_MS = 2000;

    private final String deviceId;
    private final BiConsumer<String, String> onPeerDiscovered; // (deviceId, host)
    private volatile boolean running = false;

    public DiscoveryService(String deviceId, BiConsumer<String, String> onPeerDiscovered) {
        this.deviceId = deviceId;
        this.onPeerDiscovered = onPeerDiscovered;
    }

    public void start() {
        running = true;
        printLocalAddresses(); // Always print IPs so user can manually connect
        startBroadcasting();
        startListening();
    }

    public void stop() {
        running = false;
    }

    /**
     * Print all local IPs to console — critical for manual connection fallback.
     */
    public static void printLocalAddresses() {
        System.out.println("[NETWORK] Local IP addresses on this machine:");
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        System.out.println("[NETWORK]   " + iface.getDisplayName() + " -> " + addr.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[NETWORK] Could not enumerate interfaces: " + e.getMessage());
        }
    }

    /**
     * Get all LAN IPv4 addresses (excluding loopback/link-local).
     */
    public static List<Inet4Address> getLanAddresses() {
        List<Inet4Address> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address ip4) {
                        byte[] b = ip4.getAddress();
                        // Skip link-local (169.254.x.x)
                        if (b[0] == (byte) 169 && b[1] == (byte) 254) continue;
                        result.add(ip4);
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[DISCOVERY] Interface enum error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Derive subnet broadcast address from an IP and prefix length.
     * e.g. 192.168.1.100/24 -> 192.168.1.255
     */
    private static InetAddress subnetBroadcast(Inet4Address addr, int prefixLen) throws UnknownHostException {
        byte[] ip = addr.getAddress();
        int mask = prefixLen == 0 ? 0 : (0xFFFFFFFF << (32 - prefixLen));
        int ipInt = ((ip[0] & 0xFF) << 24) | ((ip[1] & 0xFF) << 16) | ((ip[2] & 0xFF) << 8) | (ip[3] & 0xFF);
        int broadcast = (ipInt & mask) | (~mask);
        byte[] b = new byte[]{
            (byte) (broadcast >> 24),
            (byte) (broadcast >> 16),
            (byte) (broadcast >> 8),
            (byte) broadcast
        };
        return InetAddress.getByAddress(b);
    }

    private void startBroadcasting() {
        Thread.ofVirtual().start(() -> {
            String payload = "MESHCHAT:" + deviceId + ":" + TCP_PORT;
            byte[] buf = payload.getBytes();

            while (running) {
                try {
                    List<Inet4Address> lanAddresses = getLanAddresses();

                    for (Inet4Address localAddr : lanAddresses) {
                        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(localAddr, 0))) {
                            socket.setBroadcast(true);
                            socket.setSoTimeout(500);

                            // Find the prefix length for this interface
                            int prefixLen = 24; // default /24
                            NetworkInterface iface = NetworkInterface.getByInetAddress(localAddr);
                            if (iface != null) {
                                for (InterfaceAddress ia : iface.getInterfaceAddresses()) {
                                    if (ia.getAddress().equals(localAddr)) {
                                        prefixLen = ia.getNetworkPrefixLength();
                                        break;
                                    }
                                }
                            }

                            // Send subnet-directed broadcast
                            InetAddress broadcastAddr = subnetBroadcast(localAddr, prefixLen);
                            sendBroadcast(socket, buf, broadcastAddr);
                            System.out.println("[DISCOVERY] Broadcast from " + localAddr.getHostAddress()
                                + " -> " + broadcastAddr.getHostAddress() + ":" + DISCOVERY_PORT);

                            // Also send to 255.255.255.255 as fallback
                            sendBroadcast(socket, buf, InetAddress.getByName("255.255.255.255"));

                        } catch (Exception e) {
                            System.err.println("[DISCOVERY] Broadcast error on " + localAddr.getHostAddress() + ": " + e.getMessage());
                        }
                    }

                    if (lanAddresses.isEmpty()) {
                        System.err.println("[DISCOVERY] WARNING: No LAN interfaces found! Check network connection.");
                    }

                    Thread.sleep(BROADCAST_INTERVAL_MS);

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("[DISCOVERY] Broadcast loop error: " + e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
                }
            }
        });
    }

    private void sendBroadcast(DatagramSocket socket, byte[] buf, InetAddress target) {
        try {
            DatagramPacket packet = new DatagramPacket(buf, buf.length, target, DISCOVERY_PORT);
            socket.send(packet);
        } catch (Exception e) {
            // Suppress - some targets may be unreachable
        }
    }

    private void startListening() {
        Thread.ofVirtual().start(() -> {
            DatagramSocket socket = null;
            try {
                // SO_REUSEADDR is critical on Windows to allow multiple processes
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.setBroadcast(true);
                socket.setSoTimeout(1000);
                socket.bind(new InetSocketAddress(DISCOVERY_PORT));

                System.out.println("[DISCOVERY] Listening for UDP broadcasts on port " + DISCOVERY_PORT);

                byte[] buf = new byte[512];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        socket.receive(packet);

                        String msg = new String(packet.getData(), 0, packet.getLength()).trim();
                        String senderHost = packet.getAddress().getHostAddress();

                        if (msg.startsWith("MESHCHAT:")) {
                            String[] parts = msg.split(":");
                            if (parts.length >= 3) {
                                String peerId = parts[1];
                                if (!peerId.equals(deviceId)) {
                                    System.out.println("[DISCOVERY] Found peer: "
                                        + peerId.substring(0, 8) + "... @ " + senderHost);
                                    onPeerDiscovered.accept(peerId, senderHost);
                                }
                            }
                        }
                    } catch (SocketTimeoutException ignored) {
                        // Normal timeout, loop again
                    } catch (Exception e) {
                        if (running) {
                            System.err.println("[DISCOVERY] Receive error: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[DISCOVERY] FATAL: Cannot bind to UDP port " + DISCOVERY_PORT + ": " + e.getMessage());
                System.err.println("[DISCOVERY] Another app may be using port " + DISCOVERY_PORT + ". Try restarting.");
            } finally {
                if (socket != null && !socket.isClosed()) socket.close();
            }
        });
    }
}
