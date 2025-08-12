package com.example.networkpart2;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.swing.*;
import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

public class Client implements Initializable {
    DatagramSocket socket;
    String userName;
    String local_Ip;
    int local_Port;
    String remotIp;
    int remotPort;
    InetAddress remot_IPAddress;
    byte[] S_buffer;
    DatagramPacket sendpacket;
    byte[] R_buffer;
    DatagramPacket receive_packet;
    boolean connection = false;
    boolean loggedin = false;
    DataInputStream dataFromServer;
    DataOutputStream dataToServer;
    Socket serverSocket;
    Read r;
    Receive channel;
    boolean t = false;
    boolean j = false;

    @FXML private Button login;
    @FXML private Button logout;
    @FXML private ListView<Text> chatField;
    @FXML private Button deleteConv;
    @FXML private Button deleteMsg;
    @FXML private ChoiceBox<String> interfaceBox;
    @FXML private ChoiceBox<String> interfaceBox2;
    @FXML private TextField localIp;
    @FXML private TextField localPort;
    @FXML private ListView<String> onlineUsers;
    @FXML private TextField password;
    @FXML private TextField remoteIp;
    @FXML private TextField remotePort;
    @FXML private TextArea sendMessageField;
    @FXML private TextField serverIp;
    @FXML private TextField serverPort;
    @FXML private Label status;
    @FXML private TextField username;
    @FXML private Label lastLoginLbl;
    @FXML private Label elapsedTimeLbl;
    @FXML private Button exportChatBtn;
    @FXML private Button sendFileBtn;
    @FXML private TextField receiveDir;

    private String previousStatus = "Active";
    private long loginTime;
    private Timeline elapsedTimeline;
    private static final String LOG_FILE = "app_log.txt";
    private static final int PACKET_SIZE = 1024;
    private List<Long> packetDelays = new ArrayList<>();
    private long fileStartTime;
    private long fileEndTime;
    private int packetCount = 0;
    private long fileSize = 0;
    private ByteArrayOutputStream fileBuffer;
    private String receivingFileName;
    private int expectedPackets;
    private int receivedPackets = 0;
    private long receiveStartTime;
    private HashMap<Integer, Color> userColorMap = new HashMap<>();

