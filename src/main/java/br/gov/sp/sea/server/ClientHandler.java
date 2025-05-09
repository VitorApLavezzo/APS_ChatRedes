package br.gov.sp.sea.server;

import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private ChatServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String username;

    public ClientHandler(Socket socket, ChatServer server) {
        this.clientSocket = socket;
        this.server = server;
        try {
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        } catch (IOException e) {
            System.err.println("Erro ao configurar streams do cliente: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            // Primeira mensagem deve ser o nome do usuário
            username = in.readLine();
            System.out.println(username + " conectado");

            String message;
            while ((message = in.readLine()) != null) {
                String formattedMessage = username + ": " + message;
                System.out.println(formattedMessage);
                server.broadcast(formattedMessage, this);
            }
        } catch (IOException e) {
            System.err.println("Erro na comunicação com o cliente: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    public void sendMessage(String message) {
        out.println(message);
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (clientSocket != null) clientSocket.close();
            server.removeClient(this);
            System.out.println(username + " desconectado");
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexão: " + e.getMessage());
        }
    }
} 