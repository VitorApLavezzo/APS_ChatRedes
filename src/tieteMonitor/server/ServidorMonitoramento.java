package tieteMonitor.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Servidor Mock para testes do Sistema de Monitoramento Ambiental do Rio Tietê
 * Este servidor é apenas para testes e demonstração do funcionamento do chat
 */
public class ServidorMonitoramento {
    private static final int PORTA = 12345;
    private static final List<ClienteHandler> clientes = new ArrayList<>();

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORTA);
            System.out.println("Servidor iniciado na porta " + PORTA);

            while (true) {
                Socket clienteSocket = serverSocket.accept();
                System.out.println("Nova conexão recebida de " + clienteSocket.getInetAddress());

                ClienteHandler clienteHandler = new ClienteHandler(clienteSocket);
                clientes.add(clienteHandler);
                new Thread(clienteHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Erro no servidor: " + e.getMessage());
        }
    }

    /**
     * Envia mensagem para todos os clientes conectados
     */
    public static void enviarParaTodos(String mensagem) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                cliente.enviarMensagem(mensagem);
            }
        }
    }

    /**
     * Envia mensagem para um cliente específico
     */
    public static void enviarParaCliente(String nome, String mensagem) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                if (cliente.getNome().equals(nome)) {
                    cliente.enviarMensagem(mensagem);
                    return;
                }
            }
        }
    }

    /**
     * Obtém lista de nomes de todos os clientes conectados
     */
    public static String getListaClientes() {
        StringBuilder lista = new StringBuilder();
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                if (lista.length() > 0) {
                    lista.append(",");
                }
                lista.append(cliente.getNome());
            }
        }
        return lista.toString();
    }

    /**
     * Remove um cliente da lista de conectados
     */
    public static void removerCliente(ClienteHandler cliente) {
        synchronized (clientes) {
            clientes.remove(cliente);
        }
    }

    /**
     * Classe para lidar com cada cliente conectado
     */
    static class ClienteHandler implements Runnable {
        private Socket socket;
        private PrintWriter saida;
        private BufferedReader entrada;
        private String nome;
        private String local;

        public ClienteHandler(Socket socket) {
            this.socket = socket;
            try {
                this.saida = new PrintWriter(socket.getOutputStream(), true);
                this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                System.err.println("Erro ao configurar cliente: " + e.getMessage());
            }
        }

        public String getNome() {
            return nome;
        }

        public void enviarMensagem(String mensagem) {
            saida.println(mensagem);
        }

        @Override
        public void run() {
            try {
                // Lê nome e local do cliente
                nome = entrada.readLine();
                local = entrada.readLine();
                System.out.println("Cliente conectado: " + nome + " de " + local);

                // Notifica todos sobre o novo cliente
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                enviarParaTodos(nome + " conectou-se a partir de " + local +
                        " às " + sdf.format(new Date()));
                enviarParaTodos("CHAT:CONECTADO:" + nome);

                // Notifica o novo cliente sobre os inspetores conectados
                enviarMensagem("CHAT:LISTA_INSPETORES:" + getListaClientes());

                // Processa mensagens do cliente
                String mensagem;
                while ((mensagem = entrada.readLine()) != null) {
                    System.out.println("Mensagem de " + nome + ": " + mensagem);

                    if (mensagem.equals("SAIR")) {
                        break;
                    } else if (mensagem.startsWith("CHAT:LISTAR_INSPETORES")) {
                        // Envia lista de inspetores
                        enviarMensagem("CHAT:LISTA_INSPETORES:" + getListaClientes());
                    } else if (mensagem.startsWith("CHAT:PARA:")) {
                        // Formato: CHAT:PARA:destinatario:mensagem
                        String[] partes = mensagem.split(":", 4);
                        if (partes.length >= 4) {
                            String destinatario = partes[2];
                            String conteudo = partes[3];

                            if (destinatario.equals("Todos")) {
                                // Envia para todos
                                enviarParaTodos(nome + ": " + conteudo);
                                // Também notifica como mensagem de chat
                                for (ClienteHandler cliente : clientes) {
                                    if (!cliente.getNome().equals(nome)) {
                                        cliente.enviarMensagem("CHAT:MSG_DE:" + nome + ":" + conteudo);
                                    }
                                }
                            } else {
                                // Envia para um destinatário específico
                                enviarParaCliente(destinatario, nome + " (privado): " + conteudo);
                                enviarParaCliente(destinatario, "CHAT:MSG_DE:" + nome + ":" + conteudo);
                            }
                        }
                    } else if (mensagem.startsWith("ALERTA:")) {
                        // Encaminha o alerta para todos
                        enviarParaTodos("ALERTA: " + nome + " de " + local +
                                " reportou: " + mensagem.substring(7));
                    } else {
                        // Mensagem normal, encaminha para todos
                        enviarParaTodos(nome + " (" + local + "): " + mensagem);
                    }
                }

                // Cliente desconectou
                System.out.println("Cliente desconectado: " + nome);
                enviarParaTodos(nome + " desconectou-se");
                enviarParaTodos("CHAT:DESCONECTADO:" + nome);

                // Remove cliente da lista
                removerCliente(this);

                // Fecha a conexão
                socket.close();

            } catch (IOException e) {
                System.err.println("Erro com cliente " + nome + ": " + e.getMessage());

                // Remove cliente da lista em caso de erro
                removerCliente(this);

                // Notifica outros clientes
                if (nome != null) {
                    enviarParaTodos(nome + " desconectou-se (erro de conexão)");
                    enviarParaTodos("CHAT:DESCONECTADO:" + nome);
                }
            }
        }
    }
}