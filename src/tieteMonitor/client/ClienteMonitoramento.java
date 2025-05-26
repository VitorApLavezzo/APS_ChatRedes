package tieteMonitor.client;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import tieteMonitor.util.TransferenciaArquivos;
import tieteMonitor.util.MulticastManager;
import tieteMonitor.util.EmailSender;
import java.util.List;
import java.util.ArrayList;

/**
 * Cliente para Sistema de Monitoramento Ambiental do Rio Tietê
 * Permite que inspetores se comuniquem com a central e entre si
 */
public class ClienteMonitoramento {
    private String SERVIDOR_IP;
    private int SERVIDOR_PORTA;

    private Socket socket;
    private DataOutputStream dataOut;
    private DataInputStream dataIn;
    private String nomeInspetor;
    private String localMonitorado;

    // Componentes da interface gráfica
    private JFrame frame;
    private JTextArea areaMensagens;
    private JTextField campoMensagem;
    private JButton botaoEnviar;
    private JButton botaoRelatorio;
    private JButton botaoAlerta;
    private JComboBox<String> comboEmoticons;
    private JPanel painelStatus;
    private JLabel labelStatus;
    private JButton botaoEmoticons;
    private JButton botaoEmail;
    private MulticastManager multicastManager;

    // Componente de chat entre inspetores
    private ChatInspetores chatInspetores;
    
