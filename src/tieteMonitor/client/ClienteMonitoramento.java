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
import tieteMonitor.util.WebcamManager;
import tieteMonitor.util.MulticastManager;
import tieteMonitor.util.EmailSender;

/**
 * Cliente para Sistema de Monitoramento Ambiental do Rio Tietê
 * Permite que inspetores se comuniquem com a central e entre si
 */
public class ClienteMonitoramento {
    private String SERVIDOR_IP;
    private int SERVIDOR_PORTA;

    private Socket socket;
    private PrintWriter saida;
    private BufferedReader entrada;
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
    
    // Gerenciadores de recursos
    private WebcamManager webcamManager = null; // Inicializar como null, será criado após a conexão

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
        frame.setSize(800, 600);
    
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
        botaoEnviar.setIcon(new ImageIcon("src/resources/send.png"));
        botaoEnviar.addActionListener(e -> enviarMensagem());
        painelEntrada.add(campoMensagem, BorderLayout.CENTER);
        painelEntrada.add(botaoEnviar, BorderLayout.EAST);

        // Painel de botões
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        botaoRelatorio = new JButton("Enviar Relatório");
        botaoRelatorio.setIcon(new ImageIcon("src/resources/report.png"));
        botaoRelatorio.addActionListener(e -> abrirJanelaRelatorio());
        painelBotoes.add(botaoRelatorio);

        botaoAlerta = new JButton("Alerta Ambiental");
        botaoAlerta.setBackground(new Color(255, 100, 100));
        botaoAlerta.setIcon(new ImageIcon("src/resources/alert.png"));
        botaoAlerta.addActionListener(e -> abrirJanelaAlerta());
        painelBotoes.add(botaoAlerta);

        botaoEmail = new JButton("Enviar E-mail");
        botaoEmail.setIcon(new ImageIcon("src/resources/email.png"));
        botaoEmail.addActionListener(e -> enviarRelatorioEmail());
        painelBotoes.add(botaoEmail);

        JButton botaoEnviarArquivo = new JButton("Enviar Arquivo");
        botaoEnviarArquivo.setIcon(new ImageIcon("src/resources/file.png"));
        botaoEnviarArquivo.addActionListener(e -> selecionarArquivo());
        painelBotoes.add(botaoEnviarArquivo);

        JButton botaoChat = new JButton("Chat Inspetores");
        botaoChat.setBackground(new Color(100, 180, 255));
        botaoChat.setIcon(new ImageIcon("src/resources/chat.png"));
        botaoChat.addActionListener(e -> abrirChatInspetores());
        painelBotoes.add(botaoChat);

        // Novo botão para compartilhar vídeo
        JButton botaoCompartilharVideo = new JButton("Compartilhar Vídeo");
        botaoCompartilharVideo.setIcon(new ImageIcon("src/resources/webcam.png")); // Assumindo que há um ícone de webcam
        botaoCompartilharVideo.addActionListener(e -> toggleCompartilharVideo());
        painelBotoes.add(botaoCompartilharVideo);

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
            // Conecta ao servidor
            socket = new Socket(SERVIDOR_IP, SERVIDOR_PORTA);
            saida = new PrintWriter(socket.getOutputStream(), true);
            entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    
            // Envia identificação
            saida.println(nomeInspetor);
            saida.println(localMonitorado);
    
            // Inicializa componente de chat entre inspetores
            chatInspetores = new ChatInspetores(this, socket, saida);
            
            // Inicializa o gerenciador de webcam AQUI, APÓS obter o PrintWriter 'saida'
            try {
                webcamManager = new WebcamManager(saida); // Passa o PrintWriter 'saida'
            } catch (com.github.sarxos.webcam.WebcamException e) {
                 webcamManager = null;
                 SwingUtilities.invokeLater(() -> {
                     adicionarMensagem("Erro ao inicializar webcam. Função de vídeo desabilitada: " + e.getMessage());
                 });
                 System.err.println("Erro ao inicializar webcam: " + e.getMessage());
            }
    
