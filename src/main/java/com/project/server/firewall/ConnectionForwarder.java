package com.project.server.firewall;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionForwarder implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger("ConnectionForwarder");
    private static final int BUFFER_SIZE = 8192;

    private final Socket clientSocket;
    private final Socket serverSocket;
    private final String clientIp;
    private final String destService;

    private volatile boolean running;

    public ConnectionForwarder(Socket clientSocket, Socket serverSocket, String clientIp, String destService) {
        this.clientSocket = clientSocket;
        this.serverSocket = serverSocket;
        this.clientIp = clientIp;
        this.destService = destService;
        this.running = true;
    }

    @Override
    public void run() {
        logger.debug("Iniciando forwarding {} -> {}", clientIp, destService);

        Thread clientToServer = new Thread(() -> forward(clientSocket, serverSocket, "client->server"));
        Thread serverToClient = new Thread(() -> forward(serverSocket, clientSocket, "server->client"));

        clientToServer.start();
        serverToClient.start();

        try {
            clientToServer.join();
            serverToClient.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            close();
        }

        logger.debug("Forwarding encerrado {} -> {}", clientIp, destService);
    }

    private void forward(Socket from, Socket to, String direction) {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytes = 0;

        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();

            while (running && !from.isClosed() && !to.isClosed()) {
                try {
                    bytesRead = in.read(buffer);
                    if (bytesRead == -1) {
                        break;
                    }

                    out.write(buffer, 0, bytesRead);
                    out.flush();
                    totalBytes += bytesRead;

                } catch (SocketTimeoutException e) {
                    continue;
                }
            }

        } catch (SocketException e) {
            if (running) {
                logger.debug("Socket fechado durante forwarding [{}]: {}", direction, e.getMessage());
            }
        } catch (IOException e) {
            if (running) {
                logger.error("Erro no forwarding [{}]: {}", direction, e.getMessage());
            }
        }

        logger.debug("Forwarding [{}] encerrado - {} bytes transferidos", direction, totalBytes);
        running = false;
    }

    public void close() {
        running = false;

        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException e) {
            logger.debug("Erro ao fechar socket do cliente: {}", e.getMessage());
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.debug("Erro ao fechar socket do servidor: {}", e.getMessage());
        }
    }

    public boolean isRunning() {
        return running;
    }
}
