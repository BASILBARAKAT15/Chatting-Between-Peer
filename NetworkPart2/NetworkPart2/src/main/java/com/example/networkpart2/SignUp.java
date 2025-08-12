package com.example.networkpart2;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;

import javax.swing.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SignUp {
    @FXML
    private TextField password;
    @FXML
    private TextField userName;

    private static final String LOG_FILE = "app_log.txt";

    private void logEvent(String event) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write("[" + timestamp + "] " + event + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTime() {
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return currentTime.format(formatter);
    }

    private void saveUserToFile(String username, String password) {
        String filePath = "accounts.txt";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, true))) {
            writer.write(username + " " + password + " " + getCurrentTime() + " active\n");
            JOptionPane.showMessageDialog(null, "Added Successfully");
            logEvent("New user registered: " + username);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    void onSaveUser(ActionEvent event) {
        String username = userName.getText().trim();
        String password1 = password.getText().trim();

        if (!username.isEmpty() && !password1.isEmpty()) {
            if (!isUsernameUsed(username)) {
                saveUserToFile(username, password1);
                password.setText("");
                userName.setText("");
            } else {
                JOptionPane.showMessageDialog(null, "Username already exists. Please choose a different username.");
            }
        } else {
            JOptionPane.showMessageDialog(null, "Fill all the fields");
        }
    }

    private boolean isUsernameUsed(String username) {
        String filePath = "accounts.txt";
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length > 0 && parts[0].equals(username)) {
                    return true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}