            // Inicializa o gerenciador multicast
            multicastManager = new MulticastManager(mensagem -> {
                if (mensagem.startsWith("ALERTA_MULTICAST:")) {
                    String conteudoAlerta = mensagem.substring("ALERTA_MULTICAST:".length());
                    SwingUtilities.invokeLater(() -> adicionarAlerta("[MULTICAST] " + conteudoAlerta));
                }
            });
            multicastManager.iniciarRecepcao();
    
            // Atualiza status
            SwingUtilities.invokeLater(() -> {
                labelStatus.setText("Conectado ao servidor");
                labelStatus.setForeground(new Color(0, 150, 0));
                adicionarMensagem("Conectado ao Sistema de Monitoramento Ambiental do Rio Tietê");
            });
    
            // Inicia thread para receber mensagens
            new Thread(this::receberMensagens).start();
    
        } catch (IOException e) {
            adicionarMensagem("Erro ao conectar: " + e.getMessage());
            JOptionPane.showMessageDialog(frame,
                    "Não foi possível conectar ao servidor.\n" + e.getMessage(),
                    "Erro de Conexão", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void receberMensagens() {
        try {
            String mensagem;
            while ((mensagem = entrada.readLine()) != null) {
                final String msg = mensagem;
                SwingUtilities.invokeLater(() -> {
                    if (msg.startsWith("ALERTA:")) {
                        adicionarAlerta(msg.substring(7));
                    } else if (msg.startsWith("CHAT:")) {
                        // Se for uma mensagem de chat, passa para o ChatInspetores processar
                        if (chatInspetores != null && chatInspetores.processarMensagem(msg)) {
                            // Mensagem de chat processada pelo componente
                            System.out.println("DEBUG CLIENTE RECEBER: Mensagem CHAT processada por ChatInspetores.");
                        } else {
                            // Se não for mensagem de chat ou o componente não a processou, adiciona à área geral
                            adicionarMensagem(msg);
                            System.out.println("DEBUG CLIENTE RECEBER: Mensagem não-CHAT ou não processada pelo chat: " + msg);
                        }
                    } else {
                        adicionarMensagem(msg);
                    }
                });
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                adicionarMensagem("Erro na conexão: " + e.getMessage());
                labelStatus.setText("Desconectado");
                labelStatus.setForeground(Color.RED);
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
            saida.println(mensagem);
            campoMensagem.setText("");
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
                saida.println("RELATORIO:" + relatorio);
                adicionarRelatorio(relatorio);
                dialog.dispose();
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
                // Envia o alerta para o servidor
                saida.println("ALERTA:" + alerta);
                // Não adiciona localmente mais, aguarda a retransmissão do servidor
                // adicionarAlerta(alerta);
                dialog.dispose();
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
            if (webcamManager != null) {
                webcamManager.fechar();
            }
            
            if (multicastManager != null) {
                multicastManager.fechar();
            }
            
            if (saida != null) {
                saida.println("SAIR");
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
            String senha = "peuf oshg zmnp pkqj"; // <--- SUA SENHA DE APP

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

    private void selecionarArquivo() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter(
            "Arquivos de Imagem", "jpg", "jpeg", "png", "gif"));
        
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File arquivo = fileChooser.getSelectedFile();
            try {
                // Cria um novo socket para transferência de arquivos
                Socket socketArquivo = new Socket(SERVIDOR_IP, SERVIDOR_PORTA);
                if (TransferenciaArquivos.enviarArquivo(socketArquivo, arquivo, "CENTRAL")) {
                    adicionarMensagem("Arquivo enviado com sucesso: " + arquivo.getName());
                } else {
                    JOptionPane.showMessageDialog(frame,
                        "Erro ao enviar arquivo.",
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

    private void abrirChatInspetores() {
        if (chatInspetores != null) {
            chatInspetores.mostrar();
        } else {
            JOptionPane.showMessageDialog(frame,
                "Chat de inspetores não disponível. Verifique sua conexão.",
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleCompartilharVideo() {
        // Implemente a lógica para alternar o compartilhamento de vídeo
        // Isso pode envolver a inicialização ou encerramento da captura de vídeo pela webcam
        // ou a troca entre compartilhar e parar de compartilhar o vídeo
        if (webcamManager != null) {
            webcamManager.toggleCompartilharVideo();
        } else {
            JOptionPane.showMessageDialog(frame,
                "Webcam não disponível. Função desabilitada.",
                "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }
}
