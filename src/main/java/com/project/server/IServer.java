package com.project.server;

public interface IServer {
    void start();

    void stop();

    boolean isRunning();

    void showStatus();

    String getName();

    int getPort();

}
