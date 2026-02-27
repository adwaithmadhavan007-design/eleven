package meshchat.util;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

public record DeviceIdentity(String deviceId) {
    private static final Path ID_FILE = Path.of(System.getProperty("user.home"), ".meshchat_id");

    public static DeviceIdentity load() {
        if (Files.exists(ID_FILE)) {
            try {
                String id = Files.readString(ID_FILE).trim();
                if (!id.isBlank()) {
                    System.out.println("[IDENTITY] Loaded existing device ID: " + id);
                    return new DeviceIdentity(id);
                }
            } catch (IOException e) {
                System.err.println("[IDENTITY] Failed to read ID file: " + e.getMessage());
            }
        }

        String newId = UUID.randomUUID().toString();
        try {
            Files.writeString(ID_FILE, newId);
            System.out.println("[IDENTITY] Generated new device ID: " + newId);
        } catch (IOException e) {
            System.err.println("[IDENTITY] Failed to save ID file: " + e.getMessage());
        }
        return new DeviceIdentity(newId);
    }
}