    private void logEvent(String event) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write("[" + timestamp + "] " + event + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        interfaceBox.getItems().addAll("WIFI", "Ethernet", "Loopback pseudo-Interface");
        interfaceBox.setValue("WIFI");
        interfaceBox2.getItems().addAll("Active", "Busy", "Away");
        interfaceBox2.setValue("Active");

        interfaceBox2.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            previousStatus = newValue;
            updateUserStatus(newValue);
            logEvent("Status update for " + userName + ": " + newValue);
        });

        try {
            localIp.setText("192.168.193.147");
            if (receiveDir != null) {
                receiveDir.setText("C:\\");
            } else {
                logEvent("Warning: receiveDir TextField is not initialized in FXML");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendMessageField.setStyle("-fx-text-fill: black; -fx-font-size: 14;");
        startInactivityTimer();
        registerActivityListeners();
    }

    private void updateUserStatus(String newStatus) {
        Platform.runLater(() -> {
            ObservableList<String> users = onlineUsers.getItems();
            boolean statusUpdated = false;
            for (int i = 0; i < users.size(); i++) {
                String userDetail = users.get(i);
                String[] tokens = userDetail.split(",");
                if (tokens[0].equalsIgnoreCase(username.getText())) {
                    String updatedUserDetail = tokens[0] + "," + tokens[1] + "," + tokens[2] + "," + newStatus;
                    users.set(i, updatedUserDetail);
                    statusUpdated = true;
                    break;
                }
            }
            if (statusUpdated) {
                try {
                    File accountsFile = new File("accounts.txt");
                    BufferedReader reader = new BufferedReader(new FileReader(accountsFile));
                    StringBuilder fileContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(" ");
                        if (parts.length >= 4 && parts[0].equalsIgnoreCase(username.getText())) {
                            line = parts[0] + " " + parts[1] + " " + getCurrentTime() + " " + newStatus;
                        }
                        fileContent.append(line).append("\n");
                    }
                    reader.close();
                    BufferedWriter writer = new BufferedWriter(new FileWriter(accountsFile));
                    writer.write(fileContent.toString());
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                sendToServer("STATUS_UPDATE," + username.getText() + "," + newStatus);
                interfaceBox2.setValue(newStatus);
            }
        });
    }

    @FXML
    void onDeleteConv(ActionEvent event) {
        chatField.getItems().clear();
        logEvent("Conversation deleted by " + userName);
    }

    @FXML
    void onDeleteMsg(ActionEvent event) {
        Text selectedMessage = chatField.getSelectionModel().getSelectedItem();
        if (selectedMessage != null) {
            logEvent("Message deleted: " + selectedMessage.getText().trim());
            chatField.getItems().remove(selectedMessage);
        }
    }

    private String getLastLoginTime(String username) {
        try (BufferedReader reader = new BufferedReader(new FileReader("accounts.txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 3 && parts[0].equalsIgnoreCase(username)) {
                    return parts[2] + " " + parts[3];
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void updateLastLoginTime(String username) {
        try {
            File accountsFile = new File("accounts.txt");
            BufferedReader reader = new BufferedReader(new FileReader(accountsFile));
            StringBuilder fileContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 4 && parts[0].equalsIgnoreCase(username)) {
                    line = parts[0] + " " + parts[1] + " " + getCurrentTime() + " " + parts[4];
                }
                fileContent.append(line).append("\n");
            }
            reader.close();
            BufferedWriter writer = new BufferedWriter(new FileWriter(accountsFile));
            writer.write(fileContent.toString());
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTime() {
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return currentTime.format(formatter);
    }

    @FXML
    void onLogin(ActionEvent event) {
        if (username.getText().isEmpty() || serverIp.getText().isEmpty() || localIp.getText().isEmpty()
                || localPort.getText().isEmpty() || serverPort.getText().isEmpty()) {
            JOptionPane.showMessageDialog(null, "You should enter the following (TCP Port&IP, local Port&IP, and your name)");
            return;
        }

        if (loggedin) {
            JOptionPane.showMessageDialog(null, "You are already logged in");
            return;
        }

        String usernameInput = username.getText();
        String serverIpAddress = serverIp.getText();
        String localIpAddress = localIp.getText();
        local_Port = Integer.parseInt(localPort.getText().trim());
        int serverTcpPort = Integer.parseInt(serverPort.getText().trim());
        int localPortNumber = Integer.parseInt(localPort.getText().trim());
        connection = true;

        try {
            socket = new DatagramSocket(localPortNumber);
            File accountsFile = new File("accounts.txt");
            BufferedReader accountsReader = new BufferedReader(new FileReader(accountsFile));
            String line;
            boolean found = false;
            while ((line = accountsReader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length >= 2 && parts[0].equalsIgnoreCase(usernameInput) && parts[1].equals(password.getText())) {
                    found = true;
                    serverSocket = new Socket(InetAddress.getByName(serverIpAddress), serverTcpPort, InetAddress.getByName(localIpAddress), localPortNumber);
                    dataFromServer = new DataInputStream(serverSocket.getInputStream());
                    dataToServer = new DataOutputStream(serverSocket.getOutputStream());
                    dataToServer.writeUTF(usernameInput);

                    String response = dataFromServer.readUTF();
                    if (response.equals("founded")) {
                        JOptionPane.showMessageDialog(null, "You are already logged in!");
                        return;
                    } else if (response.equals("accept")) {
                        r = new Read(usernameInput);
                        r.start();
                    }

                    channel = new Receive(this);
                    channel.start();
                    j = true;
                    t = true;

                    JOptionPane.showMessageDialog(null, "You are logged in successfully");
                    loggedin = true;
                    userName = usernameInput;
                    logEvent("Login: " + usernameInput);
                    loginTime = System.currentTimeMillis();
                    startElapsedTimer();
                    break;
                }
            }
            accountsReader.close();

            if (!found) {
                JOptionPane.showMessageDialog(null, "Invalid login information. Please check username and password.");
            }
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Error occurred while logging in: " + ex.getMessage());
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        }

        String lastLoginTime = getLastLoginTime(usernameInput);
        if (lastLoginTime != null) {
            lastLoginLbl.setText(lastLoginTime);
        }
        updateLastLoginTime(usernameInput);
    }

    public void onLogout(ActionEvent event) {
        if (loggedin) {
            JOptionPane.showMessageDialog(null, "You are logged out successfully");
            loggedin = false;
            t = false;
            j = false;
            password.setText("");
            username.setText("");
            localPort.setText("");
            logEvent("Logout: " + userName);
            if (elapsedTimeline != null) {
                elapsedTimeline.stop();
            }
            elapsedTimeLbl.setText("");
            try {
                if (serverSocket != null) {
                    sendToServer("logout");
                    serverSocket.close();
                }
                if (socket != null) {
                    socket.close();
                }
                if (dataFromServer != null) {
                    dataFromServer.close();
                }
                if (dataToServer != null) {
                    dataToServer.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            }
            Platform.runLater(() -> onlineUsers.getItems().clear());
        } else {
            JOptionPane.showMessageDialog(null, "You are already logged out");
        }
    }

    @FXML
    void onSendButton(ActionEvent event) {
        try {
            if (!connection) {
                JOptionPane.showMessageDialog(null, "You can't send, please Login first");
            } else if (remoteIp.getText().isEmpty() || remotePort.getText().isEmpty()) {
                JOptionPane.showMessageDialog(null, "You should select a user from the online user list");
            } else if (sendMessageField.getText().isEmpty() || sendMessageField.getText().equals("enter text here")) {
                JOptionPane.showMessageDialog(null, "You can't send empty message");
            } else {
                userName = username.getText();
                remotIp = remoteIp.getText();
                remotPort = Integer.parseInt(remotePort.getText());
                remot_IPAddress = InetAddress.getByName(remotIp);
                String msg = sendMessageField.getText();
                sendMessageField.setText("");
                LocalDateTime now = LocalDateTime.now();

                String s1 = "[" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss a")) + "] ME: " + msg + " From " + local_Port + "\n";
                Text text = new Text(s1);
                text.setFill(Color.BLUE);
                chatField.getItems().add(text);
                logEvent("Message sent: " + msg + " to " + remotPort);
                msg = userName + ": " + msg;
                S_buffer = msg.getBytes();
                sendpacket = new DatagramPacket(S_buffer, S_buffer.length, remot_IPAddress, remotPort);
                socket.send(sendpacket);
                String s = "Sent To Ip =" + remoteIp.getText() + " ,Port = " + remotePort.getText();
                status.setText(s);
            }
        } catch (UnknownHostException ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException e) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    @FXML
    private void onSendAllMsg(ActionEvent event) {
        try {
            if (!connection) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "You can't send, please Login first");
                alert.showAndWait();
            } else if (sendMessageField.getText().isEmpty() || sendMessageField.getText().equals("enter text here")) {
                Alert alert = new Alert(Alert.AlertType.WARNING, "You can't send empty message");
                alert.showAndWait();
            } else {
                ObservableList<String> users = onlineUsers.getItems();
                for (String userDetail : users) {
                    String[] tokens = userDetail.split(",");
                    if (tokens.length >= 3 && !tokens[2].equals(localPort.getText())) {
                        int remotePort = Integer.parseInt(tokens[2].trim());
                        InetAddress remoteIPAddress = InetAddress.getByName(tokens[1].trim());
                        String message = sendMessageField.getText();
                        LocalDateTime now = LocalDateTime.now();
                        String formattedMessage = String.format("[%s] ME: %s From %s to %s\n",
                                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss a")),
                                message, localPort.getText(), tokens[2]);
                        Platform.runLater(() -> {
                            Text text = new Text(formattedMessage);
                            text.setFill(Color.RED);
                            chatField.getItems().add(text);
                        });
                        logEvent("Message sent: " + message + " to all");
                        message = userName + ": " + message;
                        byte[] buffer = message.getBytes();
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, remoteIPAddress, remotePort);
                        socket.send(packet);
                    }
                }
                sendMessageField.setText("");
            }
        } catch (Exception ex) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, ex);
            Alert alert = new Alert(Alert.AlertType.ERROR, "An error occurred: " + ex.getMessage());
            alert.showAndWait();
        }
    }

    private void sendToServer(String message) {
        try {
            if (serverSocket != null && serverSocket.isConnected()) {
                dataToServer.writeUTF(message);
            } else {
                JOptionPane.showMessageDialog(null, "Not connected to the server.");
            }
        } catch (IOException e) {
            Logger.getLogger(Client.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    public void handleItemClick(MouseEvent mouseEvent) {
        String str = onlineUsers.getSelectionModel().getSelectedItem();
        if (str != null) {
            String[] arr = str.split(",");
            remoteIp.setText(arr[1]);
            remotePort.setText(arr[2]);
        }
    }

    class Read extends Thread {
        String userName;

        public Read(String userName) {
            this.userName = userName;
        }

        @Override
        public void run() {
            while (j) {
                try {
                    String inputData = dataFromServer.readUTF();
                    if (inputData.equals("logout")) {
                        break;
                    }
                    if (inputData.startsWith("STATUS_UPDATE")) {
                        handleIncomingMessage(inputData);
                    }
                    if (inputData.contains("add to list")) {
                        processData(inputData);
                    }
                } catch (IOException ex) {
                    // Handle exception
                }
            }
        }

        private void handleIncomingMessage(String message) {
            String[] tokens = message.split(",");
            String command = tokens[0];
            if (command.equals("STATUS_UPDATE")) {
                String username = tokens[1];
                String newStatus = tokens[2];
                updateUserStatusInUI(username, newStatus);
            }
        }

        private void updateUserStatusInUI(String username, String newStatus) {
            Platform.runLater(() -> {
                ObservableList<String> users = onlineUsers.getItems();
                for (int i = 0; i < users.size(); i++) {
                    String userDetail = users.get(i);
                    String[] tokens = userDetail.split(",");
                    if (tokens[0].equalsIgnoreCase(username)) {
                        String updatedUserDetail = tokens[0] + "," + tokens[1] + "," + tokens[2] + "," + newStatus;
                        users.set(i, updatedUserDetail);
                    }
                }
            });
        }

        private void processData(String inputData) {
            final String finalInputData = inputData.substring(11);
            Platform.runLater(() -> {
                onlineUsers.getItems().clear();
                StringTokenizer st = new StringTokenizer(finalInputData, "&?");
                while (st.hasMoreTokens()) {
                    String line = st.nextToken();
                    String[] tokens = line.split(",");
                    String element = tokens[0] + "," + tokens[2] + "," + tokens[1] + "," + tokens[3];
                    onlineUsers.getItems().add(element);
                }
            });
        }
    }

    void receive() {
        try {
            if (t) {
                byte[] buffer = new byte[PACKET_SIZE + 100];
                receive_packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(receive_packet);
                String msg = new String(receive_packet.getData(), 0, receive_packet.getLength());
                if (msg.equals("logout")) {
                    return;
                }
                InetAddress S_IPAddress = receive_packet.getAddress();
                int Sport = receive_packet.getPort();

                if (msg.startsWith("FILE_START")) {
                    handleFileStart(msg);
                    receiveStartTime = System.currentTimeMillis();
                    receivedPackets = 0;
                    packetDelays.clear();
                    return;
                } else if (msg.startsWith("FILE_PACKET")) {
                    handleFilePacket(msg, receive_packet.getData(), receive_packet.getOffset(), receive_packet.getLength());
                    return;
                } else if (msg.startsWith("FILE_END")) {
                    handleFileEnd();
                    return;
                }

                LocalDateTime now = LocalDateTime.now();
                String s1 = "[" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss a")) + "]" + msg + " From " + Sport + "\n";
                Platform.runLater(() -> {
                    Text text = new Text(s1);
                    text.setFill(getUserColor(Sport));
                    chatField.getItems().add(text);
                    String s = S_IPAddress.getHostAddress();
                    status.setText("Received From IP= " + s + ", port: " + Sport);
                });
                logEvent("Message received: " + msg + " from " + Sport);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void handleFileStart(String msg) {
        String[] parts = msg.split(" ", 4);
        receivingFileName = parts[1];
        fileSize = Long.parseLong(parts[2]);
        expectedPackets = Integer.parseInt(parts[3]);
        fileBuffer = new ByteArrayOutputStream();
        Platform.runLater(() -> status.setText("Receiving file: " + receivingFileName));
    }

    private void handleFilePacket(String msg, byte[] data, int offset, int length) {
        String[] header = msg.split(" ", 3);
        int seq = Integer.parseInt(header[1]);
        long sendTime = Long.parseLong(header[2]);
        long receiveTime = System.currentTimeMillis();
        long delay = receiveTime - sendTime;
        packetDelays.add(delay);

        int headerLength = ("FILE_PACKET " + seq + " " + sendTime + " ").length();
        fileBuffer.write(data, offset + headerLength, length - headerLength);
        receivedPackets++;
    }

    private void handleFileEnd() {
        fileEndTime = System.currentTimeMillis();
        try {
            String dir = receiveDir != null ? receiveDir.getText() : "C:\\";
            if (!dir.endsWith("\\")) dir += "\\";
            File dirFile = new File(dir);
            if (!dirFile.exists()) dirFile.mkdirs();
            FileOutputStream fos = new FileOutputStream(dir + receivingFileName);
            fileBuffer.writeTo(fos);
            fos.close();
            fileBuffer.close();

            long e2eDelay = fileEndTime - receiveStartTime;
            double avgDelay = packetDelays.stream().mapToLong(val -> val).average().orElse(0.0);
            double jitter = calculateJitter(packetDelays, avgDelay);

            Platform.runLater(() -> {
                status.setText("File received: " + receivingFileName + ", Packets: " + receivedPackets + ", Size: " + fileSize + " bytes");
                JOptionPane.showMessageDialog(null, "File Stats:\nPackets: " + receivedPackets + "\nSize: " + fileSize + " bytes\nE2E Delay: " + e2eDelay + " ms\nJitter: " + jitter + " ms");
            });
            logEvent("File received: " + receivingFileName + ", Packets: " + receivedPackets + ", Size: " + fileSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double calculateJitter(List<Long> delays, double avgDelay) {
        double sumSquares = delays.stream().mapToDouble(d -> Math.pow(d - avgDelay, 2)).sum();
        return Math.sqrt(sumSquares / (delays.size() > 0 ? delays.size() : 1));
    }

    private Color getUserColor(int username) {
        if (!userColorMap.containsKey(username)) {
            Random random = new Random();
            Color randomColor = Color.rgb(random.nextInt(150), random.nextInt(150), random.nextInt(150));
            userColorMap.put(username, randomColor);
        }
        return userColorMap.get(username);
    }

    private void startInactivityTimer() {
        resetInactivityTimer();
    }

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> inactivityTask;

    private void resetInactivityTimer() {
        if (inactivityTask != null) {
            inactivityTask.cancel(true);
        }
        if (interfaceBox2.getValue().equals("Away")) {
            updateUserStatus("Active");
        } else {
            previousStatus = interfaceBox2.getValue();
        }
        inactivityTask = scheduler.schedule(() -> {
            Platform.runLater(() -> {
                previousStatus = interfaceBox2.getValue();
                updateUserStatus("Away");
            });
        }, 30, TimeUnit.SECONDS);
    }

    private void registerActivityListeners() {
        Platform.runLater(() -> {
            chatField.getScene().setOnMouseMoved(event -> {
                updateUserStatus(previousStatus);
                resetInactivityTimer();
            });
            chatField.getScene().setOnKeyPressed(event -> {
                updateUserStatus(previousStatus);
                resetInactivityTimer();
            });
        });
    }

    private void startElapsedTimer() {
        elapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            long elapsed = System.currentTimeMillis() - loginTime;
            long hours = elapsed / 3600000;
            long minutes = (elapsed % 3600000) / 60000;
            long seconds = (elapsed % 60000) / 1000;
            elapsedTimeLbl.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
        }));
        elapsedTimeline.setCycleCount(Animation.INDEFINITE);
        elapsedTimeline.play();
    }

    @FXML
    private void onExportChat(ActionEvent event) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(userName + "_chat_history.txt"))) {
            for (Text text : chatField.getItems()) {
                writer.write(text.getText());
                writer.newLine();
            }
            JOptionPane.showMessageDialog(null, "Chat history exported successfully");
            logEvent("Chat history exported by " + userName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onSendFile(ActionEvent event) {
        if (!connection || remoteIp.getText().isEmpty() || remotePort.getText().isEmpty()) {
            JOptionPane.showMessageDialog(null, "Select a user and login first");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(new Stage());
        if (file == null) return;

        try {
            remot_IPAddress = InetAddress.getByName(remoteIp.getText());
            remotPort = Integer.parseInt(remotePort.getText());
            fileSize = file.length();
            packetCount = (int) Math.ceil((double) fileSize / PACKET_SIZE);

            String startMsg = "FILE_START " + file.getName() + " " + fileSize + " " + packetCount;
            sendUdpMessage(startMsg);
            fileStartTime = System.currentTimeMillis();
            packetDelays.clear();

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[PACKET_SIZE];
            int seq = 0;
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                long sendTime = System.currentTimeMillis();
                String header = "FILE_PACKET " + seq + " " + sendTime + " ";
                byte[] headerBytes = header.getBytes();
                byte[] packetData = new byte[headerBytes.length + bytesRead];
                System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
                System.arraycopy(buffer, 0, packetData, headerBytes.length, bytesRead);
                DatagramPacket packet = new DatagramPacket(packetData, packetData.length, remot_IPAddress, remotPort);
                socket.send(packet);
                seq++;
            }
            fis.close();

            sendUdpMessage("FILE_END");
            fileEndTime = System.currentTimeMillis();

            long e2eDelay = fileEndTime - fileStartTime;
            double avgDelay = packetDelays.stream().mapToLong(val -> val).average().orElse(0.0);
            double jitter = calculateJitter(packetDelays, avgDelay);

            Platform.runLater(() -> {
                JOptionPane.showMessageDialog(null, "File sent. Stats:\nPackets: " + packetCount + "\nSize: " + fileSize + " bytes\nE2E Delay: " + e2eDelay + " ms\nJitter: " + jitter + " ms");
            });
            logEvent("File sent: " + file.getName() + ", Packets: " + packetCount + ", Size: " + fileSize);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendUdpMessage(String msg) throws IOException {
        byte[] bytes = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length, remot_IPAddress, remotPort);
        socket.send(packet);
    }
}