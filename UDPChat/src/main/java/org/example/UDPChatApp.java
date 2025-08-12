package org.example;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

public class UDPChatApp extends JFrame {

    // --- UI components (existing) ---
    private JTextField sourceIPField, sourcePortField, destIPField, destPortField, messageField;
    private JTextPane chatPane;
    private JButton sendBtn, deleteSelectedBtn, deleteAllBtn, archiveBtn;

    // --- Additional UI for extras ---
    private JButton exportHistoryBtn;
    private JLabel lastLoginLabel;
    private JLabel elapsedLabel;
    private JButton chooseSaveDirBtn;
    private JTextField saveDirField;
    private JButton sendFileBtn, receiveStatsBtn; // send file, show last file-transfer stats

    // Archive UI
    private DefaultListModel<String> archiveListModel;
    private JList<String> archiveList;

    // --- Network / concurrency ---
    private DatagramSocket socket;
    private ExecutorService executor;
    private List<ArchivedMessage> archive;
    private File logFile;

    // --- Time & logging formats ---
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter shortTime = DateTimeFormatter.ofPattern("HH:mm:ss");

    // --- Chat history storage ---
    private final List<String> chatHistory = new ArrayList<>();

    // --- Login/elapsed ---
    private String currentUsername = "unknown";
    private LocalDateTime lastSuccessfulLogin = null;
    private javax.swing.Timer elapsedTimer;

    // --- File transfer state / stats ---
    private volatile boolean sendingFile = false;
    private volatile boolean receivingFile = false;
    private File lastSentFile = null;
    private File lastReceivedFile = null;
    private final Object fileTransferLock = new Object();
    private FileTransferStats lastTransferStats = null;

    // Default receiver save dir
    private String defaultSaveDir = "C:\\";

    // Constructor
    public UDPChatApp() {
        super("UDP Peer-to-Peer Chat (Enhanced)");

        initUI();
        initState();
        startListening(); // start UDP receive thread
    }

    // Initialize UI layout
    private void initUI() {
        setLayout(new BorderLayout(6, 6));

        // Top: network fields
        JPanel topPanel = new JPanel(new GridLayout(2, 4, 6, 6));
        sourceIPField = new JTextField("0.0.0.0"); // bind to all interfaces by default
        sourcePortField = new JTextField("5000");
        destIPField = new JTextField("192.168.193.164"); // change to peer IP
        destPortField = new JTextField("6000");

        topPanel.add(new JLabel("Source IP:"));
        topPanel.add(sourceIPField);
        topPanel.add(new JLabel("Source Port:"));
        topPanel.add(sourcePortField);
        topPanel.add(new JLabel("Dest IP:"));
        topPanel.add(destIPField);
        topPanel.add(new JLabel("Dest Port:"));
        topPanel.add(destPortField);

        add(topPanel, BorderLayout.NORTH);

        // Center: chat pane
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        JScrollPane chatScroll = new JScrollPane(chatPane);
        add(chatScroll, BorderLayout.CENTER);

        // Right side: controls / extra info
        JPanel rightPanel = new JPanel(new GridLayout(12, 1, 6, 6));

        lastLoginLabel = new JLabel("Last Login: -");
        elapsedLabel = new JLabel("Elapsed: 00:00:00");
        exportHistoryBtn = new JButton("Export Chat History");
        chooseSaveDirBtn = new JButton("Save Dir (change)");
        saveDirField = new JTextField(defaultSaveDir);
        sendFileBtn = new JButton("Send File...");
        receiveStatsBtn = new JButton("Show Last File Stats");

        rightPanel.add(lastLoginLabel);
        rightPanel.add(elapsedLabel);
        rightPanel.add(exportHistoryBtn);

        rightPanel.add(new JLabel("Default Save Dir:"));
        rightPanel.add(saveDirField);
        rightPanel.add(chooseSaveDirBtn);

        rightPanel.add(sendFileBtn);
        rightPanel.add(receiveStatsBtn);

        add(rightPanel, BorderLayout.EAST);

        // Bottom: input & buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(6, 6));
        messageField = new JTextField();
        sendBtn = new JButton("Send");
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(sendBtn, BorderLayout.EAST);

        JPanel lowerBtns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        deleteSelectedBtn = new JButton("Delete Selected");
        deleteAllBtn = new JButton("Delete All");
        archiveBtn = new JButton("Archive View");
        lowerBtns.add(deleteSelectedBtn);
        lowerBtns.add(deleteAllBtn);
        lowerBtns.add(archiveBtn);

        bottomPanel.add(lowerBtns, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Archive components (constructed when needed)
        archive = new ArrayList<>();

        // Action listeners
        sendBtn.addActionListener(e -> sendMessage());
        deleteSelectedBtn.addActionListener(e -> deleteSelectedMessage());
        deleteAllBtn.addActionListener(e -> deleteAllMessages());
        archiveBtn.addActionListener(e -> showArchiveWindow());
        exportHistoryBtn.addActionListener(e -> exportChatHistory());
        chooseSaveDirBtn.addActionListener(e -> chooseSaveDir());
        sendFileBtn.addActionListener(e -> chooseAndSendFile());
        receiveStatsBtn.addActionListener(e -> showLastTransferStats());

        // Basic window settings
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Initialize files / executor
    private void initState() {
        logFile = new File("application_log.txt");
        executor = Executors.newCachedThreadPool();

        // elapsed timer (updates label every second if logged in)
        elapsedTimer = new javax.swing.Timer(1000, e -> updateElapsedLabel());
        elapsedTimer.setRepeats(true);

        // Attempt to read last login file for default user (if present)
        // We'll update currentUsername when user explicitly sets login (optional)
    }

    // --- UDP Listening for incoming messages & file control ---
    private void startListening() {
        executor.submit(() -> {
            int port;
            try {
                port = Integer.parseInt(sourcePortField.getText().trim());
            } catch (NumberFormatException ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Invalid source port"));
                return;
            }
            try {
                socket = new DatagramSocket(port, InetAddress.getByName(sourceIPField.getText().trim()));
                socket.setSoTimeout(0); // blocking receive in background thread is fine
                log("UDP listening on " + socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort());
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Failed to open UDP socket: " + ex.getMessage()));
                log("Failed to open UDP socket: " + ex.getMessage());
                return;
            }

            byte[] buffer = new byte[1500];
            while (!socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); // blocking
                    processIncomingPacket(packet);
                } catch (IOException ioe) {
                    // Socket closed or error
                    log("Socket receive error: " + ioe.getMessage());
                    break;
                }
            }
        });
    }

