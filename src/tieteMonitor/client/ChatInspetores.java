package tieteMonitor.client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.text.SimpleDateFormat;
import java.util.List;

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

        botaoEnviar = new JButton("Enviar");
        botaoEnviar.addActionListener(e -> enviarMensagem());

        painelMensagem.add(campoMensagem, BorderLayout.CENTER);
        painelMensagem.add(botaoEnviar, BorderLayout.EAST);

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
            // Formata: CHAT:PARA:destinatario:mensagem
            String destinatario = (String) comboInspetores.getSelectedItem();
            if (destinatario == null) destinatario = "Todos";

            saida.println("CHAT:PARA:" + destinatario + ":" + mensagem);

            // Adiciona mensagem na área de chat
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

        // Divide a string por vírgulas
        String[] inspetores = listaStr.split(",");
        for (String inspetor : inspetores) {
            if (!inspetor.trim().isEmpty()) {
                listaInspetores.add(inspetor.trim());
            }
        }

        atualizarListaInspetores();
    }

    /**
     * Processa mensagem recebida de outro inspetor
     *
     * @param dados Dados da mensagem no formato "remetente:mensagem"
     */
    private void processarMensagemRecebida(String dados) {
        // Extrai remetente e mensagem
        int separador = dados.indexOf(':');
        if (separador > 0) {
            String remetente = dados.substring(0, separador);
            String mensagem = dados.substring(separador + 1);

            // Adiciona mensagem na área de chat
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            String timestamp = sdf.format(new Date());

            // Se a janela não estiver aberta, abre automaticamente
            if (janela == null || !janela.isVisible()) {
                toggle();
            }

            // Adiciona a mensagem
            SwingUtilities.invokeLater(() -> {
                areaChat.append("[" + timestamp + "] " + remetente + ": " + mensagem + "\n");
                // Auto-scroll
                areaChat.setCaretPosition(areaChat.getDocument().getLength());
            });

            // Notifica o usuário
            if (!janela.isFocused()) {
                janela.toFront();
                janela.requestFocus();
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    /**
     * Adiciona um inspetor à lista
     *
     * @param inspetor Nome do inspetor a adicionar
     */
    private void adicionarInspetor(String inspetor) {
        // Verifica se já existe
        if (!listaInspetores.contains(inspetor)) {
            listaInspetores.add(inspetor);
            atualizarListaInspetores();

            // Notifica na área de chat, se aberta
            if (janela != null && janela.isVisible()) {
                areaChat.append("[Sistema] Inspetor conectado: " + inspetor + "\n");
            }
        }
    }

    /**
     * Remove um inspetor da lista
     *
     * @param inspetor Nome do inspetor a remover
     */
    private void removerInspetor(String inspetor) {
        listaInspetores.remove(inspetor);
        atualizarListaInspetores();

        // Notifica na área de chat, se aberta
        if (janela != null && janela.isVisible()) {
            areaChat.append("[Sistema] Inspetor desconectado: " + inspetor + "\n");
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
}