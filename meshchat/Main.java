package meshchat;

import meshchat.network.MeshNode;
import meshchat.ui.ChatWindow;
import meshchat.util.DeviceIdentity;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        System.out.println("[MESHCHAT] Starting MeshChat...");
        DeviceIdentity identity = DeviceIdentity.load();
        System.out.println("[MESHCHAT] Device ID: " + identity.deviceId());

        MeshNode node = new MeshNode(identity);

        SwingUtilities.invokeLater(() -> {
            ChatWindow window = new ChatWindow(identity, node);
            window.setVisible(true);
            node.setMessageListener(window);
            node.start();
        });
    }
}
