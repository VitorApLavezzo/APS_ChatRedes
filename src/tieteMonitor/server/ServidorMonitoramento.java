package tieteMonitor.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;
import javax.swing.*;
import java.awt.*;
import java.util.List;

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
            if (clientes.isEmpty()) {
                sb.append("Nenhum inspetor conectado no momento.");
            } else {
                for (ClienteHandler cliente : clientes) {
                    sb.append("- ").append(cliente.getNomeInspetor())
                            .append(" (").append(cliente.getLocalMonitorado()).append(")\n");
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

                ClienteHandler clienteHandler = new ClienteHandler(clienteSocket);
                clientes.add(clienteHandler);
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
            logArea.append(logComTimestamp + "\n");
            // Auto-scroll para a última linha
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public synchronized void removerCliente(ClienteHandler cliente) {
        clientes.remove(cliente);
        registrarLog("Inspetor desconectado: " + cliente.getNomeInspetor() +
                " - Local: " + cliente.getLocalMonitorado());
    }

    public synchronized void enviarParaTodosClientes(String mensagem) {
        for (ClienteHandler cliente : clientes) {
            cliente.enviarMensagem(mensagem);
        }
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
                registrarLog("Erro ao configurar streams: " + e.getMessage());
            }
        }

        public String getNomeInspetor() {
            return nomeInspetor != null ? nomeInspetor : "Não identificado";
        }

        public String getLocalMonitorado() {
            return localMonitorado != null ? localMonitorado : "Local não especificado";
        }

        public void enviarMensagem(String mensagem) {
            saida.println(mensagem);
        }

        @Override
        public void run() {
            try {
                // Recebe identificação do inspetor
                nomeInspetor = entrada.readLine();
                localMonitorado = entrada.readLine();

                // Valida local de monitoramento
                String localFormatado = locaisMonitorados.getOrDefault(
                        localMonitorado.toLowerCase(), localMonitorado);

                registrarLog("Inspetor conectado: " + nomeInspetor + " - Local: " + localFormatado);

                // Avisa outros inspetores sobre a nova conexão
                enviarParaTodosClientes("SISTEMA: Inspetor " + nomeInspetor +
                        " entrou no sistema, monitorando " + localFormatado);

                String mensagemRecebida;
                while ((mensagemRecebida = entrada.readLine()) != null) {
                    if (mensagemRecebida.equals("SAIR")) {
                        break;
                    } else if (mensagemRecebida.startsWith("ALERTA:")) {
                        // Processa mensagem de alerta e repassa para todos
                        String conteudoAlerta = mensagemRecebida.substring(7);
                        registrarLog("ALERTA de " + nomeInspetor + ": " + conteudoAlerta);
                        enviarParaTodosClientes("ALERTA de " + nomeInspetor +
                                " (" + localFormatado + "): " + conteudoAlerta);
                    } else if (mensagemRecebida.startsWith("RELATORIO:")) {
                        // Processa recebimento de relatório
                        String conteudoRelatorio = mensagemRecebida.substring(10);
                        registrarLog("Relatório recebido de " + nomeInspetor +
                                " (" + localFormatado + ")");

                        // Salva relatório em arquivo
                        salvarRelatorio(nomeInspetor, localFormatado, conteudoRelatorio);
                    } else {
                        // Mensagem normal
                        registrarLog(nomeInspetor + " (" + localFormatado + "): " + mensagemRecebida);

                        // Repassa para outros inspetores
                        for (ClienteHandler cliente : clientes) {
                            if (cliente != this) {
                                cliente.enviarMensagem(nomeInspetor + " (" + localFormatado + "): " +
                                        mensagemRecebida);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                registrarLog("Erro na comunicação com " + getNomeInspetor() + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    registrarLog("Erro ao fechar socket: " + e.getMessage());
                }
                removerCliente(this);
            }
        }

        private void salvarRelatorio(String inspetor, String local, String conteudo) {
            try {
                // Cria pasta de relatórios se não existir
                File pastaRelatorios = new File("relatorios");
                if (!pastaRelatorios.exists()) {
                    pastaRelatorios.mkdir();
                }

                // Cria nome de arquivo único baseado em data e hora
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String nomeArquivo = "relatorios/Relatorio_" + local.replaceAll("[^a-zA-Z0-9]", "_") +
                        "_" + sdf.format(new Date()) + ".txt";

                // Escreve relatório no arquivo
                FileWriter fw = new FileWriter(nomeArquivo);
                fw.write("RELATÓRIO DE INSPEÇÃO AMBIENTAL\n");
                fw.write("===============================\n");
                fw.write("Inspetor: " + inspetor + "\n");
                fw.write("Local: " + local + "\n");
                fw.write("Data/Hora: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()) + "\n");
                fw.write("===============================\n\n");
                fw.write(conteudo);
                fw.close();

                registrarLog("Relatório salvo em: " + nomeArquivo);
            } catch (IOException e) {
                registrarLog("Erro ao salvar relatório: " + e.getMessage());
            }
        }
    }
}
