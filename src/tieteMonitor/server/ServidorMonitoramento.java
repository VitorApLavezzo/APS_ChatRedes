package tieteMonitor.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import javax.swing.*;
import java.awt.*;
import java.util.List;
import tieteMonitor.util.TransferenciaArquivos;

/**
 * Servidor para Sistema de Monitoramento Ambiental do Rio Tietê
 * Permite comunicação entre inspetores e a central da Secretaria de Meio Ambiente
 */
public class ServidorMonitoramento {
    private static final int PORTA = 12345;
    private ServerSocket serverSocket;
    private List<ClienteHandler> clientes = new ArrayList<>();
    private JTextArea logArea;
    private JFrame frame;
    private Map<String, String> locaisMonitorados = new HashMap<>();

    public static void main(String[] args) {
        new ServidorMonitoramento().iniciar();
    }

    public ServidorMonitoramento() {
        // Inicializa locais de monitoramento
        inicializarLocais();
        // Configura a interface gráfica
        configurarInterface();
    }

    private void inicializarLocais() {
        locaisMonitorados.put("salesopolis", "Nascente - Salesópolis");
        locaisMonitorados.put("mogi", "Mogi das Cruzes");
        locaisMonitorados.put("suzano", "Suzano");
        locaisMonitorados.put("poa", "Poá");
        locaisMonitorados.put("itaquaquecetuba", "Itaquaquecetuba");
        locaisMonitorados.put("guarulhos", "Guarulhos");
        locaisMonitorados.put("saopaulo", "São Paulo - Capital");
    }

    private void configurarInterface() {
        frame = new JFrame("Servidor de Monitoramento - Rio Tietê");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);

        JPanel painelInferior = new JPanel();
        JButton btnEnviarAlerta = new JButton("Enviar Alerta Geral");
        btnEnviarAlerta.addActionListener(e -> {
            String mensagem = JOptionPane.showInputDialog(frame, "Digite a mensagem de alerta:");
            if (mensagem != null && !mensagem.trim().isEmpty()) {
                enviarParaTodosClientes("ALERTA:" + mensagem);
                registrarLog("ALERTA ENVIADO: " + mensagem);
            }
        });
        painelInferior.add(btnEnviarAlerta);

        JButton btnListarClientes = new JButton("Listar Inspetores Conectados");
        btnListarClientes.addActionListener(e -> {
            StringBuilder sb = new StringBuilder("Inspetores conectados:\n");
            synchronized (clientes) { // Sincroniza a iteração
                if (clientes.isEmpty()) {
                    sb.append("Nenhum inspetor conectado no momento.");
                } else {
                    for (ClienteHandler cliente : clientes) {
                        // Verificar se nomeInspetor e localMonitorado não são nulos antes de usar
                        String nome = cliente.getNomeInspetor() != null ? cliente.getNomeInspetor() : "(null)";
                        String local = cliente.getLocalMonitorado() != null ? cliente.getLocalMonitorado() : "(null)";
                        sb.append("- ").append(nome)
                                .append(" (").append(local).append(")\n");
                    }
                }
            }
            JOptionPane.showMessageDialog(frame, sb.toString());
        });
        painelInferior.add(btnListarClientes);

        frame.getContentPane().add(scrollPane, BorderLayout.CENTER);
        frame.getContentPane().add(painelInferior, BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    public void iniciar() {
        try {
            serverSocket = new ServerSocket(PORTA);
            registrarLog("Servidor iniciado na porta " + PORTA);

            while (true) {
                Socket clienteSocket = serverSocket.accept();
                registrarLog("Nova conexão de: " + clienteSocket.getInetAddress().getHostAddress());

                // Sempre criar um ClienteHandler para a nova conexão
                ClienteHandler clienteHandler = new ClienteHandler(clienteSocket);
                new Thread(clienteHandler).start();

            }
        } catch (IOException e) {
            registrarLog("Erro no servidor: " + e.getMessage());
        }
    }

    public synchronized void registrarLog(String mensagem) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        String timestamp = sdf.format(new Date());
        String logComTimestamp = timestamp + " - " + mensagem;

        SwingUtilities.invokeLater(() -> {
            // Estilo para mensagens de alerta no log do servidor
            if (logComTimestamp.contains("ALERTA ENVIADO:") || logComTimestamp.contains("ALERTA de")) {
                 logArea.append(logComTimestamp + " 🚨\n"); // Adiciona emoji para destacar
            } else if (logComTimestamp.contains("RELATÓRIO de")){
                 logArea.append(logComTimestamp + " 📝\n"); // Adiciona emoji para destacar
            }else if (logComTimestamp.contains("Arquivo recebido de:")){
                 logArea.append(logComTimestamp + " 📁\n"); // Adiciona emoji para destacar
            }
            else {
                logArea.append(logComTimestamp + "\n");
            }
            // Auto-scroll para a última linha
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public synchronized void removerCliente(ClienteHandler cliente) {
        clientes.remove(cliente);
        // Verificar se o nome do inspetor foi lido antes de usar
        String nomeInspetorDesconectado = cliente.getNomeInspetor() != null ? cliente.getNomeInspetor() : cliente.socket.getInetAddress().getHostAddress();
        String localMonitoradoDesconectado = cliente.getLocalMonitorado() != null ? cliente.getLocalMonitorado() : "Desconhecido";

        registrarLog("Inspetor desconectado: " + nomeInspetorDesconectado +
                " - Local: " + localMonitoradoDesconectado);

        // Notificar todos os clientes sobre o inspetor desconectado
        // Só notificar se o nome do inspetor foi lido (evitar notificações parciais)
        if (cliente.getNomeInspetor() != null) {
             notificarClientesInspetorDesconectado(cliente.getNomeInspetor());
        }
    }

    public synchronized void enviarParaTodosClientes(String mensagem) {
        for (ClienteHandler cliente : clientes) {
            cliente.enviarMensagem(mensagem);
        }
    }

    // Método para notificar clientes sobre novo inspetor (Manter este método, chamado por adicionarClienteChat)
    private void notificarClientesNovoInspetor(String nomeNovoInspetor) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                // Enviar a notificação para todos os clientes, incluindo o novo inspetor
                cliente.enviarMensagem("CHAT:CONECTADO:" + nomeNovoInspetor);
            }
        }
    }

