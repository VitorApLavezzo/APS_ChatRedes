package tieteMonitor.client;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import javax.swing.*;

/**
 * Componente de chat entre inspetores do Sistema de Monitoramento Ambiental do Rio Tietê
 * Permite comunicação direta entre inspetores em diferentes locais de monitoramento
 */
public class ChatInspetores {
    private ClienteMonitoramento clientePrincipal;
    private Socket socket;
    private PrintWriter saida;

    private JFrame janela;
    private JTextArea areaChat;
    private JTextField campoMensagem;
    private JComboBox<String> comboInspetores;
    private DefaultComboBoxModel<String> modeloInspetores;
    private JButton botaoEnviar;

    private List<String> listaInspetores;
    private String destinatarioAtual;

    /**
     * Construtor da classe ChatInspetores
     *
     * @param cliente Referência ao cliente principal de monitoramento
     * @param socket Socket de comunicação com o servidor
     * @param saida Canal de saída para o servidor
     */
    public ChatInspetores(ClienteMonitoramento cliente, Socket socket, PrintWriter saida) {
        this.clientePrincipal = cliente;
        this.socket = socket;
        this.saida = saida;
        this.listaInspetores = new ArrayList<>();

        // Configura a interface primeiro
        configurarInterface();

        // Solicita a lista de inspetores ao servidor
        solicitarListaInspetores();
    }

    /**
     * Solicita a lista de inspetores conectados ao servidor
     */
    private void solicitarListaInspetores() {
        if (saida != null) {
            saida.println("CHAT:LISTAR_INSPETORES");
        }
    }

    /**
     * Configura a interface gráfica do chat
     */
    private void configurarInterface() {
        // Configuração da janela
        janela = new JFrame("Chat entre Inspetores - " + clientePrincipal.getNomeInspetor());
        janela.setSize(500, 400);
        janela.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

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

        // Painel informativo
        JPanel painelInfo = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel labelInfo = new JLabel("Você: " + clientePrincipal.getNomeInspetor() +
                " (" + clientePrincipal.getLocalMonitorado() + ")");
        painelInfo.add(labelInfo);

        // Configuração do layout
        JPanel painelSul = new JPanel(new BorderLayout());
        painelSul.add(painelDestinatario, BorderLayout.NORTH);
        painelSul.add(painelMensagem, BorderLayout.CENTER);
        painelSul.add(painelInfo, BorderLayout.SOUTH);

        janela.getContentPane().add(scrollPane, BorderLayout.CENTER);
        janela.getContentPane().add(painelSul, BorderLayout.SOUTH);

        // Posiciona a janela
        janela.setLocationRelativeTo(clientePrincipal.getFrame());

        // Adiciona "Todos" como opção padrão
        modeloInspetores.addElement("Todos");
        destinatarioAtual = "Todos";

        // Adiciona os inspetores já conhecidos
        atualizarListaInspetores();
    }

    /**
     * Atualiza a lista de inspetores no combobox
     */
    private void atualizarListaInspetores() {
        SwingUtilities.invokeLater(() -> {
            if (comboInspetores == null) {
                return; // Retorna se o combo ainda não foi inicializado
            }
            
            // Guarda a seleção atual
            String selecaoAtual = (String) comboInspetores.getSelectedItem();

            // Limpa o modelo, mas mantém "Todos"
            while (modeloInspetores.getSize() > 1) {
                modeloInspetores.removeElementAt(1);
            }

            // Adiciona a lista atualizada
            for (String inspetor : listaInspetores) {
                // Verifica se não é o próprio inspetor
                if (!inspetor.equals(clientePrincipal.getNomeInspetor())) {
                    modeloInspetores.addElement(inspetor);
                }
            }

            // Restaura a seleção se possível
            if (selecaoAtual != null && modeloInspetores.getIndexOf(selecaoAtual) >= 0) {
                comboInspetores.setSelectedItem(selecaoAtual);
            } else {
                comboInspetores.setSelectedIndex(0); // Seleciona "Todos" por padrão
                destinatarioAtual = "Todos";
            }
        });
    }

    /**
     * Envia uma mensagem para o destinatário selecionado
     */
    private void enviarMensagem() {
        String mensagem = campoMensagem.getText().trim();
        if (!mensagem.isEmpty() && saida != null) {
            // Obtém o destinatário selecionado de forma segura
            String destinatario = (String) comboInspetores.getSelectedItem();
            
            // Remove explicitamente o ':' inicial se existir
            if (destinatario != null && destinatario.startsWith(":")) {
                destinatario = destinatario.substring(1);
            }

            if (destinatario == null || destinatario.trim().isEmpty()) {
                destinatario = "Todos"; // Garante que sempre haja um destinatário válido
            }

            // Formata a mensagem
            String mensagemParaEnviar = "CHAT:PARA:" + destinatario + ":" + mensagem;
            
            // Log de depuração no cliente
            System.out.println("DEBUG CLIENTE: Enviando para o servidor: " + mensagemParaEnviar);

            // Envia a mensagem
            saida.println(mensagemParaEnviar);

            // Adiciona mensagem na área de chat (localmente)
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String timestamp = sdf.format(new Date());

            String destinatarioTexto = destinatario.equals("Todos") ? "Todos" : "Privado para " + destinatario;
            areaChat.append("[" + timestamp + "] (Para " + destinatarioTexto + ") Você: " + mensagem + "\n");

            // Limpa o campo de mensagem
            campoMensagem.setText("");
        }
    }

