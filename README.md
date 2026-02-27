# MeshChat — Offline P2P Mesh Chat

A peer-to-peer mesh chat application for local LAN that works **without internet access**.
Multi-hop message routing means Laptop A can send to Laptop C even via Laptop B.

---

## 📁 Project Structure

```
meshchat-app/
├── meshchat/
│   ├── Main.java                    ← Entry point
│   ├── model/
│   │   ├── Message.java             ← Message record (id, from, to, ttl, text)
│   │   └── Peer.java                ← Peer record (deviceId, host, port)
│   ├── network/
│   │   ├── MeshNode.java            ← Core mesh logic (server + client + relay)
│   │   ├── PeerConnection.java      ← TCP connection wrapper
│   │   └── DiscoveryService.java    ← UDP broadcast peer discovery
│   ├── routing/
│   │   └── MessageRouter.java       ← TTL decrement, duplicate suppression
│   ├── ui/
│   │   ├── ChatWindow.java          ← Swing GUI
│   │   └── MessageListener.java     ← Event listener interface
│   └── util/
│       ├── DeviceIdentity.java      ← UUID persist to ~/.meshchat_id
│       └── SimpleJson.java          ← JSON parser (no external deps!)
├── build.bat                        ← Windows build
├── run.bat                          ← Windows run
├── build.sh                         ← Linux/Mac build
└── run.sh                           ← Linux/Mac run
```

---

## ⚙️ Prerequisites

- **Java JDK 17 or higher** (JDK, not just JRE — you need `javac`)
  - Download: https://adoptium.net (choose OpenJDK 21 LTS)
  - Verify: `javac -version`

- All laptops on the **same Wi-Fi/hotspot** (no internet needed)

---

## 🚀 Build & Run (Windows)

```bat
REM 1. Build
build.bat

REM 2. Run
run.bat
```

Or manually:
```bat
mkdir out
javac -d out --source-path . meshchat\Main.java meshchat\model\*.java meshchat\network\*.java meshchat\routing\*.java meshchat\ui\*.java meshchat\util\*.java
java -cp out meshchat.Main
```

## 🚀 Build & Run (Linux/Mac)

```bash
chmod +x build.sh run.sh
./build.sh
./run.sh
```

---

## 🌐 How Mesh Networking Works

```
Laptop A ←TCP→ Laptop B ←TCP→ Laptop C
```

Each node:
1. Starts a **TCP server** on port 45678
2. Broadcasts presence via **UDP** on port 45679 every 3 seconds
3. Auto-connects to any discovered peers
4. **Forwards** messages it's not the recipient for (with TTL decrement)

**Message flow (A→C via B):**
```
A sends msg {to:C, ttl:10}
B receives, sees to≠B, ttl>0 → forwards {to:C, ttl:9} to C
C receives, sees to==C → DELIVER → shows in UI
```

Duplicate suppression: each node tracks seen message IDs in an LRU cache.

---

## 🖥️ GUI Overview

**Left panel:**
- Your full device ID + Copy button
- Status (connected peers count)
- Discovered peers list (click to auto-fill target)

**Center:** Chat area with color-coded messages:
- 📨 Blue = received for you
- 📤 Green = sent by you
- 🔀 Orange italic = relayed through you
- Gray italic = system messages

**Bottom:**
- "To (Device ID)" field — paste or click peer to fill
- Message input + Send button (or press Enter)

---

## 🧪 Testing with 3 Laptops

### Setup
1. Connect all 3 laptops to **same Wi-Fi** (or hotspot from one laptop)
2. Copy the full `meshchat-app` folder to each laptop
3. Run `build.bat` then `run.bat` on each

### Verify Discovery
- Within ~10 seconds, peers should appear in the **left panel peer list**
- Status should show "✅ N peer(s) connected"

### Demo: A→C via B

1. **Disconnect Laptop A from Laptop C** (don't needed if they auto-connected through B)
   - Actually: just leave them all connected. The multi-hop works by flooding.
   - MeshChat uses **flood-and-forward**: message goes to ALL connected peers,
     each forwards to THEIR peers (with TTL decrement), until it reaches target.

2. On **Laptop A**:
   - Click on Laptop C's ID in the peer list (or paste it into "To" field)
   - Type a message → Send

3. On **Laptop B**: you'll see `🔀 [RELAY]` in the chat log

4. On **Laptop C**: you'll see `📨 FROM A...` — message delivered!

### Console logs to watch:
```
[ROUTER] FORWARD (ttl=10): abc123... → c3d4e5...   ← Laptop B forwarding
[ROUTER] DELIVER to self: abc123...                  ← Laptop C receiving
[NODE] *** MESSAGE FOR ME from a1b2c3...: Hello!    ← Laptop C delivering
```

---

## 🔧 Ports Used

| Port  | Protocol | Purpose               |
|-------|----------|-----------------------|
| 45678 | TCP      | Mesh message routing  |
| 45679 | UDP      | Peer discovery        |

**Windows Firewall:** You'll likely get a Windows Defender prompt — click **"Allow Access"** for both ports.

If no prompt appears, manually allow:
```bat
netsh advfirewall firewall add rule name="MeshChat TCP" dir=in action=allow protocol=TCP localport=45678
netsh advfirewall firewall add rule name="MeshChat UDP" dir=in action=allow protocol=UDP localport=45679
```

---

## 🔍 Troubleshooting

| Problem | Solution |
|---------|----------|
| Peers not discovered | Check firewall (UDP 45679). Ensure same subnet. |
| "Port in use" error | Another instance running. Kill it or use different machine. |
| Messages not delivered | Confirm you're using the FULL device ID (copy with the button) |
| Build fails | Ensure `javac` is available (JDK not JRE). `javac -version` |
| Connection drops | Normal — auto-reconnect happens every 3–5 seconds |
| No peers after 30 sec | Try: disable VPN, check same SSID, ping between machines |

---

## 🎭 Hackathon Demo Script (6 minutes)

**Setup (before demo):** All 3 laptops running, peers connected, windows visible.

**Minute 0–1: Introduction**
> "This is MeshChat — a peer-to-peer offline messaging app that works with NO internet.
> Each laptop is a node in a mesh network. Look — they've already discovered each other automatically."

Point to the peer list in the left panel.

**Minute 1–2: Simple message**
> "Let me send a message from Laptop A to Laptop B directly."

Type message, send. Show it appearing on B.

**Minute 2–4: Multi-hop demo**
> "Now here's the impressive part. I'm going to send a message from A to C — 
> but watch Laptop B's screen. The message routes THROUGH B."

Send from A to C. Show:
- B's chat: `🔀 [RELAY]` appears — B is forwarding
- C's chat: `📨` appears — C received it
- Console/log showing TTL decrements

**Minute 4–5: Explain the protocol**
> "Each message has a TTL — it starts at 10 and decrements at each hop.
> Duplicate messages are dropped, so the network doesn't flood.
> Device IDs are persistent UUIDs — this is your identity across restarts."

**Minute 5–6: Q&A + "What's next"**
> "This is a foundation. With more time, we'd add: encryption, persistent message store,
> mobile clients, and smarter routing tables instead of flooding."

---

## 🏗️ Architecture Notes

- **No external dependencies** — pure Java stdlib only
- **Virtual threads** (Java 21) for all I/O — efficient and simple
- **ConcurrentHashMap** for thread-safe peer tracking
- **LRU cache** for seen message IDs (prevents infinite loops)
- **Device ID** persisted to `~/.meshchat_id` (survives restarts)

---

*Built for hackathon demo. Pure Java 21, no external libraries.*
