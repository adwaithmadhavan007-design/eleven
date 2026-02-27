package meshchat.ui;

import meshchat.model.Message;
import meshchat.model.Peer;
import meshchat.network.DiscoveryService;
import meshchat.network.MeshNode;
import meshchat.util.DeviceIdentity;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.net.Inet4Address;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatWindow extends JFrame implements MessageListener {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final DeviceIdentity identity;
    private final MeshNode node;
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();

    // UI Components
    private JTextPane chatArea;
    private StyledDocument chatDoc;
    private JTextField targetInput;
    private JTextField messageInput;
    private JButton sendButton;
    private JLabel statusLabel;
    private DefaultListModel<String> peerListModel;

    // Text styles
    private Style styleReceived, styleSent, styleRelayed, styleSystem;

    public ChatWindow(DeviceIdentity identity, MeshNode node) {
        this.identity = identity;
        this.node = node;

        setTitle("MeshChat - " + identity.deviceId().substring(0, 8) + "...");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 680);
        setMinimumSize(new Dimension(750, 520));
        setLocationRelativeTo(null);

        buildUI();
        setupStyles();
        setupListeners();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                node.stop();
            }
        });

        appendSystem("MeshChat started.");
        appendSystem("Your full Device ID: " + identity.deviceId());
        appendSystem("Tip: If auto-discovery fails, use '+ Manual Connect' with the other machine's IP.");
    }

    private String getLocalIPsText() {
        List<Inet4Address> addrs = DiscoveryService.getLanAddresses();
        if (addrs.isEmpty()) return "No LAN IP found";
        StringBuilder sb = new StringBuilder();
        for (Inet4Address a : addrs) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(a.getHostAddress());
        }
        return sb.toString();
    }

    private void buildUI() {
        setLayout(new BorderLayout(5, 5));
        getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));

        // ── LEFT PANEL ──────────────────────────────────────────────────────
        JPanel leftPanel = new JPanel(new BorderLayout(4, 4));
        leftPanel.setPreferredSize(new Dimension(230, 0));
        leftPanel.setBorder(BorderFactory.createTitledBorder("Network"));

        // Device ID section
        JPanel idPanel = new JPanel(new BorderLayout(2, 2));
        idPanel.setBorder(BorderFactory.createTitledBorder("My Device ID"));
        JTextArea idText = new JTextArea(identity.deviceId());
        idText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        idText.setForeground(new Color(0, 100, 180));
        idText.setEditable(false);
        idText.setLineWrap(true);
        idText.setBackground(idPanel.getBackground());
        idPanel.add(idText, BorderLayout.CENTER);
        JButton copyIdBtn = new JButton("Copy Full ID");
        copyIdBtn.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        copyIdBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(identity.deviceId()), null);
            JOptionPane.showMessageDialog(this,
                "Device ID copied!\n\nShare this with other users so they can message you.",
                "Copied", JOptionPane.INFORMATION_MESSAGE);
        });
        idPanel.add(copyIdBtn, BorderLayout.SOUTH);

        // My IP section (critical for manual connect fallback)
        JPanel myIpPanel = new JPanel(new BorderLayout(2, 2));
        myIpPanel.setBorder(BorderFactory.createTitledBorder("My IP Address"));
        JTextArea ipText = new JTextArea(getLocalIPsText());
        ipText.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        ipText.setForeground(new Color(160, 0, 0));
        ipText.setEditable(false);
        ipText.setBackground(new Color(255, 248, 240));
        myIpPanel.add(ipText, BorderLayout.CENTER);

        // Manual connect button — THE KEY FALLBACK when UDP is blocked
        JButton manualBtn = new JButton("+ Connect by IP");
        manualBtn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        manualBtn.setBackground(new Color(180, 70, 0));
        manualBtn.setForeground(Color.WHITE);
        manualBtn.setToolTipText("Use this if auto-discovery doesn't work");
        manualBtn.addActionListener(e -> showManualConnectDialog());
        myIpPanel.add(manualBtn, BorderLayout.SOUTH);

        // Status label
        statusLabel = new JLabel("Starting...", SwingConstants.CENTER);
        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        statusLabel.setForeground(Color.ORANGE);
        statusLabel.setBorder(new EmptyBorder(3, 3, 3, 3));

        // Peers list
        JPanel peerPanel = new JPanel(new BorderLayout());
        peerPanel.setBorder(BorderFactory.createTitledBorder("Connected Peers (click to select)"));
        peerListModel = new DefaultListModel<>();
        JList<String> peerList = new JList<>(peerListModel);
        peerList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        peerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = peerList.getSelectedValue();
                if (selected != null) {
                    for (Peer peer : peers.values()) {
                        if (selected.contains(peer.deviceId().substring(0, 8))) {
                            targetInput.setText(peer.deviceId());
                            messageInput.requestFocus();
                            break;
                        }
                    }
                }
            }
        });
        peerPanel.add(new JScrollPane(peerList), BorderLayout.CENTER);

        // Assemble left panel top section
        JPanel leftTop = new JPanel();
        leftTop.setLayout(new BoxLayout(leftTop, BoxLayout.Y_AXIS));
        leftTop.add(idPanel);
        leftTop.add(myIpPanel);
        leftTop.add(statusLabel);

        leftPanel.add(leftTop, BorderLayout.NORTH);
        leftPanel.add(peerPanel, BorderLayout.CENTER);

        // ── CENTER PANEL (Chat) ──────────────────────────────────────────────
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(250, 250, 250));
        chatDoc = chatArea.getStyledDocument();

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder("Messages"));

        // ── BOTTOM PANEL (Input) ─────────────────────────────────────────────
        JPanel inputPanel = new JPanel(new BorderLayout(5, 5));
        inputPanel.setBorder(BorderFactory.createTitledBorder("Send Message"));

        JPanel targetRow = new JPanel(new BorderLayout(5, 0));
        targetRow.add(new JLabel("To (Device ID):"), BorderLayout.WEST);
        targetInput = new JTextField();
        targetInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        targetInput.setToolTipText("Paste the full Device ID of the recipient, or click a peer above");
        targetRow.add(targetInput, BorderLayout.CENTER);

        JPanel msgRow = new JPanel(new BorderLayout(5, 0));
        messageInput = new JTextField();
        messageInput.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        sendButton = new JButton("Send >");
        sendButton.setBackground(new Color(0, 140, 90));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        msgRow.add(messageInput, BorderLayout.CENTER);
        msgRow.add(sendButton, BorderLayout.EAST);

        inputPanel.add(targetRow, BorderLayout.NORTH);
        inputPanel.add(msgRow, BorderLayout.CENTER);

        // ── ASSEMBLE ─────────────────────────────────────────────────────────
        add(leftPanel, BorderLayout.WEST);
        add(chatScroll, BorderLayout.CENTER);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private void showManualConnectDialog() {
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(new JLabel("Enter the IP address of the other laptop:"));
        panel.add(new JLabel("(You can see their IP in the 'My IP Address' box on their screen)"));
        JTextField ipField = new JTextField("192.168.1.");
        ipField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        panel.add(ipField);

        int result = JOptionPane.showConfirmDialog(this, panel,
            "Manual Connect by IP", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String ip = ipField.getText().trim();
            if (!ip.isEmpty()) {
                appendSystem("Manually connecting to: " + ip + "...");
                node.connectManually(ip);
            }
        }
    }

    private void setupStyles() {
        StyleContext sc = StyleContext.getDefaultStyleContext();
        Style def = sc.getStyle(StyleContext.DEFAULT_STYLE);

        styleReceived = chatArea.addStyle("received", def);
        StyleConstants.setForeground(styleReceived, new Color(0, 100, 180));
        StyleConstants.setBold(styleReceived, true);

        styleSent = chatArea.addStyle("sent", def);
        StyleConstants.setForeground(styleSent, new Color(0, 130, 60));

        styleRelayed = chatArea.addStyle("relayed", def);
        StyleConstants.setForeground(styleRelayed, new Color(150, 100, 0));
        StyleConstants.setItalic(styleRelayed, true);

        styleSystem = chatArea.addStyle("system", def);
        StyleConstants.setForeground(styleSystem, new Color(120, 120, 120));
        StyleConstants.setItalic(styleSystem, true);
    }

    private void setupListeners() {
        sendButton.addActionListener(e -> sendMessage());
        messageInput.addActionListener(e -> sendMessage());
    }

    private void sendMessage() {
        String to = targetInput.getText().trim();
        String text = messageInput.getText().trim();

        if (to.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Please enter a target Device ID.\nTip: Click on a connected peer in the left panel to auto-fill.",
                "No Target", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (text.isEmpty()) return;

        node.sendMessage(to, text);
        messageInput.setText("");
        messageInput.requestFocus();
    }

    private void appendToChat(String text, Style style) {
        SwingUtilities.invokeLater(() -> {
            try {
                chatDoc.insertString(chatDoc.getLength(), text + "\n", style);
                chatArea.setCaretPosition(chatDoc.getLength());
            } catch (BadLocationException e) {
                System.err.println("[UI] Append error: " + e.getMessage());
            }
        });
    }

    private void appendSystem(String text) {
        appendToChat("[" + TIME_FMT.format(Instant.now()) + "] " + text, styleSystem);
    }

    private String formatTime(long epochMs) {
        return TIME_FMT.format(Instant.ofEpochMilli(epochMs));
    }

    // ── MessageListener ───────────────────────────────────────────────────────

    @Override
    public void onMessageReceived(Message msg) {
        String time = formatTime(msg.timestamp());
        String fromShort = msg.from().substring(0, 8);
        appendToChat("[" + time + "] FROM " + fromShort + "...: " + msg.text(), styleReceived);
    }

    @Override
    public void onMessageSent(Message msg) {
        String toShort = msg.to().substring(0, 8);
        appendToChat("[" + formatTime(msg.timestamp()) + "] TO " + toShort + "...: " + msg.text(), styleSent);
    }

    @Override
    public void onMessageRelayed(Message msg) {
        String fromShort = msg.from().substring(0, 8);
        String toShort = msg.to().substring(0, 8);
        String preview = msg.text().length() > 25 ? msg.text().substring(0, 25) + "..." : msg.text();
        appendToChat("[RELAY] " + fromShort + "->>" + toShort + " TTL:" + msg.ttl() + " | " + preview, styleRelayed);
    }

    @Override
    public void onPeerConnected(Peer peer) {
        peers.put(peer.deviceId(), peer);
        SwingUtilities.invokeLater(() -> {
            String display = peer.deviceId().substring(0, 8) + "... @ " + peer.host();
            boolean exists = false;
            for (int i = 0; i < peerListModel.size(); i++) {
                if (peerListModel.get(i).equals(display)) { exists = true; break; }
            }
            if (!exists) peerListModel.addElement(display);
            int count = peerListModel.size();
            statusLabel.setText(count + " peer(s) connected");
            statusLabel.setForeground(new Color(0, 140, 0));
        });
        appendSystem("Peer connected: " + peer.deviceId().substring(0, 8) + "... @ " + peer.host());
    }

    @Override
    public void onPeerDisconnected(String deviceId) {
        Peer removed = peers.remove(deviceId);
        SwingUtilities.invokeLater(() -> {
            if (removed != null) {
                String display = removed.deviceId().substring(0, 8) + "... @ " + removed.host();
                peerListModel.removeElement(display);
            }
            int count = peerListModel.size();
            statusLabel.setText(count > 0 ? count + " peer(s)" : "No peers connected");
            statusLabel.setForeground(count > 0 ? Color.ORANGE : Color.RED);
        });
        appendSystem("Peer disconnected: " + deviceId.substring(0, 8) + "...");
    }

    @Override
    public void onStatusUpdate(String status) {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText(status);
            statusLabel.setForeground(status.startsWith("ERROR") ? Color.RED : Color.ORANGE);
        });
        appendSystem(status);
    }
}
