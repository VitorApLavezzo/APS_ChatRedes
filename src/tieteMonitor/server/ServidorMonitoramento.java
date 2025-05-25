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
                    // É uma conexão de UPLOAD de arquivo
                    String[] partes = primeiroComando.split(":", 4);
                    if (partes.length >= 4) {
                        String nomeArquivoOriginal = partes[1];
                        String destinatario = partes[2];
                        String remetente = partes[3];

                        // GERA NOME ÚNICO E SALVA O ARQUIVO
                        String nomeUnico = System.currentTimeMillis() + "_" + nomeArquivoOriginal.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
                        registrarLog("DEBUG: Iniciando recebimento do arquivo: " + nomeArquivoOriginal);
                        registrarLog("DEBUG: Nome único gerado: " + nomeUnico);
                        registrarLog("DEBUG: Destinatário: " + destinatario);
                        registrarLog("DEBUG: Remetente: " + remetente);

                        // Lê o tamanho do arquivo
                        long tamanhoArquivo = dataIn.readLong();
                        registrarLog("DEBUG: Tamanho do arquivo recebido: " + tamanhoArquivo + " bytes");

                        // Cria o arquivo de destino
                        File pastaDestino = new File("arquivos_recebidos");
                        if (!pastaDestino.exists()) {
                            pastaDestino.mkdirs();
                        }
                        File arquivoDestino = new File(pastaDestino, nomeUnico);
                        registrarLog("DEBUG: Salvando arquivo em: " + arquivoDestino.getAbsolutePath());

                        // Recebe e salva o arquivo
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

                            // Envia confirmação para o cliente
                            dataOut.writeUTF("ARQUIVO_RECEBIDO");
                            dataOut.flush();

                            // Adiciona o arquivo ao catálogo
                            synchronized (ServidorMonitoramento.this.catalogoArquivos) {
                                String valorCatalogo = nomeArquivoOriginal + "|" + remetente;
                                ServidorMonitoramento.this.catalogoArquivos.put(nomeUnico, valorCatalogo);
                                registrarLog("DEBUG: Arquivo adicionado ao catálogo - Nome Único: " + nomeUnico + ", Nome Original: " + nomeArquivoOriginal + ", Remetente: " + remetente);
                                registrarLog("DEBUG: Tamanho atual do catálogo: " + ServidorMonitoramento.this.catalogoArquivos.size());
                                registrarLog("DEBUG: Conteúdo do catálogo após adição: " + ServidorMonitoramento.this.catalogoArquivos);
                            }
                            
                            // Notificar o(s) destinatário(s) sobre o arquivo
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
                    // É uma conexão de DOWNLOAD de arquivo
                    String nomeUnicoSolicitado = primeiroComando.substring(9);
                    registrarLog("Pedido de download do arquivo único: " + nomeUnicoSolicitado + " de " + socket.getInetAddress().getHostAddress());

                    // Envia confirmação para o cliente
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
                    // É uma conexão de CHAT/COMANDO normal
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

                // Loop principal para receber mensagens
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
                // Opcional: tentar remover o cliente se o erro indicar conexão perdida
            }
        }

        // Método para enviar a lista de inspetores de chat conectados para ESTE cliente
        private void enviarListaInspetoresChat() {
            // Constrói a string da lista de nomes dos inspetores conectados
            StringBuilder listaNomes = new StringBuilder();
            synchronized (clientes) { // Sincroniza para iterar na lista de clientes globais
                boolean first = true;
                for (ClienteHandler cliente : clientes) { // Iterar sobre a lista global 'clientes' do ServidorMonitoramento
                    // Adiciona o nome do inspetor à lista (se não for null e não estiver vazio)
                    if (cliente.getNomeInspetor() != null && !cliente.getNomeInspetor().trim().isEmpty()) {
                        if (!first) listaNomes.append(",");
                        listaNomes.append(cliente.getNomeInspetor().trim());
                        first = false;
                    }
                }
            }
            // Envia a lista formatada para ESTE cliente usando dataOut desta conexão
            try {
                // O comando completo deve ser CHAT:LISTA_INSPETORES:nome1,nome2,nome3
                dataOut.writeUTF("CHAT:LISTA_INSPETORES:" + listaNomes.toString());
                registrarLog("Lista CHAT:LISTA_INSPETORES enviada para " + nomeInspetor + ": " + listaNomes.toString());
            } catch (IOException e) {
                registrarLog("Erro ao enviar lista de inspetores para " + nomeInspetor + ": " + e.getMessage());
                // Se houver erro aqui, a conexão pode ter caído. O finally no run() deve lidar com a remoção.
            }
        }

        // Este método receberá a string COMPLETA lida do socket (ex: "CHAT:LISTAR_INSPETORES")
        private void processarMensagemChat(String mensagemCompleta) {
            registrarLog("DEBUG: Recebido em processarMensagemChat: " + mensagemCompleta); // Log de depuração

            // --- VERIFICAR E REMOVER O PREFIXO "CHAT:" ---
            String dadosMensagem;
            if (mensagemCompleta.startsWith("CHAT:")) {
                dadosMensagem = mensagemCompleta.substring(5); // Remove "CHAT:"
            } else {
                // Se receber algo aqui que não começa com CHAT: (o que não deveria ocorrer no fluxo de CHAT)
                registrarLog("Mensagem CHAT recebida com formato inválido (sem prefixo CHAT:): " + mensagemCompleta);
                try {
                    dataOut.writeUTF("CHAT:MSG_DE:Sistema:Formato de mensagem inválido.");
                } catch (IOException e) {
                     registrarLog("Erro ao avisar cliente sobre formato invalido: " + e.getMessage());
                }
                return; // Sai do método se o prefixo estiver faltando
            }

            // --- PRIMEIRO: Verificar comandos especiais (LISTAR_INSPETORES, ALERTA:) ---
            if (dadosMensagem.equals("LISTAR_INSPETORES")) { // Comparar com a string SEM o prefixo
                enviarListaInspetoresChat(); // Chama o método para enviar a lista
                registrarLog("Comando CHAT:LISTAR_INSPETORES processado de " + nomeInspetor);
                return; // Processou o comando, sai do método
            } else if (dadosMensagem.startsWith("ALERTA:")) { // Verificar com a string SEM o prefixo
                String mensagemAlerta = dadosMensagem.substring(7); // Remover "ALERTA:" da string SEM o prefixo CHAT:
                registrarLog("ALERTA de " + nomeInspetor + ": " + mensagemAlerta);
                notificarOutrosClientesAlerta(nomeInspetor, mensagemAlerta); // Retransmitir alerta
                return;
            }

            // --- SEGUNDO: Se não for um comando especial, processar como mensagem para destinatário (PARA:) ---
            // Formato esperado: PARA:destinatario:mensagem
            if (dadosMensagem.startsWith("PARA:")) { // Verificar com a string SEM o prefixo
                String conteudoPara = dadosMensagem.substring(5); // Remover "PARA:" da string SEM o prefixo CHAT:
                int firstColon = conteudoPara.indexOf(":");
            if (firstColon != -1) {
                    String destinatario = conteudoPara.substring(0, firstColon);
                    String mensagemConteudo = conteudoPara.substring(firstColon + 1);

                registrarLog("Chat de Inspetor de " + nomeInspetor + " para " + destinatario + ": " + mensagemConteudo);

                if (destinatario.equals("Todos")) {
                        // Envia para todos os outros inspetores
                    synchronized (clientes) {
                        for (ClienteHandler cliente : clientes) {
                                 // Envia para todos, exceto o remetente
                            if (!cliente.getNomeInspetor().equals(nomeInspetor)) {
                                     // Formato: CHAT:MSG_DE:remetente:mensagem
                                     cliente.enviarMensagem("CHAT:MSG_DE:" + nomeInspetor + ":" + mensagemConteudo); // Usa o enviarMensagem do handler de cada cliente
                                 }
                            }
                        }
                        registrarLog("Mensagem CHAT para Todos de " + nomeInspetor + " enviada para outros clientes.");
                    } else {
                        // Envia para um inspetor específico
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
                // Mensagens recebidas do servidor (retransmitidas). Servidor não precisa processar.
                registrarLog("DEBUG: Mensagem CHAT MSG_DE recebida inesperadamente (não processada no servidor): " + dadosMensagem);
                    } else {
                // Se não começou com um comando CHAT conhecido após remover o prefixo
                registrarLog("Comando CHAT desconhecido de " + nomeInspetor + ": " + dadosMensagem);
                try {
                    dataOut.writeUTF("CHAT:MSG_DE:Sistema:Comando CHAT desconhecido.");
                } catch (IOException e) {
                     registrarLog("Erro ao avisar cliente sobre comando CHAT desconhecido: " + e.getMessage());
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

        // Método para encontrar um cliente de chat pelo nome (ou a Central)
        private ClienteHandler encontrarClientePorNomeOuCentral(String nomeOuCentral) {
            // Lógica para encontrar a Central (se ela tiver um nome fixo ou uma forma de identificação)
            // if ("Central".equals(nomeOuCentral)) { return clienteCentral; } // Exemplo

            // Procurar entre os inspetores conectados
            for (ClienteHandler cliente : clientes) { // Assumindo que clientesChat é a lista de handlers de chat
                if (cliente.getNomeInspetor() != null && cliente.getNomeInspetor().equals(nomeOuCentral)) {
                    return cliente;
                }
            }
            return null; // Destinatário não encontrado
        }

        public String getNomeInspetor() {
            return nomeInspetor;
        }

        public String getLocalMonitorado() {
            return localMonitorado;
        }

        // NOVO MÉTODO NO ClienteHandler para notificar outros clientes sobre um alerta
        private void notificarOutrosClientesAlerta(String remetenteAlerta, String mensagemAlerta) {
            synchronized (clientes) { // Sincroniza a iteração
                for (ClienteHandler cliente : clientes) {
                    // Envia o alerta para todos, exceto o remetente
                    if (!cliente.getNomeInspetor().equals(remetenteAlerta)) {
                        cliente.enviarMensagem("CHAT:ALERTA:" + remetenteAlerta + ":" + mensagemAlerta); // <--- Formato: CHAT:ALERTA:remetente:mensagem
                    }
                }
            }
        }

        // NOVO MÉTODO NO ClienteHandler para enviar a lista de arquivos disponíveis
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
                    lista.append(entry.getKey()) // nomeUnico
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
                e.printStackTrace(); // Adiciona stack trace para debug
            }
        }
    }

    // NOVO MÉTODO NO ServidorMonitoramento para notificar todos os clientes de CHAT sobre uma nova conexão
    private void notificarConexaoParaOutros(String nomeNovoInspetor) {
        synchronized (clientes) { // Sincroniza para iterar na lista de clientes
            for (ClienteHandler cliente : clientes) {
                // Envia a notificação de CONECTADO para todos, EXCETO o próprio cliente que acabou de entrar
                if (!cliente.getNomeInspetor().equals(nomeNovoInspetor)) {
                    cliente.enviarMensagem("CHAT:CONECTADO:" + nomeNovoInspetor); // Envia a mensagem CONECTADO: para outros
                }
            }
        }
    }

    // Modificar o método removerCliente para notificar outros
    public void removerCliente(ClienteHandler clienteHandler) {
         synchronized (clientes) { // Sincroniza para modificar a lista
            if (clientes.remove(clienteHandler)) { // Tenta remover o cliente da lista
                registrarLog("Cliente " + clienteHandler.getNomeInspetor() + " removido da lista.");
                // --- NOVO: Notificar *todos os OUTROS clientes* que este cliente desconectou ---
                 notificarDesconexaoParaOutros(clienteHandler.getNomeInspetor()); // Implementar este método
            } else {
                 registrarLog("Erro: Cliente " + clienteHandler.getNomeInspetor() + " não encontrado na lista para remover.");
            }
        }
    }

    // NOVO MÉTODO NO ServidorMonitoramento para notificar todos os clientes de CHAT sobre uma desconexão
    private void notificarDesconexaoParaOutros(String nomeInspetorDesconectado) {
        synchronized (clientes) { // Sincroniza para iterar na lista de clientes
            for (ClienteHandler cliente : clientes) {
                // Envia a notificação de DESCONECTADO para todos (não precisa excluir ninguém, o desconectado já não está na lista)
                 cliente.enviarMensagem("CHAT:DESCONECTADO:" + nomeInspetorDesconectado); // Envia a mensagem DESCONECTADO: para outros
            }
        }
    }

    private void processarComandoArquivo(String comando, ClienteHandler clienteHandler) {
        if (comando.startsWith("DOWNLOAD:")) {
            String nomeUnico = comando.substring(9);
            registrarLog("Pedido de download do arquivo: " + nomeUnico);
            
            // Envia confirmação para o cliente
            try {
                clienteHandler.enviarMensagem("INICIANDO_DOWNLOAD");
            } catch (Exception e) {
                registrarLog("Erro ao enviar confirmação de download: " + e.getMessage());
                return;
            }
            
            // Envia o arquivo
            boolean enviado = TransferenciaArquivos.enviarArquivoParaCliente(clienteHandler.socket, nomeUnico, "arquivos_recebidos");
            
            if (enviado) {
                registrarLog("Arquivo enviado com sucesso: " + nomeUnico);
            } else {
                registrarLog("Falha ao enviar arquivo: " + nomeUnico);
            }
        }
    }
}
