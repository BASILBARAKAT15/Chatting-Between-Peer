package com.example.networkpart2;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {

    @Override
    public void start(Stage stage) throws IOException {


        Stage stage2 = new Stage();
        FXMLLoader fxmlLoader1 = new FXMLLoader(HelloApplication.class.getResource("Server.fxml"));
        Scene scene1 = new Scene(fxmlLoader1.load(), 800, 630);
        stage2.setTitle("Server");
        stage2.setScene(scene1);
        stage2.show();

        Stage stage3 = new Stage();
        FXMLLoader fxmlLoader3 = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene3 = new Scene(fxmlLoader3.load(), 1100, 800);
        stage3.setTitle("Client 1");
        stage3.setScene(scene3);
        stage3.show();

        /*
         Stage stage4 = new Stage();
        FXMLLoader fxmlLoader4 = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene4 = new Scene(fxmlLoader4.load(), 1100, 800);
        stage4.setTitle("Client 1");
        stage4.setScene(scene4);
        stage4.show();

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1100, 800);
        stage.setTitle("Client 2");
        stage.setScene(scene);
        stage.show();
         */


    }

    public static void main(String[] args) {
        launch();
    }
}