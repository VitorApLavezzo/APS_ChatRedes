package tieteMonitor.client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import java.text.SimpleDateFormat;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.HashMap;

/**
 * Implementa uma funcionalidade de chat estilo WhatsApp entre inspetores
 * do sistema de monitoramento do Rio Tietê
 */
public class ChatInspetores {

    // Lista de contatos online
    private DefaultListModel<String> modeloContatos;
    private JList<String> listaContatos;

    // Mapa de conversas (nome do contato -> histórico de mensagens)
    private Map<String, JTextPane> conversas;

    // Contato selecionado atualmente
    private String contatoAtual;

    // Referência ao cliente principal
    private ClienteMonitoramento clientePrincipal;
    private Socket socket;
    private PrintWriter saida;

    // Interface gráfica
    private JDialog janelaChat;
    private JTextPane areaConversa;
    private JTextField campoMensagem;
    private JButton botaoEnviar;
    private JButton botaoAnexo;
    private JPanel painelConversa;

    /**
     * Construtor do chat de inspetores
     * @param cliente Referência ao cliente principal de monitoramento
     * @param socket Socket conectado ao servidor
     * @param saida PrintWriter para envio de mensagens
     */
    public ChatInspetores(ClienteMonitoramento cliente, Socket socket, PrintWriter saida) {
        this.clientePrincipal = cliente;
        this.socket = socket;
        this.saida = saida;

        this.conversas = new HashMap<>();
        this.modeloContatos = new DefaultListModel<>();

        // Inicializa a interface gráfica
        inicializarInterface();
    }

    /**
     * Inicializa a interface do chat
     */
    private void inicializarInterface() {
        // Cria a janela de chat
        janelaChat = new JDialog((JFrame)null, "Chat de Inspetores - Sistema Tietê", false);
        janelaChat.setSize(800, 600);
        janelaChat.setLayout(new BorderLayout());

        // Área de chat
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(areaChat);

        // Painel de seleção de destinatário
        JPanel painelDestinatario = new JPanel(new BorderLayout());
        painelDestinatario.setBorder(BorderFactory.createTitledBorder("Enviar para:"));

        modeloInspetores = new DefaultComboBoxModel<>();
        comboInspetores = new JComboBox<>(modeloInspetores);
        comboInspetores.addActionListener(e -> {
            destinatarioAtual = (String) comboInspetores.getSelectedItem();
            if (destinatarioAtual != null && !destinatarioAtual.equals("Todos")) {
                areaChat.append("[Sistema] Mensagens agora serão enviadas para: " + destinatarioAtual + "\n");
            }
        });

        JButton botaoAtualizar = new JButton("Atualizar");
        botaoAtualizar.addActionListener(e -> solicitarListaInspetores());

        painelDestinatario.add(comboInspetores, BorderLayout.CENTER);
        painelDestinatario.add(botaoAtualizar, BorderLayout.EAST);

        // Painel de mensagem
        JPanel painelMensagem = new JPanel(new BorderLayout());
        campoMensagem = new JTextField();
        campoMensagem.addActionListener(e -> enviarMensagem());

        // Botão de emoji
        JButton botaoEmoji = new JButton("😊");
        botaoEmoji.setFont(new Font("Dialog", Font.PLAIN, 18));
        botaoEmoji.setFocusable(false);
        botaoEmoji.setMargin(new Insets(2, 6, 2, 6));
        botaoEmoji.addActionListener(e -> abrirPopupEmojis());

        botaoEnviar = new JButton("Enviar");
        botaoEnviar.addActionListener(e -> enviarMensagem());

        // Adiciona campo, botão emoji e botão enviar
        JPanel painelCampo = new JPanel(new BorderLayout());
        painelCampo.add(campoMensagem, BorderLayout.CENTER);
        painelCampo.add(botaoEmoji, BorderLayout.WEST);
        painelCampo.add(botaoEnviar, BorderLayout.EAST);
        painelMensagem.add(painelCampo, BorderLayout.CENTER);

        // Inicialmente mostra uma mensagem de boas-vindas
        JPanel painelBoasVindas = new JPanel(new GridBagLayout());
        JLabel labelBoasVindas = new JLabel("<html><div style='text-align: center;'>" +
                "<h2>Bem-vindo ao Chat de Inspetores</h2>" +
                "<p>Selecione um contato para iniciar uma conversa</p>" +
                "</div></html>");
        painelBoasVindas.add(labelBoasVindas);
        painelConversa.add(painelBoasVindas, BorderLayout.CENTER);

        // Adiciona os painéis à janela principal com um divisor ajustável
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                painelContatos,
                painelConversa);
        splitPane.setDividerLocation(250);
        janelaChat.add(splitPane, BorderLayout.CENTER);

