Socket API Project – CE Networks-1
📌 Overview
This project implements a Peer-to-Peer Chat Application in Java with both UDP (Part 1) and TCP + UDP Hybrid (Part 2) modes.
The system is designed to simulate real-time communication between multiple clients, with a server managing user registration, status updates, and active client lists.

Part 1: UDP-based peer-to-peer chat with GUI, message archiving, and logging.

Part 2: TCP server to track online users, handle authentication, and enable enhanced peer management while keeping UDP for actual chat.

🛠 Features
Part 1 – UDP Peer-to-Peer Chat
GUI with text boxes, buttons, dropdowns, and text areas.

Input fields for Source IP/Port and Destination IP/Port.

Display sent and received messages in a conversation area.

Timestamps for all messages.

Color-coded messages:

🟨 Yellow – Sent messages

🟧 Orange – Received messages

Message management:

Delete selected message(s).

Delete all conversation from both peers.

Archive system:

Deleted messages moved to archive.

Messages auto-deleted from archive after 2 minutes.

Messages can be restored from archive.

Non-blocking code (Swing-friendly).

Log file recording:

Send/receive events

Deletions

Archiving/restoring actions

Part 2 – TCP Server & Client Integration
TCP Client integrated into the UDP chat application.

TCP Server maintains a list of active UDP clients.

User authentication:

Stored credentials in server-side file.

Username/password validation (case-insensitive).

Login success/failure messages.

Display of active clients with:

Username

IP address

Port number

Send-to-all option.

Color-coded usernames (different color per user).

Status management:

Active / Busy / Away

Auto-set to Away after 30 seconds of inactivity.

Keyboard/mouse activity returns status to Active.

User management on server:

Add new users via GUI.

Prevent duplicate usernames.

Last login timestamp display in client.

Chat history export to text file.

Extra File Transfer Task (Part 2)
Peer-to-peer file sending:

Browse local storage to select file.

Default receive location: C:\ (configurable).

Statistics:

Packets sent/received

File size

End-to-end delay

Delay jitter

📂 Project Structure
bash
نسخ
تحرير
/src
  ├── UDPChatApp.java        # Part 1 client code
  ├── TCPServer.java         # Part 2 server code
  ├── TCPClientHandler.java  # Server-side client handler
  ├── Client.java            # Part 2 client (UDP+TCP integration)
  └── utils/
       ├── Logger.java       # Logging utility
       ├── FileTransfer.java # File sending/receiving functions
       └── Message.java      # Message object model
/logs
  └── chat.log               # Application log file
/archive
  └── archived_messages.txt  # Archived messages storage
💻 How to Run
Part 1 – UDP Chat
Compile the Java source:

bash
نسخ
تحرير
javac UDPChatApp.java
Start two instances of the client on different hosts or different IP/ports.

Enter:

Local IP & port

Remote IP & port

Click Start to open UDP socket.

Type message → Send.

Use Delete / Delete All / Archive as needed.

Part 2 – TCP Server & Client
Compile server:

bash
نسخ
تحرير
javac TCPServer.java
java TCPServer
Compile and start client:

bash
نسخ
تحرير
javac Client.java
java Client
Login with valid credentials from server-side file.

Choose recipient from active list.

Continue chat (UDP) while server updates status.

📑 Logging
The system maintains a human-readable log file (chat.log) including:

Login/logout events

Message sends/receives with content & timestamps

Message deletions

Archival & restore events

Status changes

📷 Sample GUI
Part 1 – UDP Chat Window:
(screenshot of two clients chatting)

Part 2 – TCP Server Active Clients List:
(screenshot of server GUI with online users)

⚠ Notes
Blocking code is not allowed in any part (all networking runs in background threads).

The project must be tested on different hosts — not just localhost.

For file transfer, large files may require fragmentation handling.

✍ Authors
 Basil Barakat, CE Networks-1
