module com.example.networkpart2 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.logging;
    requires java.desktop;


    opens com.example.networkpart2 to javafx.fxml;
    exports com.example.networkpart2;
}