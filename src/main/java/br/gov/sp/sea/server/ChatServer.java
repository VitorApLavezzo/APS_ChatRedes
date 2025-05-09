package br.gov.sp.sea.server;

import java.io.*;
import java.net.*;
import java.util.*;

public class ChatServer {
    private static final int PORT = 5000;
    private Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());
    private ServerSocket serverSocket;

    public ChatServer() {
        try {
            serverSocket = new ServerSocket(PORT, 50, InetAddress.getByName("0.0.0.0"));
            System.out.println("Servidor iniciado na porta " + PORT);
            System.out.println("Endereço IP local: " + InetAddress.getLocalHost().getHostAddress());
            
            // Obtém o IP público
            try {
                URL url = new URL("https://api.ipify.org");
                BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
                String publicIP = reader.readLine();
                System.out.println("Endereço IP público: " + publicIP);
                reader.close();
            } catch (Exception e) {
                System.out.println("Não foi possível obter o IP público automaticamente");
            }
            
        } catch (IOException e) {
            System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
        }
    }

    public void start() {
        try {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nova conexão estabelecida: " + clientSocket.getInetAddress());
                
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Erro ao aceitar conexão: " + e.getMessage());
        }
    }

    public void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(message);
            }
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
    }

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();
    }
} 