    // ADICIONAR UMA LISTA DE INSPETORES NO CLIENTE PRINCIPAL
    private List<String> inspetoresConectados = new ArrayList<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ClienteMonitoramento().iniciar();
        });
    }

    public void iniciar() {
        // Solicita endereço do servidor
        solicitarEnderecoServidor();

        // Coleta informações do inspetor
        solicitarInformacoes();

        // Configura a interface gráfica
        configurarInterface();

        // Conecta ao servidor
        conectarServidor();
    }

    private void solicitarEnderecoServidor() {
        String endereco = JOptionPane.showInputDialog(null,
                "Digite o endereço do servidor (ex: 0.tcp.sa.ngrok.io:12345):",
                "Conexão com Servidor",
                JOptionPane.QUESTION_MESSAGE);

        if (endereco == null || endereco.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Endereço não fornecido. O programa será encerrado.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        try {
            String[] partes = endereco.split(":");
            SERVIDOR_IP = partes[0];
            SERVIDOR_PORTA = Integer.parseInt(partes[1]);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Formato de endereço inválido. Use o formato: endereco:porta",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }
    }

    private void solicitarInformacoes() {
        // Solicita nome do inspetor
        nomeInspetor = JOptionPane.showInputDialog(null,
                "Digite seu nome completo:",
                "Identificação do Inspetor",
                JOptionPane.QUESTION_MESSAGE);

        if (nomeInspetor == null || nomeInspetor.trim().isEmpty()) {
            JOptionPane.showMessageDialog(null,
                    "Nome não fornecido. O programa será encerrado.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        // Solicita local de monitoramento
        String[] locais = {
                "Salesópolis (Nascente)",
                "Mogi das Cruzes",
                "Suzano",
                "Poá",
                "Itaquaquecetuba",
                "Guarulhos",
                "São Paulo (Capital)",
                "Outro local"
        };

        String localSelecionado = (String) JOptionPane.showInputDialog(null,
                "Selecione o local de monitoramento:",
                "Local de Trabalho",
                JOptionPane.QUESTION_MESSAGE,
                null,
                locais,
                locais[0]);

        if (localSelecionado == null) {
            JOptionPane.showMessageDialog(null,
                    "Local não selecionado. O programa será encerrado.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            System.exit(0);
        }

        if (localSelecionado.equals("Outro local")) {
            localMonitorado = JOptionPane.showInputDialog(null,
                    "Digite o nome do local de monitoramento:",
                    "Local Personalizado",
                    JOptionPane.QUESTION_MESSAGE);

            if (localMonitorado == null || localMonitorado.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null,
                        "Local não fornecido. O programa será encerrado.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                System.exit(0);
            }
        } else {
            // Extrai apenas o nome da cidade
            localMonitorado = localSelecionado.split(" \\(")[0];
        }
    }

    private void configurarInterface() {
        frame = new JFrame("Monitor Ambiental - Rio Tietê");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 800);
    
        try {
            // Tema moderno FlatLaf
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarkLaf");
            SwingUtilities.updateComponentTreeUI(frame);
        } catch (Exception e) {
            try {
                // Fallback para Nimbus
                UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
                SwingUtilities.updateComponentTreeUI(frame);
            } catch (Exception ex) {
                // Se não conseguir aplicar o Nimbus, segue com o padrão
            }
        }
        
        // Área de mensagens com estilo melhorado
        areaMensagens = new JTextArea();
        areaMensagens.setEditable(false);
        areaMensagens.setFont(new Font("SansSerif", Font.PLAIN, 14));
        areaMensagens.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(180, 180, 180)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        JScrollPane scrollPane = new JScrollPane(areaMensagens);
    
        // Painel de entrada de mensagem
        JPanel painelEntrada = new JPanel(new BorderLayout(5, 5));
        campoMensagem = new JTextField();
        campoMensagem.setFont(new Font("SansSerif", Font.PLAIN, 14));
        campoMensagem.addActionListener(e -> enviarMensagem());
        
        // Botão de emoticons
        botaoEmoticons = new JButton("😊");
        botaoEmoticons.setFont(new Font("Dialog", Font.PLAIN, 18));
        botaoEmoticons.setFocusable(false);
        botaoEmoticons.setMargin(new Insets(2, 6, 2, 6));
        botaoEmoticons.addActionListener(e -> abrirSeletorEmoticons());
        painelEntrada.add(botaoEmoticons, BorderLayout.WEST);
        
        botaoEnviar = new JButton("Enviar");
        botaoEnviar.setIcon(new ImageIcon(new ImageIcon("src/resources/send.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        botaoEnviar.setPreferredSize(new Dimension(100, 30));
        botaoEnviar.addActionListener(e -> enviarMensagem());
        painelEntrada.add(campoMensagem, BorderLayout.CENTER);
        painelEntrada.add(botaoEnviar, BorderLayout.EAST);

        // Painel de botões
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        botaoRelatorio = new JButton("Enviar Relatório");
        botaoRelatorio.setIcon(new ImageIcon(new ImageIcon("src/resources/report.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        botaoRelatorio.setPreferredSize(new Dimension(150, 30));
        botaoRelatorio.addActionListener(e -> abrirJanelaRelatorio());
        painelBotoes.add(botaoRelatorio);

        botaoAlerta = new JButton("Alerta Ambiental");
        botaoAlerta.setBackground(new Color(255, 100, 100));
        botaoAlerta.setIcon(new ImageIcon(new ImageIcon("src/resources/alert.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        botaoAlerta.setPreferredSize(new Dimension(150, 30));
        botaoAlerta.addActionListener(e -> abrirJanelaAlerta());
        painelBotoes.add(botaoAlerta);

        botaoEmail = new JButton("Enviar E-mail");
        botaoEmail.setIcon(new ImageIcon(new ImageIcon("src/resources/email.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        botaoEmail.setPreferredSize(new Dimension(150, 30));
        botaoEmail.addActionListener(e -> enviarRelatorioEmail());
        painelBotoes.add(botaoEmail);

        JButton botaoArquivos = new JButton("Arquivos Recebidos");
        botaoArquivos.setIcon(new ImageIcon(new ImageIcon("src/resources/files.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        botaoArquivos.setPreferredSize(new Dimension(150, 30));
        botaoArquivos.addActionListener(e -> abrirListaArquivos());
        painelBotoes.add(botaoArquivos);

        JButton botaoChat = new JButton("Chat Inspetores");
        botaoChat.setIcon(new ImageIcon(new ImageIcon("src/resources/chat.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        botaoChat.setPreferredSize(new Dimension(150, 30));
        botaoChat.addActionListener(e -> abrirChatInspetores());
        painelBotoes.add(botaoChat);

        JButton botaoEnviarArquivo = new JButton("Enviar Arquivo");
        botaoEnviarArquivo.setIcon(new ImageIcon(new ImageIcon("src/resources/file.png").getImage().getScaledInstance(16, 16, Image.SCALE_SMOOTH)));
        botaoEnviarArquivo.setPreferredSize(new Dimension(150, 30));
        botaoEnviarArquivo.addActionListener(e -> iniciarEnvioArquivo());
        painelBotoes.add(botaoEnviarArquivo);

        // Barra de menu
        JMenuBar menuBar = new JMenuBar();
        JMenu menuArquivo = new JMenu("Arquivo");
        JMenuItem itemSair = new JMenuItem("Sair");
        itemSair.addActionListener(e -> frame.dispose());
        menuArquivo.add(itemSair);
        menuBar.add(menuArquivo);

        JMenu menuAjuda = new JMenu("Ajuda");
        JMenuItem itemSobre = new JMenuItem("Sobre");
        itemSobre.addActionListener(e -> JOptionPane.showMessageDialog(frame, "Sistema de Monitoramento Ambiental do Rio Tietê\nVersão 1.0", "Sobre", JOptionPane.INFORMATION_MESSAGE));
        menuAjuda.add(itemSobre);
        menuBar.add(menuAjuda);

        frame.setJMenuBar(menuBar);

        // Painel de status
        painelStatus = new JPanel(new BorderLayout());
        painelStatus.setBorder(BorderFactory.createEtchedBorder());
        labelStatus = new JLabel("Desconectado");
        labelStatus.setForeground(Color.RED);
        painelStatus.add(labelStatus, BorderLayout.WEST);

        JLabel labelInfo = new JLabel("Inspetor: " + nomeInspetor + " | Local: " + localMonitorado);
        painelStatus.add(labelInfo, BorderLayout.EAST);

        // Painel principal da interface
        JPanel painelPrincipal = new JPanel(new BorderLayout());
        painelPrincipal.add(scrollPane, BorderLayout.CENTER);
        painelPrincipal.add(painelEntrada, BorderLayout.SOUTH);
        painelPrincipal.add(painelBotoes, BorderLayout.NORTH);
        painelPrincipal.add(painelStatus, BorderLayout.PAGE_END);

        frame.add(painelPrincipal);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Adiciona listener para fechamento da janela
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                desconectar();
            }
        });
    }

    private void conectarServidor() {
        try {
            socket = new Socket(SERVIDOR_IP, SERVIDOR_PORTA);
            dataOut = new DataOutputStream(socket.getOutputStream());
            dataIn = new DataInputStream(socket.getInputStream());
            dataOut.writeUTF(nomeInspetor);
            dataOut.writeUTF(localMonitorado);
    
            // Aguarda as duas mensagens de boas-vindas do servidor
            String msg1 = dataIn.readUTF();
            String msg2 = dataIn.readUTF();
            // Agora pode liberar o chat
    
            chatInspetores = new ChatInspetores(this, socket, dataOut);
    
            // Inicializa o gerenciador multicast
            multicastManager = new MulticastManager(mensagem -> {
                if (mensagem.startsWith("ALERTA_MULTICAST:")) {
                    String conteudoAlerta = mensagem.substring("ALERTA_MULTICAST:".length());
                    SwingUtilities.invokeLater(() -> adicionarAlerta("[MULTICAST] " + conteudoAlerta));
                }
            });
            multicastManager.iniciarRecepcao();
    
            // Atualiza status
            atualizarStatus("Conectado ao servidor");
    
            // Inicia thread para receber mensagens
            new Thread(this::receberMensagens).start();
    
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                "Erro ao conectar ao servidor: " + e.getMessage(),
                "Erro de Conexão",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void receberMensagens() {
        try {
            String mensagem;
            while ((mensagem = dataIn.readUTF()) != null) {
                final String msg = mensagem;
                System.out.println("DEBUG CLIENTE RECEBEU: " + msg); // Log de depuração

                SwingUtilities.invokeLater(() -> {
                    if (msg.startsWith("ALERTA:")) {
                        adicionarAlerta(msg.substring(7));
                    } else if (msg.startsWith("CHAT:")) {
                        String conteudoChat = msg.substring(5); // Remove o prefixo CHAT:

                        if (conteudoChat.startsWith("ALERTA:")) {
                            String[] partesAlerta = conteudoChat.substring(7).split(":", 2);
                            if (partesAlerta.length >= 2) {
                                String remetenteAlerta = partesAlerta[0];
                                String mensagemAlerta = partesAlerta[1];
                                adicionarAlerta("[ALERTA DE INSPETOR] De " + remetenteAlerta + ": " + mensagemAlerta);
                            } else {
                                System.err.println("DEBUG CLIENTE: Mensagem CHAT:ALERTA: mal formada: " + msg);
                            }
                        } else if (chatInspetores != null && chatInspetores.processarMensagem(conteudoChat)) {
                            System.out.println("DEBUG CLIENTE RECEBER: Mensagem CHAT processada por ChatInspetores.");
                        } else {
                            System.out.println("DEBUG CLIENTE RECEBER: Mensagem CHAT não processada pelo chat: " + msg);
                        }
                    } else if (msg.startsWith("ARQUIVO:")) {
                        // Processa mensagem de arquivo: ARQUIVO:nomeUnico:remetente:nomeOriginal
                        String[] partes = msg.substring(8).split(":", 3); // Limita o split a 3 partes para garantir que o nomeOriginal não seja quebrado por ":"
                        if (partes.length >= 3) {
                            String nomeUnico = partes[0];
                            String remetente = partes[1];
                            String nomeOriginal = partes[2]; // Este é o nome original do arquivo

                            // Não mostrar a notificação para si mesmo se for o remetente
                            if (!remetente.equals(nomeInspetor)) { // Compare com o nome do inspetor deste cliente
                                int opcao = JOptionPane.showConfirmDialog(
                                    frame,
                                    "Você recebeu um arquivo: " + nomeOriginal + "\nDe: " + remetente + "\n\nDeseja baixar?",
                                    "Arquivo Recebido",
                                    JOptionPane.YES_NO_OPTION
                                );

                                if (opcao == JOptionPane.YES_OPTION) {
                                    iniciarDownloadArquivo(nomeUnico, nomeOriginal);
                                }
                            }
                        }
                    } else {
                        // Mensagens que não são ALERTA, CHAT ou ARQUIVO (o que não deveria acontecer no fluxo normal)
                        adicionarMensagem(msg); // Adiciona mensagens desconhecidas na área geral
                    }
                });
            }
        } catch (IOException e) {
            System.err.println("Erro ao receber mensagens: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                atualizarStatus("Desconectado do servidor");
                JOptionPane.showMessageDialog(frame,
                    "Conexão com o servidor perdida: " + e.getMessage(),
                    "Erro de Conexão",
                    JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void adicionarMensagem(String mensagem) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());
        areaMensagens.append(String.format("[%s] %s\n", timestamp, mensagem));
        areaMensagens.setCaretPosition(areaMensagens.getDocument().getLength());
    }

    private void adicionarAlerta(String mensagem) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());
        
        // Estilo mais chamativo para o alerta usando HTML
        String alertaFormatado = String.format(
            "<font color=\"red\">\n" +
            "========================================\n" +
            "[%s] 🚨 **ALERTA:** %s\n" +
            "========================================\n" +
            "</font>",
            timestamp, mensagem);

        // JTextArea não suporta HTML por padrão. Usar JEditorPane para renderizar HTML.
        // Para JTextArea, vamos apenas usar os separadores e emoji.
         areaMensagens.append("\n"); // Adiciona uma linha em branco antes
         areaMensagens.append("========================================\n");
         areaMensagens.append(String.format("[%s] 🚨 ALERTA: %s\n", timestamp, mensagem));
         areaMensagens.append("========================================\n");
         areaMensagens.append("\n"); // Adiciona uma linha em branco depois

        areaMensagens.setCaretPosition(areaMensagens.getDocument().getLength());

        // Toca um som de alerta
        Toolkit.getDefaultToolkit().beep();
    }

    private void enviarMensagem() {
        String mensagem = campoMensagem.getText().trim();
        if (!mensagem.isEmpty()) {
            try {
                dataOut.writeUTF(mensagem);
            campoMensagem.setText("");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void abrirJanelaRelatorio() {
        JDialog dialog = new JDialog(frame, "Enviar Relatório", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(frame);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextArea areaRelatorio = new JTextArea();
        areaRelatorio.setFont(new Font("SansSerif", Font.PLAIN, 14));
        // Modelo de relatório pré-preenchido
        areaRelatorio.setText("Local de Monitoramento: " + localMonitorado + "\n" +
                              "Data: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()) + "\n" +
                              "Inspetor: " + nomeInspetor + "\n\n" +
                              "Descrição do Evento:\n" +
                              "--------------------\n\n" +
                              "Impacto Ambiental:\n" +
                              "--------------------\n\n" +
                              "Medidas Tomadas:\n" +
                              "--------------------\n");
        
        JScrollPane scrollPane = new JScrollPane(areaRelatorio);

        JPanel botoesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton botaoEnviar = new JButton("Enviar");
        JButton botaoCancelar = new JButton("Cancelar");

        botaoEnviar.addActionListener(e -> {
            String relatorio = areaRelatorio.getText().trim();
            if (!relatorio.isEmpty()) {
                try {
                    dataOut.writeUTF("RELATORIO:" + relatorio);
                adicionarRelatorio(relatorio);
                dialog.dispose();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            } else {
                JOptionPane.showMessageDialog(dialog,
                        "Por favor, preencha o relatório.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        botaoCancelar.addActionListener(e -> dialog.dispose());

        botoesPanel.add(botaoEnviar);
        botoesPanel.add(botaoCancelar);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(botoesPanel, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void abrirJanelaAlerta() {
        JDialog dialog = new JDialog(frame, "Enviar Alerta Ambiental", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(frame);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTextArea areaAlerta = new JTextArea();
        areaAlerta.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(areaAlerta);

        JPanel botoesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton botaoEnviar = new JButton("Enviar Alerta");
        JButton botaoCancelar = new JButton("Cancelar");

        botaoEnviar.setBackground(new Color(255, 100, 100));
        botaoEnviar.setForeground(Color.WHITE);

        botaoEnviar.addActionListener(e -> {
            String alerta = areaAlerta.getText().trim();
            if (!alerta.isEmpty()) {
                // ENCAPSULAR O ALERTA DENTRO DO PROTOCOLO CHAT
                String comandoAlerta = "CHAT:ALERTA:" + alerta; // <--- ADICIONAR PREFIXO CHAT:
                try {
                    dataOut.writeUTF(comandoAlerta); // Usar dataOut para enviar na conexão principal de chat
                    adicionarMensagem("ALERTA ENVIADO: " + alerta);
                dialog.dispose();
                } catch (IOException e1) {
                    JOptionPane.showMessageDialog(frame,
                        "Erro ao enviar alerta: " + e1.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(dialog,
                        "Por favor, descreva o alerta ambiental.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        botaoCancelar.addActionListener(e -> dialog.dispose());

        botoesPanel.add(botaoEnviar);
        botoesPanel.add(botaoCancelar);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(botoesPanel, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void desconectar() {
        try {
            if (dataOut != null) {
                dataOut.writeUTF("SAIR");
            }
    
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Erro ao desconectar: " + e.getMessage());
        }
    }

    private void adicionarRelatorio(String relatorio) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());
        areaMensagens.append(String.format("[%s] 📝 RELATÓRIO ENVIADO:\n%s\n", timestamp, relatorio));
        areaMensagens.setCaretPosition(areaMensagens.getDocument().getLength());
    }

    public String getNomeInspetor() {
        return nomeInspetor;
    }

    public String getLocalMonitorado() {
        return localMonitorado;
    }

    public JFrame getFrame() {
        return frame;
    }

    private void enviarRelatorioEmail() {
        JDialog dialog = new JDialog(frame, "Enviar Relatório por E-mail", true);
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(frame);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridLayout(4, 1, 5, 5)); // Campos para destinatário, assunto, etc.
        
        JTextField campoDestinatario = new JTextField();
        JTextField campoAssunto = new JTextField();
        JTextArea areaMensagem = new JTextArea(); // Área do corpo do e-mail
        areaMensagem.setFont(new Font("SansSerif", Font.PLAIN, 14));
        // Preencher com o modelo de relatório
        areaMensagem.setText("Local de Monitoramento: " + localMonitorado + "\n" +
                             "Data: " + new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date()) + "\n" +
                             "Inspetor: " + nomeInspetor + "\n\n" +
                             "Descrição do Evento:\n" +
                             "--------------------\n\n" +
                             "Impacto Ambiental:\n" +
                             "--------------------\n\n" +
                             "Medidas Tomadas:\n" +
                             "--------------------\n");
        
        JScrollPane scrollPane = new JScrollPane(areaMensagem);

        formPanel.add(new JLabel("Destinatário:"));
        formPanel.add(campoDestinatario);
        formPanel.add(new JLabel("Assunto:"));
        formPanel.add(campoAssunto);

        JPanel botoesPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton botaoEnviar = new JButton("Enviar"); // Botão de enviar
        JButton botaoCancelar = new JButton("Cancelar");

        botaoEnviar.addActionListener(e -> {
            String destinatario = campoDestinatario.getText().trim();
            String assunto = campoAssunto.getText().trim();
            String corpo = areaMensagem.getText().trim();

            if (destinatario.isEmpty() || assunto.isEmpty() || corpo.isEmpty()) {
                JOptionPane.showMessageDialog(dialog,
                        "Por favor, preencha todos os campos de destinatário, assunto e corpo.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // **AQUI VOCÊ INSERE SEU EMAIL GMAIL E A SENHA DE APP**
            // Substitua "SEU_EMAIL_GMAIL@gmail.com" pelo seu email
            // Substitua "SUA_SENHA_DE_APP" pela senha de app gerada ou senha de acesso menos seguro
            String remetente = "valavezzo@gmail.com"; // <--- SEU EMAIL
            String senha = "SUA_SENHA_DE_APP"; // <--- SUA SENHA DE APP

            // Verifica se as credenciais foram atualizadas (evita tentar enviar com placeholders)
            if (remetente.equals("SEU_EMAIL_GMAIL@gmail.com") || senha.equals("SUA_SENHA_DE_APP")) {
                 JOptionPane.showMessageDialog(dialog,
                        "Por favor, atualize seu email e senha no código (EmailSender).", "Erro de Configuração", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Tenta enviar o e-mail
            boolean sucesso = EmailSender.enviarEmail(remetente, senha, destinatario, assunto, corpo);

            if (sucesso) {
                JOptionPane.showMessageDialog(dialog,
                        "E-mail enviado com sucesso!",
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog,
                        "Erro ao enviar e-mail. Verifique as credenciais ou as configurações de segurança da sua conta Google.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });

        botaoCancelar.addActionListener(e -> dialog.dispose());

        botoesPanel.add(botaoEnviar);
        botoesPanel.add(botaoCancelar);

        panel.add(formPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(botoesPanel, BorderLayout.SOUTH);

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void abrirSeletorEmoticons() {
        String[] emoticons = {
            "😊", "😂", "❤️", "👍", "🎉", "🔥", "⭐", "💯",
            "😎", "🤔", "😢", "😡", "🙏", "👏", "🎯", "💪"
        };

        JDialog dialog = new JDialog(frame, "Selecionar Emoticon", true);
        dialog.setSize(300, 200);
        dialog.setLocationRelativeTo(frame);

        JPanel panel = new JPanel(new GridLayout(4, 4, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        for (String emoticon : emoticons) {
            JButton botao = new JButton(emoticon);
            botao.setFont(new Font("Dialog", Font.PLAIN, 20));
            botao.addActionListener(e -> {
                campoMensagem.setText(campoMensagem.getText() + emoticon);
                dialog.dispose();
            });
            panel.add(botao);
        }

        dialog.add(panel);
        dialog.setVisible(true);
    }

    private void iniciarEnvioArquivo() {
        JFileChooser fileChooser = new JFileChooser();
        int resultado = fileChooser.showOpenDialog(frame);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            File arquivoSelecionado = fileChooser.getSelectedFile();
            if (arquivoSelecionado.exists() && arquivoSelecionado.isFile()) {
                // Criar um JDialog para selecionar o destinatário
                JDialog dialog = new JDialog(frame, "Selecionar Destinatário", true);
                dialog.setLayout(new BorderLayout());
                
                // Criar o JComboBox com os destinatários
                DefaultComboBoxModel<String> modelo = new DefaultComboBoxModel<>();
                modelo.addElement("Todos os Inspetores");
                modelo.addElement("Central");
                
                // Adicionar os inspetores individuais
                for (String inspetor : chatInspetores.getListaInspetores()) {
                    if (!inspetor.equals(nomeInspetor)) { // Não incluir o próprio inspetor
                        modelo.addElement(inspetor);
                    }
                }
                
                JComboBox<String> comboDestinatarios = new JComboBox<>(modelo);
                JPanel painel = new JPanel(new FlowLayout());
                painel.add(new JLabel("Enviar para:"));
                painel.add(comboDestinatarios);
                
                JButton btnEnviar = new JButton("Enviar");
                btnEnviar.setPreferredSize(new Dimension(100, 30));
                btnEnviar.addActionListener(e -> {
                    String destinatario = (String) comboDestinatarios.getSelectedItem();
                    dialog.dispose();
                    enviarArquivo(arquivoSelecionado, destinatario);
                });
                
                dialog.add(painel, BorderLayout.CENTER);
                dialog.add(btnEnviar, BorderLayout.SOUTH);
                dialog.pack();
                dialog.setLocationRelativeTo(frame);
                dialog.setVisible(true);
            }
        }
    }

    private void abrirChatInspetores() {
        if (chatInspetores != null) {
            chatInspetores.mostrar();
        } else {
            JOptionPane.showMessageDialog(frame,
                "Chat de inspetores não disponível. Verifique sua conexão.",
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void atualizarStatus(String status) {
        SwingUtilities.invokeLater(() -> {
            labelStatus.setText(status);
            if (status.contains("Conectado")) {
                labelStatus.setForeground(new Color(0, 150, 0));
            } else {
                labelStatus.setForeground(Color.RED);
            }
        });
    }

    private void abrirListaArquivos() {
        // Executa a comunicação com o servidor em uma nova thread
        new Thread(() -> {
            try {
                // 1. Comunicação com o servidor (em thread separada)
                Socket socketArquivos = new Socket(SERVIDOR_IP, SERVIDOR_PORTA);
                DataOutputStream out = new DataOutputStream(socketArquivos.getOutputStream());
                DataInputStream in = new DataInputStream(socketArquivos.getInputStream());

                out.writeUTF("LISTAR_ARQUIVOS"); // Envia o comando
                out.flush(); // Garante que o comando seja enviado imediatamente

                String lista = in.readUTF(); // Bloqueia APENAS esta thread (não a EDT) esperando a lista
                System.out.println("DEBUG: Lista recebida do servidor: " + lista); // Debug

                socketArquivos.close();

                // 2. Atualiza a interface gráfica (na EDT)
                SwingUtilities.invokeLater(() -> {
                    // Parse da lista recebida
                    if (lista == null || lista.trim().isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "Nenhum arquivo disponível no momento.", 
                            "Arquivos Disponíveis", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    String[] arquivos = lista.split(";");
                    java.util.List<String> nomesExibicao = new java.util.ArrayList<>();
                    Map<String, String[]> mapaArquivos = new HashMap<>();

                    for (String arq : arquivos) {
                        if (arq.trim().isEmpty()) continue;
                        String[] partes = arq.split("\\|");
                        if (partes.length >= 3) {
                            String nomeUnico = partes[0];
                            String nomeOriginal = partes[1];
                            String remetente = partes[2];
                            String exibicao = nomeOriginal + " (de " + remetente + ")";
                            nomesExibicao.add(exibicao);
                            mapaArquivos.put(exibicao, new String[]{nomeUnico, nomeOriginal});
                        }
                    }

                    if (nomesExibicao.isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "Nenhum arquivo disponível no momento.", 
                            "Arquivos Disponíveis", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // Mostra a lista em um JList
                    JList<String> listaArquivos = new JList<>(nomesExibicao.toArray(new String[0]));
                    JScrollPane scrollPane = new JScrollPane(listaArquivos);
                    scrollPane.setPreferredSize(new Dimension(400, Math.min(300, nomesExibicao.size() * 25)));

                    int opcao = JOptionPane.showConfirmDialog(frame, scrollPane, 
                        "Arquivos Disponíveis", JOptionPane.OK_CANCEL_OPTION);

                    if (opcao == JOptionPane.OK_OPTION && listaArquivos.getSelectedValue() != null) {
                        String[] info = mapaArquivos.get(listaArquivos.getSelectedValue());
                        iniciarDownloadArquivo(info[0], info[1]);
                    }
                });

            } catch (IOException e) {
                String erroMsg = "Erro ao listar arquivos: " + e.getMessage();
                System.err.println(erroMsg);
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, erroMsg, "Erro", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    private void iniciarDownloadArquivo(String nomeUnico, String nomeOriginal) {
        // Classe interna para armazenar o arquivo de destino
        class ArquivoDestinoHolder {
            File arquivo;
            String erro;
        }
        final ArquivoDestinoHolder holder = new ArquivoDestinoHolder();

        // Executa o download em uma nova thread
        new Thread(() -> {
            try {
                // Primeiro, mostra o JFileChooser para o usuário escolher onde salvar
                SwingUtilities.invokeAndWait(() -> {
                    try {
                        JFileChooser fileChooser = new JFileChooser();
                        fileChooser.setSelectedFile(new File(nomeOriginal));
                        fileChooser.setDialogTitle("Salvar arquivo como");
                        
                        int resultado = fileChooser.showSaveDialog(frame);
                        if (resultado == JFileChooser.APPROVE_OPTION) {
                            holder.arquivo = fileChooser.getSelectedFile();
                            
                            // Cria a pasta de destino se não existir
                            File pastaDestino = holder.arquivo.getParentFile();
                            if (pastaDestino != null && !pastaDestino.exists()) {
                                if (!pastaDestino.mkdirs()) {
                                    holder.erro = "Não foi possível criar a pasta de destino: " + pastaDestino.getAbsolutePath();
                                }
                            }
                        }
                    } catch (Exception e) {
                        holder.erro = "Erro ao selecionar local do arquivo: " + e.getMessage();
                    }
                });

                // Se houve erro na seleção do arquivo, mostra mensagem e retorna
                if (holder.erro != null) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(frame, holder.erro, "Erro", JOptionPane.ERROR_MESSAGE);
                    });
                    return;
                }

                // Se o usuário cancelou a seleção, não faz o download
                if (holder.arquivo == null) {
                    return;
                }

                // Conecta ao servidor para baixar o arquivo
                Socket socketDownload = new Socket(SERVIDOR_IP, SERVIDOR_PORTA);
                DataOutputStream outDownload = new DataOutputStream(socketDownload.getOutputStream());
                DataInputStream inDownload = new DataInputStream(socketDownload.getInputStream());

                // Envia o comando de download
                outDownload.writeUTF("DOWNLOAD:" + nomeUnico);
                outDownload.flush();

                // Aguarda a resposta do servidor
                String resposta = inDownload.readUTF();
                if (!resposta.equals("INICIANDO_DOWNLOAD")) {
                    throw new IOException("Servidor não iniciou o download: " + resposta);
                }

                // Recebe o arquivo e salva no local escolhido pelo usuário
                if (TransferenciaArquivos.receberArquivo(socketDownload, holder.arquivo.getName(), holder.arquivo.getParent())) {
                    SwingUtilities.invokeLater(() -> {
                        adicionarMensagem("Arquivo baixado com sucesso: " + holder.arquivo.getName());
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(frame, "Erro ao baixar arquivo.", "Erro", JOptionPane.ERROR_MESSAGE);
                    });
                }
                socketDownload.close();
            } catch (Exception e) {
                String erroMsg = "Erro ao baixar arquivo: " + e.getMessage();
                System.err.println(erroMsg);
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(frame, erroMsg, "Erro", JOptionPane.ERROR_MESSAGE);
                });
            }
        }).start();
    }

    // Método para atualizar a lista de inspetores (chamado pelo ChatInspetores)
    public void atualizarListaInspetores(List<String> listaRecebidaDoServidor) {
        SwingUtilities.invokeLater(() -> {
            inspetoresConectados.clear(); // Limpa a lista atual

            // Sempre incluir as opções fixas
            inspetoresConectados.add("Central");
            inspetoresConectados.add("Todos os Inspetores");

            // Adicionar os inspetores recebidos do servidor (exceto ele mesmo)
            if (listaRecebidaDoServidor != null) {
                for (String inspetor : listaRecebidaDoServidor) {
                    // Adicionar inspetor apenas se não for o próprio cliente e não estiver vazio, e ainda não foi adicionado (Central/Todos)
                    if (!inspetor.equals(nomeInspetor) && !inspetor.trim().isEmpty() &&
                        !inspetoresConectados.contains(inspetor.trim())) {
                         inspetoresConectados.add(inspetor.trim());
                    }
                }
            }

            // DEBUG: Exibir lista atualizada
            System.out.println("DEBUG CLIENTE: Lista de inspetores atualizada: " + inspetoresConectados);

        });
    }

    private void enviarArquivo(File arquivo, String destinatario) {
        try {
            // Cria um novo socket para transferência de arquivos
            Socket socketArquivo = new Socket(SERVIDOR_IP, SERVIDOR_PORTA);
            DataOutputStream out = new DataOutputStream(socketArquivo.getOutputStream());
            DataInputStream in = new DataInputStream(socketArquivo.getInputStream());

            // Envia o comando de arquivo com nome, destinatário e remetente
            out.writeUTF("ARQUIVO:" + arquivo.getName() + ":" + destinatario + ":" + nomeInspetor);
            
            // Envia o tamanho do arquivo
            out.writeLong(arquivo.length());
            
            // Envia o conteúdo do arquivo
            FileInputStream fileIn = new FileInputStream(arquivo);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            fileIn.close();
            
            // Aguarda confirmação do servidor
            String resposta = in.readUTF();
            if (resposta.equals("ARQUIVO_RECEBIDO")) {
                adicionarMensagem("Arquivo enviado com sucesso: " + arquivo.getName() + " para " + destinatario);
            } else {
                JOptionPane.showMessageDialog(frame,
                    "Erro ao enviar arquivo: " + resposta,
                    "Erro", JOptionPane.ERROR_MESSAGE);
            }
            socketArquivo.close();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame,
                "Erro ao conectar para envio de arquivo: " + e.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
