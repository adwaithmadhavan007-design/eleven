package meshchat.ui;

import meshchat.model.Message;
import meshchat.model.Peer;

public interface MessageListener {
    void onMessageReceived(Message msg);
    void onMessageSent(Message msg);
    void onMessageRelayed(Message msg);
    void onPeerConnected(Peer peer);
    void onPeerDisconnected(String deviceId);
    void onStatusUpdate(String status);
}
