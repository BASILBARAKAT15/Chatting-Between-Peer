Peer-to-Peer Chatting Application
This project implements a Peer-to-Peer Chatting Application using Java with UDP Socket Programming (Part 1) and extends it with TCP Socket Programming for client registration and server tracking (Part 2). The application includes a GUI with various interactive components and meets all specified requirements.
Overview

Part 1: A non-blocking UDP-based chat application with features like timestamped messages, colored message display, message deletion, archiving, and logging.
Part 2: Extends Part 1 by adding TCP client-server functionality for user registration, online user tracking, status management, file transfer, and additional logging.

Features
Part 1

GUI with text boxes for IP addresses and port numbers, text areas for chat, and buttons for sending, deleting, and archiving messages.
Timestamp added to each sent and received message.
Sent messages in yellow, received messages in orange.
Buttons to delete selected messages or all conversations, with deleted messages moved to an archive tab.
Archived messages auto-removed after 2 minutes, with an option to restore them.
Log file (chat_log.txt) recording all activities.

Part 2

TCP Client for registration to a TCP Server, displaying a list of active clients.
TCP Server with GUI showing active client list and option to add new users.
Login/logout functionality with username/password validation (case-insensitive) from a credentials file.
Unique text colors for each user.
Status options (Active, Busy, Away) with automatic "Away" after 30 seconds of inactivity, reverting to "Active" on user activity.
"Send to All" option.
Chat history export to a text file.
File transfer between peers with statistics (packets, file size, E2E delay, jitter).
Display of last login time and time elapsed since login.
Comprehensive log file for all events.

Requirements

Java Development Kit (JDK) 8 or higher.
Execution on different hosts (not on a single machine).

Setup

Clone the repository or copy the source files (ChatClient.java, TCPServer.java).
Ensure the credentials file (users.txt) is in the project directory with the format:User Name
Password
Ali
1234
Saly
A20B
Aws
ABcd
Adam
1Cb2


Compile and run ChatClient.java on multiple machines with different IP addresses.
Run TCPServer.java on one machine to manage client registration.

Usage

Part 1: Enter source and destination IPs/ports, type messages, and use buttons for chat management.
Part 2: Log in with valid credentials, select peers from the active list, set status, send files, and export chat history.

Files

ChatClient.java: Main client application with UDP and TCP functionality.
TCPServer.java: Server application tracking active clients.
chat_log.txt: Log file for all activities.
users.txt: Credentials file for login.

Notes

Ensure ports (e.g., 6000, 6001) are not in use or inaccessible on the network.
The application must be tested on different hosts as per requirements.

Future Improvements

Enhance GUI responsiveness.
Add encryption for secure communication.
Improve file transfer reliability.
