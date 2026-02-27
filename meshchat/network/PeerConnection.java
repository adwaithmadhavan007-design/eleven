package meshchat.network;

import meshchat.model.Message;
import meshchat.model.Peer;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class PeerConnection implements Closeable {
    private final Socket socket;
    private final PrintWriter writer;
    private final BufferedReader reader;
    private Peer peer;
    private volatile boolean running = true;

    public PeerConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void setPeer(Peer peer) {
        this.peer = peer;
    }

    public Peer getPeer() {
        return peer;
    }

    public String getRemoteHost() {
        return socket.getInetAddress().getHostAddress();
    }

    public void send(Message msg) {
        if (!socket.isClosed() && socket.isConnected()) {
            writer.println(msg.toJson());
        }
    }

    public void startReading(Consumer<String> onMessage, Runnable onClose) {
        Thread.ofVirtual().start(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    onMessage.accept(line);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("[CONNECTION] Read error from " + getRemoteHost() + ": " + e.getMessage());
                }
            } finally {
                onClose.run();
            }
        });
    }

    public boolean isConnected() {
        return !socket.isClosed() && socket.isConnected();
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (IOException ignored) {}
    }
}