    // Método para notificar clientes sobre inspetor desconectado (Manter este método, chamado por removerCliente)
    private void notificarClientesInspetorDesconectado(String nomeInspetorDesconectado) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                 // Enviar a notificação para todos os clientes restantes
                cliente.enviarMensagem("CHAT:DESCONECTADO:" + nomeInspetorDesconectado);
            }
        }
    }

    // Novo método para adicionar cliente de chat APÓS a identificação
    public synchronized void adicionarClienteChat(ClienteHandler cliente) {
        clientes.add(cliente);
        registrarLog("Novo inspetor conectado: " + cliente.getNomeInspetor() + " - Local: " + cliente.getLocalMonitorado());
        // Notificar todos os clientes (incluindo o novo) sobre o novo inspetor
        notificarClientesNovoInspetor(cliente.getNomeInspetor());
    }

    /**
     * Classe interna que gerencia cada conexão de cliente
     */
    private class ClienteHandler implements Runnable {
        private Socket socket;
        private BufferedReader entrada;
        private PrintWriter saida;
        private String nomeInspetor;
        private String localMonitorado;

        public ClienteHandler(Socket socket) {
            this.socket = socket;
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                saida = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                registrarLog("Erro ao configurar cliente: " + e.getMessage());
            }
        }

        @Override
        public void run() {
            try {
                // Tentar ler nome e local do inspetor primeiro (conexão de chat)
                socket.setSoTimeout(500); // Timeout curto para a leitura inicial de identificação
                nomeInspetor = entrada.readLine();
                localMonitorado = entrada.readLine();
                socket.setSoTimeout(0); // Remover timeout após ler identificação

                if (nomeInspetor != null && localMonitorado != null) {
                    // É uma conexão de chat válida
                    ServidorMonitoramento.this.adicionarClienteChat(this);

                    // Envia mensagem de boas-vindas
                    saida.println("Bem-vindo ao Sistema de Monitoramento Ambiental do Rio Tietê, " + nomeInspetor + "!");
                    saida.println("Você está monitorando: " + localMonitorado);

                    // Loop principal de recebimento de mensagens
                    String mensagem;
                    while ((mensagem = entrada.readLine()) != null) {
                        if (mensagem.equals("SAIR")) {
                            break;
                        }

                        if (mensagem.startsWith("CHAT:")) {
                            String conteudoChat = mensagem.substring(5);
                            if (conteudoChat.equals("LISTAR_INSPETORES")) {
                                 enviarListaInspetoresChat();
                            } else if (conteudoChat.startsWith("PARA:")) {
                                 processarMensagemChat(conteudoChat.substring(5));
                            }
                        } else if (mensagem.startsWith("ALERTA:")) {
                            registrarLog("ALERTA de " + nomeInspetor + ": " + mensagem.substring(7));
                            enviarParaTodosClientes("ALERTA:" + nomeInspetor + " - " + mensagem.substring(7));
                        } else if (mensagem.startsWith("RELATORIO:")) {
                            registrarLog("RELATÓRIO de " + nomeInspetor + ": " + mensagem.substring(10));
                        } else {
                            registrarLog(nomeInspetor + ": " + mensagem);
                        }
                    }
                } else {
                    // Nome ou local nulo após timeout/conexão prematura - tentar como transferência de arquivo
                    registrarLog("Tentativa de conexão sem identificação (possível transferência de arquivo) de: " + socket.getInetAddress().getHostAddress());
                     // Nao fechar o socket aqui, a tentativa de receber arquivo ou finally cuidará disso
                     // Tentar receber arquivo
                     File arquivoRecebido = TransferenciaArquivos.receberArquivo(socket, "arquivos_recebidos");
                     if (arquivoRecebido != null) {
                         registrarLog("Arquivo recebido de: " + arquivoRecebido.getName() + " da conexão: " + socket.getInetAddress().getHostAddress());
                         // Futuramente: exibir miniatura ou link na interface
                         // Conexão será fechada no finally
                     } else {
                          // Não foi chat nem arquivo, conexão inválida
                          registrarLog("Conexão inválida ou incompleta de: " + socket.getInetAddress().getHostAddress());
                     }
                }

            } catch (IOException e) {
                // Erro geral de I/O durante a comunicação (inicial ou durante o chat)
                 registrarLog("Erro na comunicação do cliente " + (nomeInspetor != null ? nomeInspetor : socket.getInetAddress().getHostAddress()) + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    registrarLog("Erro ao fechar conexão com " + (nomeInspetor != null ? nomeInspetor : socket.getInetAddress().getHostAddress()) + ": " + e.getMessage());
                }
                // Remover o cliente apenas se ele foi adicionado (ou seja, nomeInspetor não é null e a conexão foi de chat)
                if (nomeInspetor != null) {
                   ServidorMonitoramento.this.removerCliente(this);
                }
            }
        }

        public void enviarMensagem(String mensagem) {
            saida.println(mensagem);
        }

        // Novo método para enviar a lista de inspetores para o chat
        private void enviarListaInspetoresChat() {
            StringBuilder lista = new StringBuilder();
            synchronized (clientes) { // Sincroniza para evitar modificação concorrente da lista
                for (ClienteHandler cliente : clientes) {
                    // Não inclui o próprio inspetor na lista enviada a ele mesmo
                    if (!cliente.getNomeInspetor().equals(nomeInspetor)) {
                         if (lista.length() > 0) lista.append(",");
                         lista.append(cliente.getNomeInspetor());
                    }
                }
            }
            // Envia a lista de volta para o cliente solicitante
            saida.println("CHAT:LISTA_INSPETORES:" + lista.toString());
        }

        // Novo método para processar mensagens de chat entre inspetores
        private void processarMensagemChat(String dadosMensagem) {
             registrarLog("DEBUG: Recebido em processarMensagemChat: " + dadosMensagem); // Log de depuração
             // Formato esperado: destinatario:mensagem
            int firstColon = dadosMensagem.indexOf(":");
            if (firstColon != -1) {
                String destinatario = dadosMensagem.substring(0, firstColon);
                String mensagemConteudo = dadosMensagem.substring(firstColon + 1);

                registrarLog("Chat de Inspetor de " + nomeInspetor + " para " + destinatario + ": " + mensagemConteudo);

                if (destinatario.equals("Todos")) {
                    // Enviar para todos os outros inspetores
                    synchronized (clientes) {
                        for (ClienteHandler cliente : clientes) {
                            if (!cliente.getNomeInspetor().equals(nomeInspetor)) {
                                cliente.enviarMensagem("CHAT:MSG_DE:" + nomeInspetor + ":" + mensagemConteudo);
                            }
                        }
                    }
                } else {
                    // Enviar para um inspetor específico
                     ClienteHandler clienteDestino = encontrarClientePorNome(destinatario);
                    if (clienteDestino != null) {
                        // Envia para o destinatário
                        clienteDestino.enviarMensagem("CHAT:MSG_DE:" + nomeInspetor + ":" + mensagemConteudo + " [PRIVADO]");
                        // Envia confirmação para o remetente
                        saida.println("CHAT:MSG_DE:" + nomeInspetor + ":" + mensagemConteudo + " [PRIVADO]");
                    } else {
                         // Inspetor de destino não encontrado, avisar o remetente
                         saida.println("CHAT:MSG_DE:Sistema:Inspetor " + destinatario + " não encontrado.");
                         registrarLog("Destinatário de chat " + destinatario + " não encontrado.");
                    }
                }
            }
        }

        // Método auxiliar para encontrar um cliente pelo nome
        private ClienteHandler encontrarClientePorNome(String nome) {
            synchronized (clientes) {
                for (ClienteHandler cliente : clientes) {
                    if (cliente.getNomeInspetor() != null && cliente.getNomeInspetor().equals(nome)) {
                        return cliente;
                    }
                }
            }
            return null;
        }

        public String getNomeInspetor() {
            return nomeInspetor;
        }

        public String getLocalMonitorado() {
            return localMonitorado;
        }
    }
}