        // Configura a janela
        janelaChat.setLocationRelativeTo(null);
        janelaChat.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
    }

    /**
     * Cria o painel de conversa com um contato específico
     */
    private void abrirConversa(String contato) {
        // Atualiza o contato atual
        contatoAtual = contato;

        // Remove componentes existentes do painel de conversa
        painelConversa.removeAll();

        // Cabeçalho com o nome do contato
        JPanel painelCabecalho = new JPanel(new BorderLayout());
        painelCabecalho.setBackground(new Color(225, 245, 254));
        painelCabecalho.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel labelContato = new JLabel(contato);
        labelContato.setFont(new Font("SansSerif", Font.BOLD, 16));
        painelCabecalho.add(labelContato, BorderLayout.CENTER);

        // Área de conversa
        if (!conversas.containsKey(contato)) {
            // Cria uma nova área de conversa se não existir
            areaConversa = new JTextPane();
            areaConversa.setEditable(false);
            areaConversa.setContentType("text/html");
            areaConversa.setText("<html><body style='font-family: Arial, sans-serif;'></body></html>");
            conversas.put(contato, areaConversa);
        } else {
            // Usa a área de conversa existente
            areaConversa = conversas.get(contato);
        }

        JScrollPane scrollConversa = new JScrollPane(areaConversa);

        // Painel de entrada de mensagem
        JPanel painelEntrada = new JPanel(new BorderLayout(5, 0));
        painelEntrada.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        campoMensagem = new JTextField();
        campoMensagem.addActionListener(e -> enviarMensagemPrivada());

        botaoEnviar = new JButton("Enviar");
        botaoEnviar.addActionListener(e -> enviarMensagemPrivada());

        botaoAnexo = new JButton("📎");
        botaoAnexo.setToolTipText("Anexar arquivo");
        botaoAnexo.addActionListener(e -> anexarArquivo());

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        painelBotoes.add(botaoAnexo);
        painelBotoes.add(botaoEnviar);

        painelEntrada.add(campoMensagem, BorderLayout.CENTER);
        painelEntrada.add(painelBotoes, BorderLayout.EAST);

        // Adiciona componentes ao painel de conversa
        painelConversa.add(painelCabecalho, BorderLayout.NORTH);
        painelConversa.add(scrollConversa, BorderLayout.CENTER);
        painelConversa.add(painelEntrada, BorderLayout.SOUTH);

        // Atualiza o painel de conversa
        painelConversa.revalidate();
        painelConversa.repaint();

        // Foca no campo de mensagem
        campoMensagem.requestFocus();
    }

    /**
     * Envia uma mensagem privada para o contato selecionado
     */
    private void enviarMensagemPrivada() {
        String mensagem = campoMensagem.getText().trim();
        if (!mensagem.isEmpty() && contatoAtual != null && saida != null) {
            // Formata a mensagem para o protocolo
            saida.println("MENSAGEM_PRIVADA:" + contatoAtual + ":" + mensagem);

            // Adiciona a mensagem à área de conversa
            adicionarMensagemConversa(contatoAtual, mensagem, true);

            // Limpa o campo de mensagem
            campoMensagem.setText("");
        }
    }

    /**
     * Anexa um arquivo para enviar ao contato
     */
    private void anexarArquivo() {
        if (contatoAtual == null) {
            JOptionPane.showMessageDialog(janelaChat,
                    "Selecione um contato primeiro.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JFileChooser seletor = new JFileChooser();
        int resultado = seletor.showOpenDialog(janelaChat);

        if (resultado == JFileChooser.APPROVE_OPTION) {
            File arquivo = seletor.getSelectedFile();

            // Verifica tamanho do arquivo (máximo 5MB para este exemplo)
            if (arquivo.length() > 5 * 1024 * 1024) {
                JOptionPane.showMessageDialog(janelaChat,
                        "O arquivo é muito grande. Tamanho máximo: 5MB",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Inicia transferência em uma thread separada
            new Thread(() -> {
                try {
                    // Primeiro notifica o contato que está enviando um arquivo
                    saida.println("NOTIFICAR_ARQUIVO:" + contatoAtual + ":" + arquivo.getName() + ":" + arquivo.length());

                    // Cria um socket temporário para transferência
                    Socket socketTransferencia = new Socket(socket.getInetAddress().getHostAddress(), 9876);

                    // Usa a classe TransferenciaArquivos para enviar
                    boolean sucesso = TransferenciaArquivos.enviarArquivo(
                            socketTransferencia,
                            arquivo,
                            "MENSAGEM_PRIVADA_" + contatoAtual);

                    socketTransferencia.close();

                    // Adiciona mensagem à conversa
                    if (sucesso) {
                        final String mensagemArquivo = "Arquivo enviado: " + arquivo.getName();
                        SwingUtilities.invokeLater(() -> {
                            adicionarMensagemConversa(contatoAtual, mensagemArquivo, true);
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            JOptionPane.showMessageDialog(janelaChat,
                                    "Falha ao enviar o arquivo.",
                                    "Erro", JOptionPane.ERROR_MESSAGE);
                        });
                    }

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(janelaChat,
                                "Erro ao enviar arquivo: " + e.getMessage(),
                                "Erro", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start();
        }
    }

    /**
     * Solicita a lista de inspetores online ao servidor
     */
    public void solicitarListaContatos() {
        if (saida != null) {
            saida.println("LISTAR_INSPETORES");
        }
    }

    /**
     * Atualiza a lista de contatos
     * @param contatos Lista de nomes dos inspetores online
     */
    public void atualizarListaContatos(List<String> contatos) {
        SwingUtilities.invokeLater(() -> {
            modeloContatos.clear();
            for (String contato : contatos) {
                modeloContatos.addElement(contato);
            }
        });
    }

    /**
     * Adiciona uma nova mensagem à conversa
     * @param contato Nome do contato
     * @param mensagem Conteúdo da mensagem
     * @param enviada true se a mensagem foi enviada pelo usuário local, false se recebida
     */
    public void adicionarMensagemConversa(String contato, String mensagem, boolean enviada) {
        // Verifica se existe uma conversa com este contato
        if (!conversas.containsKey(contato)) {
            JTextPane novaConversa = new JTextPane();
            novaConversa.setEditable(false);
            novaConversa.setContentType("text/html");
            novaConversa.setText("<html><body style='font-family: Arial, sans-serif;'></body></html>");
            conversas.put(contato, novaConversa);
        }

        // Obtém a área de conversa
        JTextPane areaConversa = conversas.get(contato);

        // Formata a mensagem (estilo bolha de chat)
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        String horario = sdf.format(new Date());

        String alinhamento = enviada ? "right" : "left";
        String cor = enviada ? "#dcf8c6" : "#ffffff";
        String textoMensagem = mensagem.replace("\n", "<br>").replace(" ", "&nbsp;");

        // Verifica se é um link para arquivo
        if (mensagem.startsWith("Arquivo recebido:") || mensagem.startsWith("Arquivo enviado:")) {
            String nomeArquivo = mensagem.substring(mensagem.indexOf(":") + 1).trim();
            textoMensagem = "<strong>" + (mensagem.startsWith("Arquivo recebido") ? "📥" : "📤") +
                    " " + nomeArquivo + "</strong>";
        }

        String novaMensagem = String.format(
                "<div style='text-align: %s; margin: 5px;'>" +
                        "<div style='display: inline-block; background-color: %s; border-radius: 10px; " +
                        "padding: 8px; max-width: 70%%; text-align: left;'>" +
                        "%s<br><span style='font-size: 0.8em; color: gray;'>%s</span>" +
                        "</div></div>",
                alinhamento, cor, textoMensagem, horario);

        // Adiciona a mensagem ao histórico
        try {
            javax.swing.text.html.HTMLDocument doc = (javax.swing.text.html.HTMLDocument) areaConversa.getDocument();
            javax.swing.text.html.HTMLEditorKit kit = (javax.swing.text.html.HTMLEditorKit) areaConversa.getEditorKit();

            // Insere no final do body
            kit.insertHTML(doc, doc.getLength(), novaMensagem, 0, 0, null);

            // Scroll automático para o final
            areaConversa.setCaretPosition(doc.getLength());

        } catch (Exception e) {
            System.err.println("Erro ao adicionar mensagem: " + e.getMessage());
        }

        // Se o contato não é o atual, destaca na lista de contatos
        if (!contato.equals(contatoAtual)) {
            // Implementação da notificação visual na lista de contatos
            // (será feita pelo ContatoRenderer)
            listaContatos.repaint();
        }
    }

    /**
     * Processa mensagem recebida do servidor
     * @param mensagem Mensagem recebida
     * @return true se a mensagem foi processada pelo chat, false caso contrário
     */
    public boolean processarMensagem(String mensagem) {
        // Mensagem privada: MENSAGEM_PRIVADA:remetente:texto
        if (mensagem.startsWith("MENSAGEM_PRIVADA:")) {
            String[] partes = mensagem.split(":", 3);
            if (partes.length >= 3) {
                String remetente = partes[1];
                String texto = partes[2];
                adicionarMensagemConversa(remetente, texto, false);
                return true;
            }
        }
        // Lista de inspetores: LISTA_INSPETORES:inspetor1,inspetor2,...
        else if (mensagem.startsWith("LISTA_INSPETORES:")) {
            String[] partes = mensagem.split(":", 2);
            if (partes.length >= 2) {
                String[] inspetores = partes[1].split(",");
                List<String> listaInspetores = Arrays.asList(inspetores);
                atualizarListaContatos(listaInspetores);
                return true;
            }
        }
        // Notificação de arquivo: NOTIFICAR_ARQUIVO:remetente:nomeArquivo:tamanho
        else if (mensagem.startsWith("NOTIFICAR_ARQUIVO:")) {
            String[] partes = mensagem.split(":", 4);
            if (partes.length >= 4) {
                String remetente = partes[1];
                String nomeArquivo = partes[2];
                long tamanhoArquivo = Long.parseLong(partes[3]);

                // Pergunta se o usuário deseja aceitar o arquivo
                int resposta = JOptionPane.showConfirmDialog(janelaChat,
                        remetente + " deseja enviar o arquivo " + nomeArquivo +
                                " (" + (tamanhoArquivo / 1024) + " KB).\nDeseja aceitar?",
                        "Receber Arquivo", JOptionPane.YES_NO_OPTION);

                if (resposta == JOptionPane.YES_OPTION) {
                    // Solicita ao remetente que inicie a transferência
                    saida.println("ACEITAR_ARQUIVO:" + remetente + ":" + nomeArquivo);

                    // Escolhe onde salvar o arquivo
                    JFileChooser seletor = new JFileChooser();
                    seletor.setSelectedFile(new File(nomeArquivo));
                    seletor.setDialogTitle("Salvar arquivo recebido");

                    if (seletor.showSaveDialog(janelaChat) == JFileChooser.APPROVE_OPTION) {
                        File destino = seletor.getSelectedFile();

                        // Inicia recebimento em thread separada
                        new Thread(() -> {
                            try {
                                // Abre socket de transferência
                                ServerSocket serverSocket = new ServerSocket(9877);
                                Socket socketTransferencia = serverSocket.accept();

                                // Recebe o arquivo
                                File arquivoRecebido = TransferenciaArquivos.receberArquivo(
                                        socketTransferencia,
                                        destino.getParent());

                                // Renomeia para o nome escolhido pelo usuário
                                if (arquivoRecebido != null) {
                                    try {
                                        Files.move(
                                                arquivoRecebido.toPath(),
                                                destino.toPath(),
                                                StandardCopyOption.REPLACE_EXISTING);

                                        final String mensagemArquivo = "Arquivo recebido: " + destino.getName();
                                        SwingUtilities.invokeLater(() -> {
                                            adicionarMensagemConversa(remetente, mensagemArquivo, false);
                                        });
                                    } catch (IOException e) {
                                        System.err.println("Erro ao renomear arquivo: " + e.getMessage());
                                    }
                                }

                                socketTransferencia.close();
                                serverSocket.close();

                            } catch (Exception e) {
                                SwingUtilities.invokeLater(() -> {
                                    JOptionPane.showMessageDialog(janelaChat,
                                            "Erro ao receber arquivo: " + e.getMessage(),
                                            "Erro", JOptionPane.ERROR_MESSAGE);
                                });
                            }
                        }).start();
                    }
                } else {
                    // Recusa o arquivo
                    saida.println("RECUSAR_ARQUIVO:" + remetente + ":" + nomeArquivo);
                }

                return true;
            }
        }
        // Outras mensagens do protocolo de chat...

        // Se chegou aqui, não era uma mensagem para o chat
        return false;
    }

    /**
     * Exibe ou oculta a janela de chat
     */
    public void toggle() {
        if (janelaChat.isVisible()) {
            janelaChat.setVisible(false);
        } else {
            // Solicita lista atualizada de contatos antes de exibir
            solicitarListaContatos();
            janelaChat.setVisible(true);
        }
    }

    /**
     * Renderizador personalizado para exibir contatos com indicadores de mensagens não lidas
     */
    private class ContatoRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

            // Configura o label principal com o nome do contato
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            label.setBorder(BorderFactory.createEmptyBorder());

            // Remove o ícone e texto padrão
            label.setIcon(null);

            // Cria versão personalizada
            JLabel nomeLabel = new JLabel(value.toString());
            nomeLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));

            // Adiciona indicador de mensagem não lida (simplificado)
            String contato = value.toString();
            boolean temMensagemNaoLida = !contato.equals(contatoAtual) && conversas.containsKey(contato);

            if (temMensagemNaoLida) {
                nomeLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

                JPanel indicador = new JPanel();
                indicador.setPreferredSize(new Dimension(10, 10));
                indicador.setBackground(new Color(0, 150, 0));
                indicador.setBorder(new RoundBorder(10));

                panel.add(indicador, BorderLayout.EAST);
            }

            // Configura cores de fundo e primeiro plano
            if (isSelected) {
                panel.setBackground(label.getBackground());
                nomeLabel.setForeground(label.getForeground());
            } else {
                panel.setBackground(Color.WHITE);
                nomeLabel.setForeground(Color.BLACK);
            }

            panel.add(nomeLabel, BorderLayout.CENTER);
            return panel;
        }
    }

    /**
     * Alterna a visibilidade da janela de chat
     */
    public void toggle() {
        if (janela == null) {
            configurarInterface();
        }

        if (!janela.isVisible()) {
            janela.setVisible(true);
            solicitarListaInspetores(); // Atualiza a lista ao abrir
        } else {
            janela.setVisible(false);
        }
    }

    // Novo método para abrir o popup de emojis
    private void abrirPopupEmojis() {
        JDialog dialog = new JDialog(janela, "Emojis", true);
        dialog.setSize(400, 350);
        dialog.setLayout(new BorderLayout());

        // Categorias de emojis
        String[] categorias = {"Expressões", "Natureza", "Objetos", "Símbolos"};
        JComboBox<String> comboCategorias = new JComboBox<>(categorias);

        // Emojis por categoria
        Map<String, String[]> emojisPorCategoria = new HashMap<>();
        emojisPorCategoria.put("Expressões", new String[]{"😊", "😄", "😃", "😀", "😁", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕"});
        emojisPorCategoria.put("Natureza", new String[]{"🌱", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃", "🌺", "🌸", "🌼", "🌻", "🌞", "🌝", "🌛", "🌜", "🌚", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔", "🌙", "🌎", "🌍", "🌏", "💫", "⭐", "🌟", "✨", "⚡", "☄️", "💥", "🔥", "🌪", "🌈", "☀️", "🌤", "⛅", "🌥", "☁️", "🌦", "🌧", "⛈", "🌩", "🌨", "❄️", "☃️", "⛄", "🌬", "💨", "💧", "💦", "☔", "☂️", "🌊", "🌫"});
        emojisPorCategoria.put("Objetos", new String[]{"📱", "📲", "📟", "📠", "🔋", "🔌", "💻", "🖥", "🖨", "⌨️", "🖱", "🖲", "🕹", "🗜", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥", "📽", "🎞", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙", "🎚", "🎛", "🧭", "⏱", "⏲", "⏰", "🕰", "⌛️", "⏳", "📡", "🔋", "🔌", "💡", "🔦", "🕯", "🗑", "🛢", "💸", "💵", "💴", "💶", "💷", "🗃", "📦", "📫", "📪", "📬", "📭", "📮", "🗳", "✉️", "📩", "📨", "📧", "💌", "📥", "📤", "📦", "🏷", "🗳", "🛍", "🛒", "🎁", "🎈", "🎏", "🎀", "🎊", "🎉", "🎎", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧", "💌", "📥", "📤", "📦", "🏷", "🗳", "🛍", "🛒", "🎁", "🎈", "🎏", "🎀", "🎊", "🎉", "🎎", "🏮", "🎐", "🧧"});
        emojisPorCategoria.put("Símbolos", new String[]{"❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈️", "♉️", "♊️", "♋️", "♌️", "♍️", "♎️", "♏️", "♐️", "♑️", "♒️", "♓️", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳", "🈶", "🈚️", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️", "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️", "🆘", "❌", "⭕️", "🛑", "⛔️", "📛", "🚫", "💯", "💢", "♨️", "🚷", "🚯", "🚳", "🚱", "🔞", "📵", "🚭", "❗️", "❕", "❓", "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️", "🔰", "♻️", "✅", "🈯️", "💹", "❇️", "✳️", "❎", "🌐", "💠", "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿️", "🅿️", "🛗", "🛂", "🛃", "🛄", "🛅", "🚹", "🚺", "🚼", "🚻", "🚮", "🎦", "📶", "🈁", "🔣", "ℹ️", "🔤", "🔡", "🔠", "🆖", "🆗", "🆙", "🆒", "🆕", "🆓", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔟", "🔢", "#️⃣", "*️⃣", "⏏️", "▶️", "⏸", "⏹", "⏺", "⏭", "⏮", "⏩", "⏪", "⏫", "⏬", "◀️", "🔼", "🔽", "➡️", "⬅️", "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️", "↕️", "↔️", "↪️", "↩️", "⤴️", "⤵️", "🔀", "🔁", "🔂", "🔄", "🔃", "🎵", "🎶", "➕", "➖", "➗", "✖️", "💲", "💱", "™️", "©️", "®️", "👁‍🗨", "🔚", "🔙", "🔛", "🔝", "🔜", "〰️", "➰", "➿", "✔️", "☑️", "🔘", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫️", "⚪️", "🟤", "🔺", "🔻", "🔸", "🔹", "🔶", "🔷", "🔳", "🔲", "▪️", "▫️", "◾️", "◽️", "◼️", "◻️", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪", "⬛️", "⬜️", "🟫", "🔈", "🔇", "🔉", "🔊", "🔔", "🔕", "📣", "📢", "💬", "💭", "🗯", "♠️", "♣️", "♥️", "♦️", "🃏", "🎴", "🀄️", "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚", "🕛", "🕜", "🕝", "🕞", "🕟", "🕠", "🕡", "🕢", "🕣", "🕤", "🕥", "🕦", "🕧"});

        JPanel painelEmojis = new JPanel(new GridLayout(0, 8, 2, 2));
        JScrollPane scrollEmojis = new JScrollPane(painelEmojis);
        scrollEmojis.setPreferredSize(new Dimension(350, 200));

        // Atualiza emojis ao trocar categoria
        comboCategorias.addActionListener(e -> {
            String categoria = (String) comboCategorias.getSelectedItem();
            painelEmojis.removeAll();
            for (String emoji : emojisPorCategoria.get(categoria)) {
                JButton botaoEmoji = new JButton(emoji);
                botaoEmoji.setFont(new Font("Dialog", Font.PLAIN, 22));
                botaoEmoji.setFocusPainted(false);
                botaoEmoji.setBorderPainted(false);
                botaoEmoji.setContentAreaFilled(false);
                botaoEmoji.setMargin(new Insets(0, 0, 0, 0));
                botaoEmoji.addActionListener(ev -> {
                    campoMensagem.setText(campoMensagem.getText() + emoji);
                    dialog.dispose();
                });
                painelEmojis.add(botaoEmoji);
            }
            painelEmojis.revalidate();
            painelEmojis.repaint();
        });
        comboCategorias.setSelectedIndex(0);

        dialog.add(comboCategorias, BorderLayout.NORTH);
        dialog.add(scrollEmojis, BorderLayout.CENTER);
        dialog.setLocationRelativeTo(janela);
        dialog.setVisible(true);
    }
}
