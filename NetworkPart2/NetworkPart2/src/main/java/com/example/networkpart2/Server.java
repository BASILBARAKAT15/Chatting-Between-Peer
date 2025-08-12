
        package com.example.networkpart2;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import javax.swing.*;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Server implements Initializable {
    @FXML
    private Label status;
    @FXML
    private ChoiceBox<String> interfaceBox;
    @FXML
    private ListView<String> onlineUsers;
    @FXML
    private TextFlow serverMessages;
    @FXML
    private TextField Port; // Kept for compatibility, but not used in onStart

    private ServerSocket socket;
    private HashMap<String, Socket> clientsHash = new HashMap<>();
    private static final String LOG_FILE = "app_log.txt";

    private void logEvent(String event) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            // Use EEST (Eastern European Summer Time) for logging
            String timestamp = LocalDateTime.now(ZoneId.of("Europe/Helsinki"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
            String logMessage = "[" + timestamp + "] " + event; // e.g., [2025-08-12 10:05:00 EEST] ...
            writer.write(logMessage + "\n");
            System.out.println(logMessage); // Debug to console
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    public void onStart() {
        final String SERVER_ADDRESS = "192.168.193.147";
        final int SERVER_PORT = 5000;
        try {
            socket = new ServerSocket(SERVER_PORT, 50, InetAddress.getByName(SERVER_ADDRESS));
            String message = "Start Listening at " + SERVER_ADDRESS + ":" + SERVER_PORT + "\n";
            Platform.runLater(() -> {
                if (serverMessages != null) {
                    Text text = new Text(message);
                    text.setFill(Color.BLUE);
                    serverMessages.getChildren().add(text);
                } else {
                    logEvent("Error: serverMessages TextFlow is null");
                }
                if (status != null) {
                    status.setText("Address: " + SERVER_ADDRESS + ", port: " + SERVER_PORT);
                } else {
                    logEvent("Error: status Label is null");
                }
            });
            new ClientAccept(socket).start();
            logEvent("Server started on " + SERVER_ADDRESS + ":" + SERVER_PORT);
        } catch (IOException ex) {
            String errorMessage = "The port " + SERVER_PORT + " on " + SERVER_ADDRESS + " is already in use or inaccessible.";
            Platform.runLater(() -> {
                if (JOptionPane.getRootFrame() != null) {
                    JOptionPane.showMessageDialog(null, errorMessage);
                }
            });
            logEvent("Failed to start server on " + SERVER_ADDRESS + ":" + SERVER_PORT + ": " + ex.getMessage());
        }
    }

    public void onAddUser(ActionEvent actionEvent) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("SignUp.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("New User");
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            logEvent("Error opening SignUp window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (interfaceBox != null) {
            interfaceBox.getItems().addAll("WIFI", "Ethernet", "Loopback pseudo-Interface");
            interfaceBox.setValue("Loopback pseudo-Interface");
        } else {
            logEvent("Error: interfaceBox ChoiceBox is null");
        }
    }

    private class ClientAccept extends Thread {
        private ServerSocket socket;

        public ClientAccept(ServerSocket socket) {
            this.socket = socket;
        }

        public void run() {
            while (true) {
                try {
                    Socket clientSocket = socket.accept();
                    String username = new DataInputStream(clientSocket.getInputStream()).readUTF();
                    DataOutputStream dataOutOfClient = new DataOutputStream(clientSocket.getOutputStream());
                    if (clientsHash.containsKey(username)) {
                        dataOutOfClient.writeUTF("founded");
                    } else {
                        clientsHash.put(username, clientSocket);
                        addTextToArea(username, true);
                        dataOutOfClient.writeUTF("accept");
                        new endToEndList().start();
                        new ReadMessage(clientSocket, username).start();
                        logEvent("Login: " + username);
                    }
                } catch (IOException ex) {
                    logEvent("Error in ClientAccept: " + ex.getMessage());
                }
            }
        }
    }

    class ReadMessage extends Thread {
        Socket s;
        String ID;

        ReadMessage(Socket s, String username) {
            this.s = s;
            this.ID = username;
        }

        public void run() {
            while (!clientsHash.isEmpty() && clientsHash.containsKey(ID)) {
                try {
                    String in = new DataInputStream(s.getInputStream()).readUTF();
                    if (in.contains("logout")) {
                        new DataOutputStream(clientsHash.get(ID).getOutputStream()).writeUTF("logout");
                        clientsHash.remove(ID);
                        addTextToArea(ID, false);
                        new endToEndList().start();
                        logEvent("Logout: " + ID);
                    } else if (in.contains("STATUS_UPDATE")) {
                        new endToEndList().start();
                        logEvent("Status update received from " + ID + ": " + in);
                    }
                } catch (IOException ex) {
                    clientsHash.remove(ID);
                    try {
                        addTextToArea(ID, false);
                    } catch (Exception ex1) {
                        logEvent("Error in addTextToArea for logout: " + ex1.getMessage());
                    }
                    new endToEndList().start();
                    logEvent("Client disconnected: " + ID);
                }
            }
        }
    }

    private class endToEndList extends Thread {
        public void run() {
            try {
                String s = "";
                Set<String> k = clientsHash.keySet();
                Iterator<String> itr = k.iterator();
                Platform.runLater(() -> {
                    if (onlineUsers != null) {
                        onlineUsers.getItems().clear();
                    } else {
                        logEvent("Error: onlineUsers ListView is null");
                    }
                });

                while (itr.hasNext()) {
                    String key = itr.next();
                    String status = "";
                    try (BufferedReader reader = new BufferedReader(new FileReader("accounts.txt"))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String[] parts = line.split(" ");
                            if (parts.length >= 4 && parts[0].equalsIgnoreCase(key)) {
                                status = parts[4];
                                break;
                            }
                        }
                    } catch (IOException e) {
                        logEvent("Error reading accounts.txt: " + e.getMessage());
                        e.printStackTrace();
                    }

                    s += key + "," + String.valueOf(clientsHash.get(key).getPort()) + ","
                            + clientsHash.get(key).getInetAddress().getHostAddress() + "," + status + "&?";
                    String ele = clientsHash.get(key).getInetAddress().getHostAddress() + ","
                            + String.valueOf(clientsHash.get(key).getPort());
                    Platform.runLater(() -> {
                        if (onlineUsers != null) {
                            onlineUsers.getItems().add(ele);
                        }
                    });
                }
                if (s.length() != 0) {
                    s = s.substring(0, s.length() - 2);
                }
                itr = k.iterator();
                while (itr.hasNext()) {
                    String key = itr.next();
                    try {
                        new DataOutputStream(clientsHash.get(key).getOutputStream()).writeUTF("add to list" + s);
                    } catch (IOException ex) {
                        clientsHash.remove(key);
                        addTextToArea(key, false);
                        logEvent("Error sending list to client " + key + ": " + ex.getMessage());
                    }
                }
            } catch (Exception ex) {
                logEvent("Error in endToEndList: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    public void addTextToArea(String username, boolean color) {
        Platform.runLater(() -> {
            if (serverMessages != null) {
                String s1 = username + (color ? " login" : " logout") + "\n";
                Text text = new Text(s1);
                text.setFill(color ? Color.RED : Color.BLUE);
                serverMessages.getChildren().add(text);
            } else {
                logEvent("Error: serverMessages TextFlow is null in addTextToArea");
            }
        });
    }




}
