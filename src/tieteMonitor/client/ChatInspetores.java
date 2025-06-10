package tieteMonitor.client;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.ArrayList;
import javax.swing.*;

/**
 * Componente de chat entre inspetores do Sistema de Monitoramento Ambiental do Rio Tietê
 * Permite comunicação direta entre inspetores em diferentes locais de monitoramento
 */
public class ChatInspetores {
    private ClienteMonitoramento clientePrincipal;
    private Socket socket;
    private DataOutputStream dataOut;
    private DataInputStream dataIn;
    private JFrame janela;
    private JTextArea areaChat;
    private JTextField campoMensagem;
    private JComboBox<String> comboInspetores;
    private DefaultComboBoxModel<String> modeloInspetores;
    private JButton botaoEnviar;
    private List<String> listaInspetores;
    private String destinatarioAtual;

    /**
     * @param cliente Referência ao cliente principal de monitoramento
     * @param socket Socket de comunicação com o servidor
     * @param dataOut Canal de saída para o servidor
     */
    public ChatInspetores(ClienteMonitoramento cliente, Socket socket, DataOutputStream dataOut) throws IOException {
        this.clientePrincipal = cliente;
        this.socket = socket;
        this.dataOut = dataOut;
        this.dataIn = new DataInputStream(socket.getInputStream());
        this.listaInspetores = new ArrayList<>();
        configurarInterface();
        solicitarListaInspetores();
    }

