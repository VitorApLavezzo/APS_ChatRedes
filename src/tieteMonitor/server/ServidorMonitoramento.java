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
    private Map<String, String> catalogoArquivos = new HashMap<>();

    public static void main(String[] args) {
        new ServidorMonitoramento().iniciar();
    }

    public ServidorMonitoramento() {
        inicializarLocais();
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
        btnEnviarAlerta.setPreferredSize(new Dimension(200, 30));
        btnEnviarAlerta.addActionListener(e -> {
            String mensagem = JOptionPane.showInputDialog(frame, "Digite a mensagem de alerta:");
            if (mensagem != null && !mensagem.trim().isEmpty()) {
                enviarParaTodosClientes("ALERTA:" + mensagem);
                registrarLog("ALERTA ENVIADO: " + mensagem);
            }
        });
        painelInferior.add(btnEnviarAlerta);
        JButton btnListarClientes = new JButton("Listar Inspetores Conectados");
        btnListarClientes.setPreferredSize(new Dimension(200, 30));
        btnListarClientes.addActionListener(e -> {
            StringBuilder sb = new StringBuilder("Inspetores conectados:\n");
            synchronized (clientes) {
                if (clientes.isEmpty()) {
                    sb.append("Nenhum inspetor conectado no momento.");
                } else {
                    for (ClienteHandler cliente : clientes) {
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
            if (logComTimestamp.contains("ALERTA ENVIADO:") || logComTimestamp.contains("ALERTA de")) {
                 logArea.append(logComTimestamp + " 🚨\n");
            } else if (logComTimestamp.contains("RELATÓRIO de")){
                 logArea.append(logComTimestamp + " 📝\n");
            }else if (logComTimestamp.contains("Arquivo recebido de:")){
                 logArea.append(logComTimestamp + " 📁\n");
            }
            else {
                logArea.append(logComTimestamp + "\n");
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public synchronized void enviarParaTodosClientes(String mensagem) {
        for (ClienteHandler cliente : clientes) {
            cliente.enviarMensagem(mensagem);
        }
    }

    private void notificarClientesNovoInspetor(String nomeNovoInspetor) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                cliente.enviarMensagem("CHAT:CONECTADO:" + nomeNovoInspetor);
            }
        }
    }

    private void notificarClientesInspetorDesconectado(String nomeInspetorDesconectado) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                cliente.enviarMensagem("CHAT:DESCONECTADO:" + nomeInspetorDesconectado);
            }
        }
    }

    public synchronized void adicionarClienteChat(ClienteHandler cliente) {
        clientes.add(cliente);
        registrarLog("Novo inspetor conectado: " + cliente.getNomeInspetor() + " - Local: " + cliente.getLocalMonitorado());
        notificarClientesNovoInspetor(cliente.getNomeInspetor());
    }

    /**
     * Classe interna que gerencia cada conexão de cliente
     */
    private class ClienteHandler implements Runnable {
        private Socket socket;
        private DataInputStream dataIn;
        private DataOutputStream dataOut;
        private String nomeInspetor;
        private String localMonitorado;
        public ClienteHandler(Socket socket) {
            this.socket = socket;
        }
        @Override
        public void run() {
            try {
                dataIn = new DataInputStream(socket.getInputStream());
                dataOut = new DataOutputStream(socket.getOutputStream());
                String primeiroComando;
                try {
                    primeiroComando = dataIn.readUTF();
                } catch (IOException e) {
                    registrarLog("Erro ao ler primeiro comando da conexão de: " + socket.getInetAddress().getHostAddress() + " - " + e.getMessage());
                    return;
                }

                if (primeiroComando.startsWith("ARQUIVO:")) {
                    String[] partes = primeiroComando.split(":", 4);
                    if (partes.length >= 4) {
                        String nomeArquivoOriginal = partes[1];
                        String destinatario = partes[2];
                        String remetente = partes[3];
                        String nomeUnico = System.currentTimeMillis() + "_" + nomeArquivoOriginal.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
                        registrarLog("DEBUG: Iniciando recebimento do arquivo: " + nomeArquivoOriginal);
                        registrarLog("DEBUG: Nome único gerado: " + nomeUnico);
                        registrarLog("DEBUG: Destinatário: " + destinatario);
                        registrarLog("DEBUG: Remetente: " + remetente);
                        long tamanhoArquivo = dataIn.readLong();
                        registrarLog("DEBUG: Tamanho do arquivo recebido: " + tamanhoArquivo + " bytes");
                        File pastaDestino = new File("arquivos_recebidos");
                        if (!pastaDestino.exists()) {
                            pastaDestino.mkdirs();
                        }
                        File arquivoDestino = new File(pastaDestino, nomeUnico);
                        registrarLog("DEBUG: Salvando arquivo em: " + arquivoDestino.getAbsolutePath());
                        try (FileOutputStream fileOut = new FileOutputStream(arquivoDestino)) {
                            byte[] buffer = new byte[8192];
                            long bytesRestantes = tamanhoArquivo;
                            int bytesLidos;
                            long totalRecebido = 0;
                            while (bytesRestantes > 0) {
                                bytesLidos = dataIn.read(buffer, 0, (int) Math.min(buffer.length, bytesRestantes));
                                if (bytesLidos == -1) {
                                    throw new IOException("Conexão fechada inesperadamente");
                                }
                                fileOut.write(buffer, 0, bytesLidos);
                                bytesRestantes -= bytesLidos;
                                totalRecebido += bytesLidos;
                                registrarLog("DEBUG: Recebidos " + totalRecebido + " de " + tamanhoArquivo + " bytes");
                            }
                            fileOut.flush();
                            registrarLog("DEBUG: Arquivo salvo com sucesso");
                            dataOut.writeUTF("ARQUIVO_RECEBIDO");
                            dataOut.flush();
                            synchronized (ServidorMonitoramento.this.catalogoArquivos) {
                                String valorCatalogo = nomeArquivoOriginal + "|" + remetente;
                                ServidorMonitoramento.this.catalogoArquivos.put(nomeUnico, valorCatalogo);
                                registrarLog("DEBUG: Arquivo adicionado ao catálogo - Nome Único: " + nomeUnico + ", Nome Original: " + nomeArquivoOriginal + ", Remetente: " + remetente);
                                registrarLog("DEBUG: Tamanho atual do catálogo: " + ServidorMonitoramento.this.catalogoArquivos.size());
                                registrarLog("DEBUG: Conteúdo do catálogo após adição: " + ServidorMonitoramento.this.catalogoArquivos);
                            }
                            String mensagemNotificacao = "ARQUIVO:" + nomeUnico + ":" + remetente + ":" + nomeArquivoOriginal;
                            if (destinatario.equals("Todos os Inspetores")) {
                                enviarParaTodosClientes(mensagemNotificacao);
                            } else if (destinatario.equals("Central")) {
                                ClienteHandler clienteCentral = encontrarClientePorNomeOuCentral("Central");
                                if (clienteCentral != null) {
                                    clienteCentral.enviarMensagem(mensagemNotificacao);
                                } else {
                                    registrarLog("Central não encontrada para envio de arquivo.");
                                }
                            } else {
                                ClienteHandler clienteDestino = encontrarClientePorNome(destinatario);
                                if (clienteDestino != null) {
                                    clienteDestino.enviarMensagem(mensagemNotificacao);
                                } else {
                                    registrarLog("Destinatário '" + destinatario + "' para arquivo não encontrado.");
                                }
                            }
                        } catch (IOException e) {
                            registrarLog("Erro ao receber arquivo '" + nomeArquivoOriginal + "' de " + remetente);
                        }
                    } else {
                        registrarLog("Comando ARQUIVO mal formado de: " + socket.getInetAddress().getHostAddress() + " Comando: " + primeiroComando);
                    }
                    return;
                } else if (primeiroComando.startsWith("DOWNLOAD:")) {
                    String nomeUnicoSolicitado = primeiroComando.substring(9);
                    registrarLog("Pedido de download do arquivo único: " + nomeUnicoSolicitado + " de " + socket.getInetAddress().getHostAddress());
                    try {
                        dataOut.writeUTF("INICIANDO_DOWNLOAD");
                    } catch (Exception e) {
                        registrarLog("Erro ao enviar confirmação de download: " + e.getMessage());
                        return;
                    }
                    boolean enviado = TransferenciaArquivos.enviarArquivoParaCliente(socket, nomeUnicoSolicitado, "arquivos_recebidos");
                    if (enviado) {
                        registrarLog("Arquivo único '" + nomeUnicoSolicitado + "' enviado para download.");
                    } else {
                        registrarLog("Falha ao enviar arquivo único '" + nomeUnicoSolicitado + "' para download (arquivo não encontrado ou erro).");
                    }
                    return;
                } else if (primeiroComando.equals("LISTAR_ARQUIVOS")) {
                    registrarLog("Pedido de lista de arquivos de: " + socket.getInetAddress().getHostAddress());
                    enviarListaArquivosDisponiveis();
                    return;
                } else {
                    nomeInspetor = primeiroComando;
                    try {
                        localMonitorado = dataIn.readUTF();
                        if (encontrarClientePorNome(nomeInspetor) != null) {
                            dataOut.writeUTF("CHAT:MSG_DE:Sistema:Nome de usuário '" + nomeInspetor + "' já em uso.");
                            registrarLog("Tentativa de conexão com nome duplicado: " + nomeInspetor);
                            return;
                        }
                        ServidorMonitoramento.this.adicionarClienteChat(this);
                        registrarLog("Novo inspetor conectado: " + nomeInspetor + " - Local: " + localMonitorado);
                        dataOut.writeUTF("BEMVINDO:" + nomeInspetor);
                        dataOut.writeUTF("LOCAL:" + localMonitorado);
                        enviarListaInspetoresChat();
                    } catch (IOException e) {
                        registrarLog("Erro ao processar conexão de inspetor: " + e.getMessage());
                        return;
                    }
                }
                while (true) {
                    String mensagem = dataIn.readUTF();
                    processarMensagemChat(mensagem);
                }
            } catch (IOException e) {
                registrarLog("Conexão encerrada: " + (nomeInspetor != null ? nomeInspetor : socket.getInetAddress().getHostAddress()));
            } finally {
                if (nomeInspetor != null) {
                    ServidorMonitoramento.this.removerCliente(this);
                    notificarDesconexaoParaOutros(nomeInspetor);
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    registrarLog("Erro ao fechar socket: " + e.getMessage());
                }
            }
        }

        public void enviarMensagem(String mensagem) {
            try {
                dataOut.writeUTF(mensagem);
            } catch (IOException e) {
                registrarLog("Erro ao enviar mensagem para " + (nomeInspetor != null ? nomeInspetor : "cliente desconectado") + ": " + e.getMessage());
            }
        }

        private void enviarListaInspetoresChat() {
            StringBuilder listaNomes = new StringBuilder();
            synchronized (clientes) {
                boolean first = true;
                for (ClienteHandler cliente : clientes) {
                    if (cliente.getNomeInspetor() != null && !cliente.getNomeInspetor().trim().isEmpty()) {
                        if (!first) listaNomes.append(",");
                        listaNomes.append(cliente.getNomeInspetor().trim());
                        first = false;
                    }
                }
            }
            try {
                dataOut.writeUTF("CHAT:LISTA_INSPETORES:" + listaNomes.toString());
                registrarLog("Lista CHAT:LISTA_INSPETORES enviada para " + nomeInspetor + ": " + listaNomes.toString());
            } catch (IOException e) {
                registrarLog("Erro ao enviar lista de inspetores para " + nomeInspetor + ": " + e.getMessage());
            }
        }

        private void processarMensagemChat(String mensagemCompleta) {
            registrarLog("DEBUG: Recebido em processarMensagemChat: " + mensagemCompleta);
            String dadosMensagem;
            if (mensagemCompleta.startsWith("CHAT:")) {
                dadosMensagem = mensagemCompleta.substring(5);
            } else {
                registrarLog("Mensagem CHAT recebida com formato inválido (sem prefixo CHAT:): " + mensagemCompleta);
                try {
                    dataOut.writeUTF("CHAT:MSG_DE:Sistema:Formato de mensagem inválido.");
                } catch (IOException e) {
                     registrarLog("Erro ao avisar cliente sobre formato invalido: " + e.getMessage());
                }
                return;
            }

            if (dadosMensagem.equals("LISTAR_INSPETORES")) {
                enviarListaInspetoresChat();
                registrarLog("Comando CHAT:LISTAR_INSPETORES processado de " + nomeInspetor);
                return;
            } else if (dadosMensagem.startsWith("ALERTA:")) {
                String mensagemAlerta = dadosMensagem.substring(7);
                registrarLog("ALERTA de " + nomeInspetor + ": " + mensagemAlerta);
                notificarOutrosClientesAlerta(nomeInspetor, mensagemAlerta);
                return;
            }
            if (dadosMensagem.startsWith("PARA:")) {
                String conteudoPara = dadosMensagem.substring(5);
                int firstColon = conteudoPara.indexOf(":");
            if (firstColon != -1) {
                    String destinatario = conteudoPara.substring(0, firstColon);
                    String mensagemConteudo = conteudoPara.substring(firstColon + 1);

                registrarLog("Chat de Inspetor de " + nomeInspetor + " para " + destinatario + ": " + mensagemConteudo);

                if (destinatario.equals("Todos")) {
                    synchronized (clientes) {
                        for (ClienteHandler cliente : clientes) {
                            if (!cliente.getNomeInspetor().equals(nomeInspetor)) {
                                     cliente.enviarMensagem("CHAT:MSG_DE:" + nomeInspetor + ":" + mensagemConteudo); // Usa o enviarMensagem do handler de cada cliente
                                 }
                            }
                        }
                        registrarLog("Mensagem CHAT para Todos de " + nomeInspetor + " enviada para outros clientes.");
                    } else {
                        ClienteHandler clienteDestino = encontrarClientePorNome(destinatario);
                         if (clienteDestino != null) {
                             clienteDestino.enviarMensagem("CHAT:MSG_DE:" + nomeInspetor + ":" + mensagemConteudo + " [PRIVADO]");
                             registrarLog("Mensagem CHAT privada de " + nomeInspetor + " para " + destinatario + " enviada.");
                         } else {
                             enviarMensagem("CHAT:MSG_DE:Sistema:Inspetor '" + destinatario + "' não encontrado para chat privado.");
                             registrarLog("Destinatário de chat privado '" + destinatario + "' não encontrado (remetente: " + nomeInspetor + ").");
                        }
                    }
                } else {
                    registrarLog("Mensagem CHAT PARA: mal formada de " + nomeInspetor + ": " + dadosMensagem);
                    enviarMensagem("CHAT:MSG_DE:Sistema:Comando PARA: mal formado.");
                }
            } else if (dadosMensagem.startsWith("MSG_DE:")) {
                registrarLog("DEBUG: Mensagem CHAT MSG_DE recebida inesperadamente (não processada no servidor): " + dadosMensagem);
                    } else {
                registrarLog("Comando CHAT desconhecido de " + nomeInspetor + ": " + dadosMensagem);
                try {
                    dataOut.writeUTF("CHAT:MSG_DE:Sistema:Comando CHAT desconhecido.");
                } catch (IOException e) {
                     registrarLog("Erro ao avisar cliente sobre comando CHAT desconhecido: " + e.getMessage());
                }
            }
        }

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

        private ClienteHandler encontrarClientePorNomeOuCentral(String nomeOuCentral) {
            for (ClienteHandler cliente : clientes) {
                if (cliente.getNomeInspetor() != null && cliente.getNomeInspetor().equals(nomeOuCentral)) {
                    return cliente;
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

        private void notificarOutrosClientesAlerta(String remetenteAlerta, String mensagemAlerta) {
            synchronized (clientes) {
                for (ClienteHandler cliente : clientes) {
                    if (!cliente.getNomeInspetor().equals(remetenteAlerta)) {
                        cliente.enviarMensagem("CHAT:ALERTA:" + remetenteAlerta + ":" + mensagemAlerta); // <--- Formato: CHAT:ALERTA:remetente:mensagem
                    }
                }
            }
        }

        private void enviarListaArquivosDisponiveis() {
            StringBuilder lista = new StringBuilder();
            synchronized (ServidorMonitoramento.this.catalogoArquivos) {
                registrarLog("DEBUG: Enviando lista de arquivos. Tamanho do catálogo: " + ServidorMonitoramento.this.catalogoArquivos.size());
                registrarLog("DEBUG: Conteúdo do catálogo: " + ServidorMonitoramento.this.catalogoArquivos);
                
                if (ServidorMonitoramento.this.catalogoArquivos.isEmpty()) {
                    try {
                        dataOut.writeUTF("");
                        dataOut.flush();
                        registrarLog("Lista de arquivos vazia enviada para " + socket.getInetAddress().getHostAddress());
                    } catch (IOException e) {
                        registrarLog("Erro ao enviar lista de arquivos vazia: " + e.getMessage());
                    }
                    return;
                }

                boolean first = true;
                for (Map.Entry<String, String> entry : ServidorMonitoramento.this.catalogoArquivos.entrySet()) {
                    if (!first) lista.append(";");
                    String[] partes = entry.getValue().split("\\|");
                    String nomeOriginal = partes[0];
                    String remetente = partes.length > 1 ? partes[1] : "Desconhecido";
                    lista.append(entry.getKey())
                         .append("|")
                         .append(nomeOriginal)
                         .append("|")
                         .append(remetente);
                    first = false;
                    registrarLog("DEBUG: Adicionando à lista - Nome Único: " + entry.getKey() + ", Nome Original: " + nomeOriginal + ", Remetente: " + remetente);
                }
            }

            try {
                String listaFinal = lista.toString();
                registrarLog("DEBUG: Lista final a ser enviada: " + listaFinal);
                dataOut.writeUTF(listaFinal);
                dataOut.flush();
                registrarLog("Lista de arquivos enviada para " + socket.getInetAddress().getHostAddress() + ": " + listaFinal);
            } catch (IOException e) {
                registrarLog("Erro ao enviar lista de arquivos: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void notificarConexaoParaOutros(String nomeNovoInspetor) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                if (!cliente.getNomeInspetor().equals(nomeNovoInspetor)) {
                    cliente.enviarMensagem("CHAT:CONECTADO:" + nomeNovoInspetor);
                }
            }
        }
    }

    public void removerCliente(ClienteHandler clienteHandler) {
         synchronized (clientes) {
            if (clientes.remove(clienteHandler)) {
                registrarLog("Cliente " + clienteHandler.getNomeInspetor() + " removido da lista.");
                 notificarDesconexaoParaOutros(clienteHandler.getNomeInspetor());
            } else {
                 registrarLog("Erro: Cliente " + clienteHandler.getNomeInspetor() + " não encontrado na lista para remover.");
            }
        }
    }

    private void notificarDesconexaoParaOutros(String nomeInspetorDesconectado) {
        synchronized (clientes) {
            for (ClienteHandler cliente : clientes) {
                 cliente.enviarMensagem("CHAT:DESCONECTADO:" + nomeInspetorDesconectado);
            }
        }
    }

    private void processarComandoArquivo(String comando, ClienteHandler clienteHandler) {
        if (comando.startsWith("DOWNLOAD:")) {
            String nomeUnico = comando.substring(9);
            registrarLog("Pedido de download do arquivo: " + nomeUnico);
            try {
                clienteHandler.enviarMensagem("INICIANDO_DOWNLOAD");
            } catch (Exception e) {
                registrarLog("Erro ao enviar confirmação de download: " + e.getMessage());
                return;
            }
            boolean enviado = TransferenciaArquivos.enviarArquivoParaCliente(clienteHandler.socket, nomeUnico, "arquivos_recebidos");
            if (enviado) {
                registrarLog("Arquivo enviado com sucesso: " + nomeUnico);
            } else {
                registrarLog("Falha ao enviar arquivo: " + nomeUnico);
            }
        }
    }
}
