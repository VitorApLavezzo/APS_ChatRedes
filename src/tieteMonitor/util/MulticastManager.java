package tieteMonitor.util;

import java.io.IOException;
import java.net.*;
import java.util.function.Consumer;

public class MulticastManager {
    private static final String MULTICAST_ADDRESS = "230.0.0.1";
    private static final int MULTICAST_PORT = 4446;
    private static final int BUFFER_SIZE = 1024;
    private MulticastSocket socket;
    private InetAddress group;
    private boolean running;
    private Thread receiveThread;
    private Consumer<String> messageHandler;
    
    /**
     * @param messageHandler
     * @throws IOException
     */
    public MulticastManager(Consumer<String> messageHandler) throws IOException {
        this.messageHandler = messageHandler;
        socket = new MulticastSocket(MULTICAST_PORT);
        group = InetAddress.getByName(MULTICAST_ADDRESS);
        socket.joinGroup(group);
    }
    
    public void iniciarRecepcao() {
        if (receiveThread != null && receiveThread.isAlive()) {
            return;
        }
        running = true;
        receiveThread = new Thread(this::receiveLoop);
        receiveThread.setDaemon(true);
        receiveThread.start();
    }
    
    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];
        
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String message = new String(packet.getData(), 0, packet.getLength());
                if (messageHandler != null) {
                    messageHandler.accept(message);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Erro ao receber mensagem multicast: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * @param message Mensagem a ser enviada
     * @return true se o envio foi bem sucedido, false caso contrário
     */
    public boolean enviarMensagem(String message) {
        try {
            byte[] buffer = message.getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, group, MULTICAST_PORT);
            socket.send(packet);
            return true;
        } catch (IOException e) {
            System.err.println("Erro ao enviar mensagem multicast: " + e.getMessage());
            return false;
        }
    }
    public void fechar() {
        running = false;
        if (socket != null) {
            try {
                socket.leaveGroup(group);
                socket.close();
            } catch (IOException e) {
                System.err.println("Erro ao fechar socket multicast: " + e.getMessage());
            }
        }
    }
}