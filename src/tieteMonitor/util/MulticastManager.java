package tieteMonitor.util;

import java.io.IOException;
import java.net.*;
import java.util.function.Consumer;

/**
 * Gerenciador de comunicação multicast para o sistema de monitoramento do Rio Tietê
 */
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
     * Inicializa o gerenciador multicast
     * 
     * @param messageHandler Handler para processar mensagens recebidas
     * @throws IOException Se ocorrer um erro ao inicializar o socket
     */
    public MulticastManager(Consumer<String> messageHandler) throws IOException {
        this.messageHandler = messageHandler;
        socket = new MulticastSocket(MULTICAST_PORT);
        group = InetAddress.getByName(MULTICAST_ADDRESS);
        socket.joinGroup(group);
    }
    
    /**
     * Inicia a thread de recebimento de mensagens
     */
    public void iniciarRecepcao() {
        if (receiveThread != null && receiveThread.isAlive()) {
            return;
        }
        
        running = true;
        receiveThread = new Thread(this::receiveLoop);
        receiveThread.setDaemon(true);
        receiveThread.start();
    }
    
    /**
     * Loop de recebimento de mensagens
     */
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
     * Envia uma mensagem multicast
     * 
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
    
    /**
     * Para a recepção de mensagens e fecha o socket
     */
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