    /**
     * Processa mensagens recebidas do servidor
     *
     * @param mensagem A mensagem recebida
     * @return true se a mensagem foi processada, false caso contrário
     */
    public boolean processarMensagem(String mensagem) {
        // Verifica se é uma mensagem relacionada ao chat
        if (mensagem.startsWith("CHAT:")) {
            // Remove o prefixo
            String conteudo = mensagem.substring(5);

            // Processa diferentes tipos de mensagens de chat
            if (conteudo.startsWith("LISTA_INSPETORES:")) {
                // Atualiza lista de inspetores
                processarListaInspetores(conteudo.substring(16));
                return true;
            } else if (conteudo.startsWith("MSG_DE:")) {
                // Processa mensagem recebida
                processarMensagemRecebida(conteudo.substring(7));
                return true;
            } else if (conteudo.startsWith("CONECTADO:")) {
                // Inspetor conectado
                String novoInspetor = conteudo.substring(10);
                adicionarInspetor(novoInspetor);
                return true;
            } else if (conteudo.startsWith("DESCONECTADO:")) {
                // Inspetor desconectado
                String inspetorSaiu = conteudo.substring(13);
                removerInspetor(inspetorSaiu);
                return true;
            }
        }

        // Não é uma mensagem de chat
        return false;
    }

    /**
     * Processa lista de inspetores recebida do servidor
     *
     * @param listaStr Lista de inspetores separada por vírgulas
     */
    private void processarListaInspetores(String listaStr) {
        listaInspetores.clear();

        // Divide a string por vírgulas e filtra/limpa entradas
        if (listaStr != null && !listaStr.trim().isEmpty()) {
            String[] inspetores = listaStr.split(",");
            for (String inspetor : inspetores) {
                // Limpeza rigorosa: remove espaços em branco e caracteres não visíveis
                String nomeLimpo = inspetor.trim().replaceAll("[^\\p{Print}\\p{Space}]", "").trim();
                
                if (!nomeLimpo.isEmpty() && !nomeLimpo.equals(clientePrincipal.getNomeInspetor())) {
                    listaInspetores.add(nomeLimpo);
                }
            }
        }

        // Ordena a lista de inspetores alfabeticamente
        Collections.sort(listaInspetores);

        atualizarListaInspetores();
    }

    /**
     * Processa mensagem recebida de outro inspetor
     *
     * @param dados Dados da mensagem no formato "remetente:mensagem" (ou pode incluir [PRIVADO])
     */
    private void processarMensagemRecebida(String dados) {
        System.out.println("DEBUG CHAT CLIENTE: Processando mensagem recebida: " + dados); // Log de depuração
        
        String[] partes = dados.split(":", 2);
        if (partes.length == 2) {
            String remetente = partes[0];
            String mensagem = partes[1];
            
            // Verifica se é uma mensagem privada
            boolean isPrivada = mensagem.endsWith(" [PRIVADO]");
            if (isPrivada) {
                mensagem = mensagem.substring(0, mensagem.length() - 10); // Remove o [PRIVADO]
            }
            
            String mensagemFormatada = String.format("[%s] %s: %s", 
                new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()),
                remetente,
                mensagem);
            
            if (isPrivada) {
                mensagemFormatada = "[PRIVADO] " + mensagemFormatada;
            }
            
            areaChat.append(mensagemFormatada + "\n");
            areaChat.setCaretPosition(areaChat.getDocument().getLength());
        }
    }

    /**
     * Adiciona um inspetor à lista e atualiza a interface
     *
     * @param inspetor O nome do inspetor a ser adicionado
     */
    private void adicionarInspetor(String inspetor) {
        // Verifica se já existe
        if (!listaInspetores.contains(inspetor)) {
            listaInspetores.add(inspetor);
            atualizarListaInspetores();

            // Notifica na área de chat, se aberta
            if (janela != null && janela.isVisible()) {
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String timestamp = sdf.format(new Date());
                areaChat.append("[" + timestamp + "] [Sistema] Inspetor conectado: " + inspetor + "\n");
            }
        }
    }

    /**
     * Remove um inspetor da lista e atualiza a interface
     *
     * @param inspetor O nome do inspetor a ser removido
     */
    private void removerInspetor(String inspetor) {
        listaInspetores.remove(inspetor);
        atualizarListaInspetores();

        // Notifica na área de chat, se aberta
        if (janela != null && janela.isVisible()) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String timestamp = sdf.format(new Date());
            areaChat.append("[" + timestamp + "] [Sistema] Inspetor desconectado: " + inspetor + "\n");
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

    /**
     * Mostra a janela do chat
     */
    public void mostrar() {
        if (janela == null) {
            configurarInterface();
        }
        janela.setVisible(true);
        solicitarListaInspetores(); // Atualiza a lista ao abrir
    }
}