    private void solicitarListaInspetores() {
        if (dataOut != null) {
            try {
                dataOut.writeUTF("CHAT:LISTAR_INSPETORES");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void configurarInterface() {
        janela = new JFrame("Chat entre Inspetores - " + clientePrincipal.getNomeInspetor());
        janela.setSize(500, 400);
        janela.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        areaChat = new JTextArea();
        areaChat.setEditable(false);
        areaChat.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(areaChat);
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
        JPanel painelMensagem = new JPanel(new BorderLayout());
        campoMensagem = new JTextField();
        campoMensagem.addActionListener(e -> enviarMensagem());
        JButton botaoEmoji = new JButton("😊");
        botaoEmoji.setFont(new Font("Dialog", Font.PLAIN, 18));
        botaoEmoji.setFocusable(false);
        botaoEmoji.setMargin(new Insets(2, 6, 2, 6));
        botaoEmoji.addActionListener(e -> abrirPopupEmojis());
        botaoEnviar = new JButton("Enviar");
        botaoEnviar.addActionListener(e -> enviarMensagem());
        JPanel painelCampo = new JPanel(new BorderLayout());
        painelCampo.add(campoMensagem, BorderLayout.CENTER);
        painelCampo.add(botaoEmoji, BorderLayout.WEST);
        painelCampo.add(botaoEnviar, BorderLayout.EAST);
        painelMensagem.add(painelCampo, BorderLayout.CENTER);
        JPanel painelInfo = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JLabel labelInfo = new JLabel("Você: " + clientePrincipal.getNomeInspetor() +
                " (" + clientePrincipal.getLocalMonitorado() + ")");
        painelInfo.add(labelInfo);
        JPanel painelSul = new JPanel(new BorderLayout());
        painelSul.add(painelDestinatario, BorderLayout.NORTH);
        painelSul.add(painelMensagem, BorderLayout.CENTER);
        painelSul.add(painelInfo, BorderLayout.SOUTH);
        janela.getContentPane().add(scrollPane, BorderLayout.CENTER);
        janela.getContentPane().add(painelSul, BorderLayout.SOUTH);
        janela.setLocationRelativeTo(clientePrincipal.getFrame());
        modeloInspetores.addElement("Todos");
        destinatarioAtual = "Todos";
        atualizarListaInspetores();
    }

    private void atualizarListaInspetores() {
        SwingUtilities.invokeLater(() -> {
            if (comboInspetores == null) {
                return;
            }
            System.out.println("DEBUG CHAT CLIENTE: Atualizando lista UI. Lista interna: " + this.listaInspetores);
            String selecaoAtual = (String) comboInspetores.getSelectedItem();
            modeloInspetores.removeAllElements();
            modeloInspetores.addElement("Todos");
            for (String inspetor : this.listaInspetores) {
                String nomeLimpo = inspetor.trim();
                if (!nomeLimpo.isEmpty() && !nomeLimpo.equals(clientePrincipal.getNomeInspetor())) {
                    modeloInspetores.addElement(nomeLimpo);
                }
            }
            if (selecaoAtual != null && modeloInspetores.getIndexOf(selecaoAtual) >= 0) {
                comboInspetores.setSelectedItem(selecaoAtual);
            } else {
                comboInspetores.setSelectedIndex(0);
                destinatarioAtual = "Todos";
            }
        });
    }

    private void enviarMensagem() {
        String mensagem = campoMensagem.getText().trim();
        if (!mensagem.isEmpty() && dataOut != null) {
            String destinatario = (String) comboInspetores.getSelectedItem();
            if (destinatario != null && destinatario.startsWith(":")) {
                destinatario = destinatario.substring(1);
            }
            if (destinatario == null || destinatario.trim().isEmpty()) {
                destinatario = "Todos";
            }
            String mensagemParaEnviar = "CHAT:PARA:" + destinatario + ":" + mensagem;
            System.out.println("DEBUG CLIENTE: Enviando para o servidor: " + mensagemParaEnviar);
            try {
                dataOut.writeUTF(mensagemParaEnviar);
            } catch (IOException e) {
                e.printStackTrace();
            }
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String timestamp = sdf.format(new Date());
            String destinatarioTexto = destinatario.equals("Todos") ? "Todos" : "Privado para " + destinatario;
            areaChat.append("[" + timestamp + "] (" + destinatarioTexto + ") Você: " + mensagem + "\n");
            campoMensagem.setText("");
        }
    }

    /**
     * @param mensagem A mensagem recebida
     * @return true se a mensagem foi processada, false caso contrário
     */
    public boolean processarMensagem(String mensagem) {
        String conteudo = mensagem;
        System.out.println("DEBUG CHAT CLIENTE: Processando mensagem: " + conteudo);
        if (conteudo.startsWith("LISTA_INSPETORES:")) {
            String listaStr = conteudo.substring("LISTA_INSPETORES:".length());
            List<String> listaInspetoresRecebida = parseListaInspetores(listaStr);
            SwingUtilities.invokeLater(() -> {
                 this.listaInspetores.clear();
                 for (String inspetor : listaInspetoresRecebida) {
                     String nomeLimpo = inspetor.trim();
                     if (!nomeLimpo.isEmpty()) {
                         this.listaInspetores.add(nomeLimpo);
                     }
                 }
                 Collections.sort(this.listaInspetores);
                 this.atualizarListaInspetores();
            });
            return true;
        } else if (conteudo.startsWith("MSG_DE:")) {
            String dadosMensagem = conteudo.substring("MSG_DE:".length());
            processarMensagemRecebida(dadosMensagem);
            return true;
        } else if (conteudo.startsWith("CONECTADO:")) {
            String novoInspetor = conteudo.substring("CONECTADO:".length());
            adicionarInspetor(novoInspetor);
            return true;
        } else if (conteudo.startsWith("DESCONECTADO:")) {
            String inspetorSaiu = conteudo.substring("DESCONECTADO:".length());
            removerInspetor(inspetorSaiu);
            return true;
        }
        return false;
    }

    private List<String> parseListaInspetores(String listaStr) {
        List<String> lista = new ArrayList<>();
        if (listaStr != null && !listaStr.trim().isEmpty()) {
            String[] inspetores = listaStr.split(",");
            for (String inspetor : inspetores) {
                String nomeLimpo = inspetor.trim();
                if (!nomeLimpo.isEmpty()) {
                    lista.add(nomeLimpo);
                }
            }
        }
        return lista;
    }
    /**
     * Processa mensagem recebida de outro inspetor
     * @param dados Dados da mensagem no formato "remetente:mensagem" (ou pode incluir [PRIVADO])
     */
    private void processarMensagemRecebida(String dados) {
        System.out.println("DEBUG CHAT CLIENTE: Processando mensagem recebida: " + dados);
        
        String[] partes = dados.split(":", 2);
        if (partes.length == 2) {
            String remetente = partes[0];
            String mensagem = partes[1];
            boolean isPrivada = mensagem.endsWith(" [PRIVADO]");
            if (isPrivada) {
                mensagem = mensagem.substring(0, mensagem.length() - 10);
            }
            String mensagemFormatada;
            if (isPrivada) {
                mensagemFormatada = String.format("[%s] (Privado para %s) %s: %s", 
                    new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()),
                    remetente.equals(clientePrincipal.getNomeInspetor()) ? "Você" : remetente,
                    remetente.equals(clientePrincipal.getNomeInspetor()) ? "Você" : remetente,
                    mensagem);
            } else {
                mensagemFormatada = String.format("[%s] %s: %s", 
                    new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()),
                    remetente,
                    mensagem);
            }
            
            areaChat.append(mensagemFormatada + "\n");
            areaChat.setCaretPosition(areaChat.getDocument().getLength());
        }
    }
    /**
     * Adiciona um inspetor à lista e atualiza a interface
     * @param inspetor O nome do inspetor a ser adicionado
     */
    private void adicionarInspetor(String inspetor) {
        SwingUtilities.invokeLater(() -> {
            String nomeLimpo = inspetor.trim();
            if (!nomeLimpo.isEmpty() && !this.listaInspetores.contains(nomeLimpo)) {
                this.listaInspetores.add(nomeLimpo);
                Collections.sort(this.listaInspetores);
                this.atualizarListaInspetores();
                 if (janela != null && janela.isVisible()) {
                     areaChat.append("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] [Sistema] Inspetor conectado: " + nomeLimpo + "\n");
                 }
            }
        });
    }

    /**
     * Remove um inspetor da lista e atualiza a interface
     *
     * @param inspetor
     */
    private void removerInspetor(String inspetor) {
        SwingUtilities.invokeLater(() -> {
             String nomeLimpo = inspetor.trim();
            if (this.listaInspetores.remove(nomeLimpo)) {
                this.atualizarListaInspetores();
                 if (janela != null && janela.isVisible()) {
                     areaChat.append("[" + new SimpleDateFormat("HH:mm:ss").format(new Date()) + "] [Sistema] Inspetor desconectado: " + nomeLimpo + "\n");
                 }
            }
        });
    }

    public void toggle() {
        if (janela == null) {
            configurarInterface();
        }
        if (!janela.isVisible()) {
            janela.setVisible(true);
            solicitarListaInspetores();
        } else {
            janela.setVisible(false);
        }
    }

    private void abrirPopupEmojis() {
        JDialog dialog = new JDialog(janela, "Emojis", true);
        dialog.setSize(400, 350);
        dialog.setLayout(new BorderLayout());
        String[] categorias = {"Expressões", "Natureza", "Objetos", "Símbolos"};
        JComboBox<String> comboCategorias = new JComboBox<>(categorias);
        Map<String, String[]> emojisPorCategoria = new HashMap<>();
        emojisPorCategoria.put("Expressões", new String[]{"😊", "😄", "😃", "😀", "😁", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕"});
        emojisPorCategoria.put("Natureza", new String[]{"🌱", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁", "🍂", "🍃", "🌺", "🌸", "🌼", "🌻", "🌞", "🌝", "🌛", "🌜", "🌚", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔", "🌙", "🌎", "🌍", "🌏", "💫", "⭐", "🌟", "✨", "⚡", "☄️", "💥", "🔥", "🌪", "🌈", "☀️", "🌤", "⛅", "🌥", "☁️", "🌦", "🌧", "⛈", "🌩", "🌨", "❄️", "☃️", "⛄", "🌬", "💨", "💧", "💦", "☔", "☂️", "🌊", "🌫"});
        emojisPorCategoria.put("Objetos", new String[]{"📱", "📲", "📟", "📠", "🔋", "🔌", "💻", "🖥", "🖨", "⌨️", "🖱", "🖲", "🕹", "🗜", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥", "📽", "🎞", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙", "🎚", "🎛", "🧭", "⏱", "⏲", "⏰", "🕰", "⌛️", "⏳", "📡", "🔋", "🔌", "💡", "🔦", "🕯", "🗑", "🛢", "💸", "💵", "💴", "💶", "💷", "🗃", "📦", "📫", "📪", "📬", "📭", "📮", "🗳", "✉️", "📩", "📨", "📧", "💌", "📥", "📤", "📦", "🏷", "🗳", "🛍", "🛒", "🎁", "🎈", "🎏", "🎀", "🎊", "🎉", "🎎", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧", "💌", "📥", "📤", "📦", "🏷", "🗳", "🛍", "🛒", "🎁", "🎈", "🎏", "🎀", "🎊", "🎉", "🎎", "🏮", "🎐", "🧧"});
        emojisPorCategoria.put("Símbolos", new String[]{"❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈️", "♉️", "♊️", "♋️", "♌️", "♍️", "♎️", "♏️", "♐️", "♑️", "♒️", "♓️", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳", "🈶", "🈚️", "🈸", "🈺", "🈷️", "✴️", "🆚", "💮", "🉐", "㊙️", "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆎", "🆑", "🅾️", "🆘", "❌", "⭕️", "🛑", "⛔️", "📛", "🚫", "💯", "💢", "♨️", "🚷", "🚯", "🚳", "🚱", "🔞", "📵", "🚭", "❗️", "❕", "❓", "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️", "🔰", "♻️", "✅", "🈯️", "💹", "❇️", "✳️", "❎", "🌐", "💠", "Ⓜ️", "🌀", "💤", "🏧", "🚾", "♿️", "🅿️", "🛗", "🛂", "🛃", "🛄", "🛅", "🚹", "🚺", "🚼", "🚻", "🚮", "🎦", "📶", "🈁", "🔣", "ℹ️", "🔤", "🔡", "🔠", "🆖", "🆗", "🆙", "🆒", "🆕", "🆓", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣", "🔢", "#️⃣", "*️⃣", "⏏️", "▶️", "⏸", "⏹", "⏺", "⏭", "⏮", "⏩", "⏪", "⏫", "⏬", "◀️", "🔼", "🔽", "➡️", "⬅️", "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️", "↕️", "↔️", "↪️", "↩️", "⤴️", "⤵️", "🔀", "🔁", "🔂", "🔄", "🔃", "🎵", "🎶", "➕", "➖", "➗", "✖️", "💲", "💱", "™️", "©️", "®️", "👁‍🗨", "🔚", "🔙", "🔛", "🔝", "🔜", "〰️", "➰", "➿", "✔️", "☑️", "🔘", "🔴", "🟠", "🟡", "🟢", "🔵", "🟣", "⚫️", "⚪️", "🟤", "🔺", "🔻", "🔸", "🔹", "🔶", "🔷", "🔳", "🔲", "▪️", "▫️", "◾️", "◽️", "◼️", "◻️", "🟥", "🟧", "🟨", "🟩", "🟦", "🟪", "⬛️", "⬜️", "🟫", "🔈", "🔇", "🔉", "🔊", "🔔", "🔕", "📣", "📢", "💬", "💭", "🗯", "♠️", "♣️", "♥️", "♦️", "🃏", "🎴", "🀄️", "🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚", "🕛", "🕜", "🕝", "🕞", "🕟", "🕠", "🕡", "🕢", "🕣", "🕤", "🕥", "🕦", "🕧"});
        JPanel painelEmojis = new JPanel(new GridLayout(0, 8, 2, 2));
        JScrollPane scrollEmojis = new JScrollPane(painelEmojis);
        scrollEmojis.setPreferredSize(new Dimension(350, 200));
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

    public void mostrar() {
        if (janela == null) {
            configurarInterface();
        }
        janela.setVisible(true);
        solicitarListaInspetores();
    }

    /**
     * Retorna a lista de inspetores conectados
     * @return Lista de nomes dos inspetores
     */
    public List<String> getListaInspetores() {
        return new ArrayList<>(this.listaInspetores);
    }
}