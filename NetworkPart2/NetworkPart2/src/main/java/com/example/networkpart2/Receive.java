package com.example.networkpart2;

public class Receive implements Runnable {
    Client c;
    boolean exit;

    public void stop() {
        exit = true;
    }

    Receive(Client aThis) {
        c = aThis;
    }

    public void start() {
        Thread thread = new Thread(this);
        thread.start();
    }

    @Override
    public void run() {
        while (!exit) {
            c.receive();
        }
    }
}