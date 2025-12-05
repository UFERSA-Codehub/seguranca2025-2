package com.project.client;

public interface IClient {
    void start();
    void stop();
    boolean isRunning();
    String getName();
}