    // Distinguish between control messages and chat text / file data
    private void processIncomingPacket(DatagramPacket packet) {
        // Check if it's a simple control string (UTF-8) starting with "CTRL:"
        byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
        String maybeStr = tryDecodeString(data);
        if (maybeStr != null && maybeStr.startsWith("CTRL:")) {
            // control commands: CTRL:MSG:<timestamp>|<text>  or CTRL:FILE_START|filename|filesize|packetSize
            handleControlMessage(maybeStr, packet.getAddress(), packet.getPort());
            return;
        }

        // Otherwise, it may be file data (binary with 4-byte seq prefix)
        handleFileDataPacket(data, packet.getAddress(), packet.getPort());
    }

    // Try decode as UTF-8 string; return null if non-printable binary likely
    private String tryDecodeString(byte[] data) {
        try {
            String s = new String(data, StandardCharsets.UTF_8);
            // Heuristic: if contains "CTRL:" or printable, treat as string
            if (s.startsWith("CTRL:") || s.chars().allMatch(c -> (c >= 9 && c <= 126) || c == '\n' || c == '\r' || c == '\t')) {
                return s;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // Handle control messages (chat text or file-control)
    private void handleControlMessage(String ctrl, InetAddress senderAddr, int senderPort) {
        // Examples:
        // CTRL:MSG|[HH:mm:ss] username: text
        // CTRL:FILE_START|filename|filesize|packetSize
        // CTRL:FILE_END
        if (ctrl.startsWith("CTRL:MSG|")) {
            String payload = ctrl.substring("CTRL:MSG|".length());
            SwingUtilities.invokeLater(() -> {
                displayMessage(payload, false);
                chatHistory.add(shortTime.format(LocalDateTime.now()) + " " + payload);
                logEvent("Received message from " + senderAddr.getHostAddress() + ":" + senderPort + " -> " + payload);
            });
            return;
        }

        if (ctrl.startsWith("CTRL:FILE_START|")) {
            // Parse
            String[] parts = ctrl.split("\\|");
            if (parts.length >= 4) {
                String filename = parts[1];
                long fileSize = Long.parseLong(parts[2]);
                int packetSize = Integer.parseInt(parts[3]);
                // start receiver worker
                executor.submit(() -> receiveFileWorkflow(senderAddr, senderPort, filename, fileSize, packetSize));
            }
            return;
        }

        if (ctrl.startsWith("CTRL:FILE_END")) {
            // ignore: handled in receive flow
            return;
        }
    }



    // Display a chat message in pane (with colors for sent/received)
    private void displayMessage(String msg, boolean sent) {
        StyledDocument doc = chatPane.getStyledDocument();
        Style style = chatPane.addStyle("Style_" + (sent ? "S" : "R") + "_" + UUID.randomUUID(), null);
        StyleConstants.setForeground(style, sent ? Color.YELLOW.darker() : Color.ORANGE.darker());
        StyleConstants.setFontSize(style, 20);
        try {
            doc.insertString(doc.getLength(), msg + "\n", style);
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    // Send a chat message (control message over UDP so receiver can parse)
    private void sendMessage() {
        String text = messageField.getText().trim();
        if (text.isEmpty()) return;
        String timestamp = shortTime.format(LocalDateTime.now());
        String full = "[" + timestamp + "] " + currentUsername + ": " + text;
        String ctrl = "CTRL:MSG|" + full;
        byte[] data = ctrl.getBytes(StandardCharsets.UTF_8);
        try {
            InetAddress destIP = InetAddress.getByName(destIPField.getText().trim());
            int destPort = Integer.parseInt(destPortField.getText().trim());
            DatagramPacket packet = new DatagramPacket(data, data.length, destIP, destPort);
            socket.send(packet);
            displayMessage(full, true);
            chatHistory.add(shortTime.format(LocalDateTime.now()) + " " + full);
            logEvent("Sent message to " + destIP.getHostAddress() + ":" + destPort + " -> " + full);
            messageField.setText("");
        } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Send failed: " + ex.getMessage()));
            logEvent("Send failed: " + ex.getMessage());
        }
    }

    // --- Archive & Delete operations ---
    private void deleteSelectedMessage() {
        String selectedText = chatPane.getSelectedText();
        if (selectedText != null && !selectedText.trim().isEmpty()) {
            archiveMessage(selectedText.trim());
            chatPane.replaceSelection("");
            logEvent("Deleted message: " + selectedText.trim());
        }
    }

    private void deleteAllMessages() {
        String allText = chatPane.getText();
        if (!allText.trim().isEmpty()) {
            archiveMessage(allText.trim());
            chatPane.setText("");
            logEvent("Deleted all messages");
        }
    }

    private void archiveMessage(String msg) {
        ArchivedMessage am = new ArchivedMessage(msg, System.currentTimeMillis());
        archive.add(am);
        // Auto-remove after 2 minutes
        new javax.swing.Timer(120000, e -> {
            archive.remove(am);
            if (archiveListModel != null) SwingUtilities.invokeLater(() -> archiveListModel.removeElement(am.message));
        }) {{
            setRepeats(false);
            start();
        }};
    }

    private void showArchiveWindow() {
        JFrame archiveFrame = new JFrame("Archived Messages");
        archiveListModel = new DefaultListModel<>();
        archive.forEach(a -> archiveListModel.addElement(a.message));
        archiveList = new JList<>(archiveListModel);

        JScrollPane scrollPane = new JScrollPane(archiveList);
        JButton restoreBtn = new JButton("Restore");
        restoreBtn.addActionListener(e -> restoreMessage());

        archiveFrame.add(scrollPane, BorderLayout.CENTER);
        archiveFrame.add(restoreBtn, BorderLayout.SOUTH);
        archiveFrame.setSize(500, 400);
        archiveFrame.setLocationRelativeTo(this);
        archiveFrame.setVisible(true);
    }

    private void restoreMessage() {
        String selected = archiveList.getSelectedValue();
        if (selected == null) return;
        displayMessage(selected, false);
        archiveListModel.removeElement(selected);
        archive.removeIf(a -> a.message.equals(selected));
        logEvent("Restored message: " + selected);
    }

    // --- Export chat history ---
    private void exportChatHistory() {
        String filename = "chat_history_" + currentUsername + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt";
        File out = new File(filename);
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(out))) {
            for (String line : chatHistory) bw.write(line + System.lineSeparator());
            JOptionPane.showMessageDialog(this, "Chat history exported to " + out.getAbsolutePath());
            logEvent("Exported chat history to " + out.getAbsolutePath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage());
            logEvent("Export failed: " + e.getMessage());
        }
    }

    // --- Last login & elapsed time features ---
    // Save last login to file
    private void saveLastLogin() {
        if (currentUsername == null || currentUsername.isEmpty()) return;
        File f = new File("last_login_" + sanitizeFilename(currentUsername) + ".txt");
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
            bw.write(lastSuccessfulLogin.format(timeFormat));
            logEvent("Saved last login for " + currentUsername + " -> " + lastSuccessfulLogin.format(timeFormat));
        } catch (IOException e) {
            logEvent("Failed saving last login: " + e.getMessage());
        }
    }

    // Load last login (if exists) for given username
    private void loadLastLoginFor(String username) {
        File f = new File("last_login_" + sanitizeFilename(username) + ".txt");
        if (!f.exists()) {
            lastLoginLabel.setText("Last Login: -");
            lastSuccessfulLogin = null;
            return;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line = br.readLine();
            if (line != null && !line.isEmpty()) {
                lastSuccessfulLogin = LocalDateTime.parse(line, timeFormat);
                lastLoginLabel.setText("Last Login: " + lastSuccessfulLogin.format(timeFormat));
                logEvent("Loaded last login for " + username + " -> " + line);
            }
        } catch (Exception e) {
            logEvent("Failed to load last login: " + e.getMessage());
        }
    }

    private void updateElapsedLabel() {
        if (lastSuccessfulLogin == null) {
            elapsedLabel.setText("Elapsed: 00:00:00");
            return;
        }
        Duration d = Duration.between(lastSuccessfulLogin, LocalDateTime.now());
        long hours = d.toHours();
        long mins = d.toMinutes() % 60;
        long secs = d.getSeconds() % 60;
        elapsedLabel.setText(String.format("Elapsed: %02d:%02d:%02d", hours, mins, secs));
    }

    // Helper to sanitize filenames
    private String sanitizeFilename(String s) {
        return s.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
    }

    // --- File transfer (stop-and-wait over UDP) ---
    private void chooseAndSendFile() {
        if (sendingFile) {
            JOptionPane.showMessageDialog(this, "Already sending a file—wait until current transfer ends.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        int res = chooser.showOpenDialog(this);
        if (res != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, "Invalid file.");
            return;
        }
        // get dest ip/port
        String destIP = destIPField.getText().trim();
        int destPort;
        try {
            destPort = Integer.parseInt(destPortField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid destination port");
            return;
        }
        // start file sender
        executor.submit(() -> sendFileWorkflow(file, destIP, destPort));
    }

    // Sender workflow
    private void sendFileWorkflow(File file, String destIp, int destPort) {
        synchronized (fileTransferLock) {
            sendingFile = true;
            lastSentFile = file;
            FileTransferStats stats = new FileTransferStats();
            stats.fileName = file.getName();
            stats.fileSizeBytes = file.length();
            InetAddress destAddr;
            try {
                destAddr = InetAddress.getByName(destIp);
            } catch (Exception e) {
                logEvent("File send failed: bad dest IP");
                sendingFile = false;
                return;
            }

            int packetPayloadSize = 1000; // payload per packet (excluding 4 byte seq)
            int totalPackets = (int) ((file.length() + packetPayloadSize - 1) / packetPayloadSize);
            stats.totalPackets = totalPackets;

            // send FILE_START control msg
            String ctrl = "CTRL:FILE_START|" + file.getName() + "|" + file.length() + "|" + packetPayloadSize;
            try {
                byte[] ctrlB = ctrl.getBytes(StandardCharsets.UTF_8);
                DatagramPacket ctrlP = new DatagramPacket(ctrlB, ctrlB.length, destAddr, destPort);
                socket.send(ctrlP);
                logEvent("Sent FILE_START to " + destIp + ":" + destPort + " name=" + file.getName());
            } catch (IOException e) {
                logEvent("Failed to send FILE_START: " + e.getMessage());
                sendingFile = false;
                return;
            }

            // Send packets stop-and-wait with ACK
            long startTime = System.currentTimeMillis();
            stats.startTime = startTime;
            int seq = 0;
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                byte[] payload = new byte[packetPayloadSize];
                int read;
                while ((read = bis.read(payload)) != -1) {
                    seq++;
                    byte[] packetData = ByteBuffer.allocate(4 + read).putInt(seq).put(payload, 0, read).array();
                    DatagramPacket dp = new DatagramPacket(packetData, packetData.length, destAddr, destPort);

                    boolean acked = false;
                    int retries = 0;
                    while (!acked && retries < 10) {
                        socket.send(dp);
                        stats.packetsSent++;
                        // wait for ACK with timeout
                        try {
                            socket.setSoTimeout(2000); // 2s
                            byte[] ackBuf = new byte[200];
                            DatagramPacket ackP = new DatagramPacket(ackBuf, ackBuf.length);
                            socket.receive(ackP);
                            String ackStr = new String(ackP.getData(), 0, ackP.getLength(), StandardCharsets.UTF_8);
                            if (ackStr.startsWith("ACK|")) {
                                int ackSeq = Integer.parseInt(ackStr.split("\\|")[1]);
                                if (ackSeq == seq) {
                                    acked = true;
                                    break;
                                }
                            }
                        } catch (SocketTimeoutException ste) {
                            retries++;
                            logEvent("Timeout waiting ACK for seq " + seq + " (retry " + retries + ")");
                        }
                    }
                    if (!acked) {
                        logEvent("Failed to receive ACK for seq " + seq + " - aborting transfer");
                        break;
                    }
                }
            } catch (IOException ex) {
                logEvent("Error reading file: " + ex.getMessage());
            } finally {
                // send FILE_END
                try {
                    String end = "CTRL:FILE_END";
                    socket.send(new DatagramPacket(end.getBytes(StandardCharsets.UTF_8), end.length(), destAddr, destPort));
                } catch (IOException ignored) {}
                long endTime = System.currentTimeMillis();
                stats.endTime = endTime;
                stats.durationMs = endTime - startTime;
                lastTransferStats = stats;
                logEvent("File send finished. Stats: " + stats.toString());
                sendingFile = false;
                // reset timeout to infinite
                try { socket.setSoTimeout(0); } catch (SocketException ignored) {}
            }
        }
    }

    // Receiver workflow: called when we get FILE_START control message
    private void receiveFileWorkflow(InetAddress senderAddr, int senderPort, String filename, long fileSize, int packetPayloadSize) {
        synchronized (fileTransferLock) {
            if (receivingFile) {
                logEvent("Already receiving a file; ignoring new incoming file start.");
                return;
            }
            receivingFile = true;
            lastReceivedFile = new File(defaultSaveDir, filename);
            FileTransferStats stats = new FileTransferStats();
            stats.fileName = filename;
            stats.fileSizeBytes = fileSize;
            stats.startTime = System.currentTimeMillis();

            try (RandomAccessFile raf = new RandomAccessFile(lastReceivedFile, "rw")) {
                int expectedSeq = 1;
                long bytesWritten = 0;
                long prevArrival = -1;
                List<Long> interArrival = new ArrayList<>();
                byte[] buf = new byte[packetPayloadSize + 10 + 4]; // some extra margin

                // Keep receiving until FILE_END or file complete
                while (true) {
                    DatagramPacket dp = new DatagramPacket(buf, buf.length);
                    socket.receive(dp);
                    byte[] data = Arrays.copyOf(dp.getData(), dp.getLength());

                    // Check if control end
                    String maybe = tryDecodeString(data);
                    if (maybe != null && maybe.startsWith("CTRL:FILE_END")) {
                        logEvent("Received FILE_END");
                        break;
                    }

                    // Otherwise expect binary packet with 4-byte seq
                    if (data.length < 4) continue;
                    ByteBuffer bb = ByteBuffer.wrap(data);
                    int seq = bb.getInt();
                    byte[] payload = Arrays.copyOfRange(data, 4, data.length);

                    long arrival = System.currentTimeMillis();
                    if (prevArrival != -1) {
                        interArrival.add(Math.abs(arrival - prevArrival));
                    }
                    prevArrival = arrival;

                    // Only write if seq == expected (stop-and-wait ensures in-order)
                    if (seq == expectedSeq) {
                        raf.seek((long) (expectedSeq - 1) * packetPayloadSize);
                        raf.write(payload);
                        bytesWritten += payload.length;
                        expectedSeq++;
                        stats.packetsReceived++;
                        // send ACK
                        String ack = "ACK|" + seq;
                        byte[] ackB = ack.getBytes(StandardCharsets.UTF_8);
                        DatagramPacket ackP = new DatagramPacket(ackB, ackB.length, senderAddr, senderPort);
                        socket.send(ackP);
                    } else {
                        // duplicate or out of order - re-ACK last good seq
                        int ackSeq = expectedSeq - 1;
                        if (ackSeq >= 1) {
                            String ack = "ACK|" + ackSeq;
                            socket.send(new DatagramPacket(ack.getBytes(StandardCharsets.UTF_8), ack.getBytes(StandardCharsets.UTF_8).length, senderAddr, senderPort));
                        }
                    }

                    // stop when file size reached
                    if (bytesWritten >= fileSize) {
                        logEvent("Received expected file bytes");
                        break;
                    }
                } // while
                stats.endTime = System.currentTimeMillis();
                stats.durationMs = stats.endTime - stats.startTime;
                // compute jitter approx as average interArrival delta
                stats.jitterMs = computeJitter(interArrival);
                stats.bytesTransferred = lastReceivedFile.length();
                lastTransferStats = stats;

                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "File received: " + lastReceivedFile.getAbsolutePath());
                });
                logEvent("File received saved to " + lastReceivedFile.getAbsolutePath() + ". Stats: " + stats.toString());
            } catch (IOException e) {
                logEvent("File receive error: " + e.getMessage());
            } finally {
                receivingFile = false;
            }
        }
    }

    // compute jitter as average absolute diff of inter-arrival times
    private double computeJitter(List<Long> interArrival) {
        if (interArrival.size() < 2) return 0.0;
        double sum = 0;
        for (int i = 1; i < interArrival.size(); i++) {
            sum += Math.abs(interArrival.get(i) - interArrival.get(i - 1));
        }
        return sum / (interArrival.size() - 1);
    }

    // Handle binary file data when packet arrives unexpectedly (called from processIncomingPacket)
    private void handleFileDataPacket(byte[] data, InetAddress senderAddr, int senderPort) {
        // For simplicity we forward the binary to the receiveFileWorkflow via leaving it in socket.receive loop,
        // because receiveFileWorkflow also performs socket.receive(); here we just ignore unexpected binary when not in receive flow.
        // In our design, receiver enters receiveFileWorkflow immediately when getting FILE_START control message,
        // and continues to read on same socket.
    }

    // Show last transfer stats
    private void showLastTransferStats() {
        if (lastTransferStats == null) {
            JOptionPane.showMessageDialog(this, "No file transfer stats yet.");
            return;
        }
        JOptionPane.showMessageDialog(this, lastTransferStats.toMultilineString());
    }

    // Choose default save directory
    private void chooseSaveDir() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(new File(defaultSaveDir));
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            defaultSaveDir = chooser.getSelectedFile().getAbsolutePath();
            saveDirField.setText(defaultSaveDir);
            logEvent("Default save dir changed to " + defaultSaveDir);
        }
    }

    // Utility: decode possible text
    private String tryDecodeStringSafely(byte[] data) {
        try {
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // --- Logging utilities ---
    private synchronized void logEvent(String action) {
        String ts = LocalDateTime.now().format(timeFormat);
        String line = ts + " - " + action;
        try (FileWriter fw = new FileWriter(logFile, true)) {
            fw.write(line + System.lineSeparator());
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(line);
    }

    private void log(String s) {
        System.out.println(s);
    }

    // --- Helper classes ---
    private static class ArchivedMessage {
        String message;
        long timestamp;
        ArchivedMessage(String msg, long ts) {
            this.message = msg;
            this.timestamp = ts;
        }
    }

    private static class FileTransferStats {
        String fileName;
        long fileSizeBytes;
        int totalPackets;
        int packetsSent = 0;
        int packetsReceived = 0;
        long startTime;
        long endTime;
        long durationMs;
        long bytesTransferred;
        double jitterMs;

        @Override
        public String toString() {
            return String.format("file=%s size=%d bytes packetsSent=%d packetsReceived=%d durationMs=%d jitterMs=%.2f",
                    fileName, fileSizeBytes, packetsSent, packetsReceived, durationMs, jitterMs);
        }

        public String toMultilineString() {
            return String.format("File: %s%nSize (bytes): %d%nPackets Sent: %d%nPackets Received: %d%nDuration (ms): %d%nJitter (ms): %.2f%nSaved To: %s",
                    fileName, fileSizeBytes, packetsSent, packetsReceived, durationMs, jitterMs,
                    "receiver_default_or_latest");
        }
    }

    // Main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            UDPChatApp app = new UDPChatApp();
            // optionally set username here or later via UI
            app.currentUsername = System.getProperty("user.name", "user");
            app.loadLastLoginFor(app.currentUsername);
            app.lastSuccessfulLogin = LocalDateTime.now(); // assume login now (or change if you have real auth)
            app.lastLoginLabel.setText("Last Login: " + app.lastSuccessfulLogin.format(app.timeFormat));
            app.saveLastLogin();
            app.elapsedTimer.start();
            app.logEvent("Application started by " + app.currentUsername);
        });
    }
}
