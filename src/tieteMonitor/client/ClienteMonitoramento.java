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

/**
 * Cliente para Sistema de Monitoramento Ambiental do Rio Tietê
 * Permite que inspetores se comuniquem com a central e entre si
 */
public class ClienteMonitoramento {
    private static final String SERVIDOR_IP = "0.tcp.sa.ngrok.io";
    private static final int SERVIDOR_PORTA = 17137;

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

    // Componente de chat entre inspetores
    private ChatInspetores chatInspetores;

    public static void main(String[] args) {
        new ClienteMonitoramento().iniciar();
    }

    public void iniciar() {
        // Coleta informações do inspetor
        solicitarInformacoes();

        // Configura a interface gráfica
        configurarInterface();

        // Conecta ao servidor
        conectarServidor();
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
        frame.setSize(700, 500);

        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
            SwingUtilities.updateComponentTreeUI(frame);
        } catch (Exception e) {
            // Se não conseguir aplicar o Nimbus, segue com o padrão
        }

        // Área de mensagens
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
        botaoEnviar = new JButton("Enviar");
        botaoEnviar.setIcon(new ImageIcon("src/resources/send.png")); // Adicione um ícone de envio
        botaoEnviar.addActionListener(e -> enviarMensagem());
        painelEntrada.add(campoMensagem, BorderLayout.CENTER);
        painelEntrada.add(botaoEnviar, BorderLayout.EAST);

        // Painel de botões
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        botaoRelatorio = new JButton("Enviar Relatório");
        botaoRelatorio.setIcon(new ImageIcon("src/resources/report.png")); // Ícone de relatório
        botaoRelatorio.addActionListener(e -> abrirJanelaRelatorio());
        painelBotoes.add(botaoRelatorio);

        botaoAlerta = new JButton("Alerta Ambiental");
        botaoAlerta.setBackground(new Color(255, 100, 100));
        botaoAlerta.setIcon(new ImageIcon("src/resources/alert.png")); // Ícone de alerta
        botaoAlerta.addActionListener(e -> abrirJanelaAlerta());
        painelBotoes.add(botaoAlerta);

        JButton botaoEnviarArquivo = new JButton("Enviar Arquivo");
        botaoEnviarArquivo.setIcon(new ImageIcon("src/resources/file.png")); // Ícone de arquivo
        botaoEnviarArquivo.addActionListener(e -> selecionarArquivo());
        painelBotoes.add(botaoEnviarArquivo);

        JButton botaoChat = new JButton("Chat Inspetores");
        botaoChat.setBackground(new Color(100, 180, 255));
        botaoChat.setIcon(new ImageIcon("src/resources/chat.png")); // Ícone de chat
        botaoChat.addActionListener(e -> abrirChatInspetores());
        painelBotoes.add(botaoChat);

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

        // Adiciona o painel principal ao frame
        frame.getContentPane().add(painelPrincipal);

        // Configura janela
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Configura encerramento
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
                final String msgFinal = mensagem;
    
                // Verifica se a mensagem é para o chat de inspetores
                boolean processadaPeloChat = chatInspetores.processarMensagem(msgFinal);
    
                // Se não foi processada pelo chat, trata normalmente
                if (!processadaPeloChat) {
                    SwingUtilities.invokeLater(() -> {
                        if (msgFinal.startsWith("ALERTA:")) {
                            // Formata alertas de forma destacada
                            adicionarAlerta(msgFinal.substring(7));
                        } else if (msgFinal.startsWith("RELATORIO_COMPLETO:")) {
                            // Processa relatório completo
                            String relatorio = msgFinal.substring(19);
                            
                            // Substitui todos os <br> por quebras de linha reais
                            relatorio = relatorio.replace("<br>", "\n");
                            
                            adicionarRelatorio(relatorio);
                        } else if (msgFinal.startsWith("RELATORIO_DIRETO:")) {
                            // Processa relatório direto (novo formato)
                            String relatorio = msgFinal.substring(17);
                            adicionarRelatorio(relatorio);
                        } else if (msgFinal.startsWith("RELATORIO_LOCAL:")) {
                            // Ignora mensagens com este prefixo, pois já foram processadas localmente
                            // Não faz nada aqui
                        } else {
                            adicionarMensagem(msgFinal);
                        }
                    });
                }
            }
        } catch (IOException e) {
            if (!socket.isClosed()) {
                SwingUtilities.invokeLater(() -> {
                    adicionarMensagem("Conexão com o servidor perdida: " + e.getMessage());
                    labelStatus.setText("Desconectado");
                    labelStatus.setForeground(Color.RED);
                });
            }
        }
    }

    private void adicionarMensagem(String mensagem) {
        // Adiciona timestamp à mensagem
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());

        areaMensagens.append("[" + timestamp + "] " + mensagem + "\n");
        // Auto-scroll para a última linha
        areaMensagens.setCaretPosition(areaMensagens.getDocument().getLength());
    }

    private void adicionarAlerta(String mensagem) {
        // Destaca alertas com formação especial
        String textoAtual = areaMensagens.getText();
        areaMensagens.setText("");
        areaMensagens.append(textoAtual);

        // Usa estilo especial para alertas
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());

        areaMensagens.append("\n===================== ALERTA =====================\n");
        areaMensagens.append("[" + timestamp + "] " + mensagem + "\n");
        areaMensagens.append("==================================================\n\n");

        // Auto-scroll para a última linha
        areaMensagens.setCaretPosition(areaMensagens.getDocument().getLength());

        // Adicionalmente, mostra popup de alerta
        JOptionPane.showMessageDialog(frame,
                mensagem,
                "ALERTA AMBIENTAL",
                JOptionPane.WARNING_MESSAGE);
    }

    private void enviarMensagem() {
        String mensagem = campoMensagem.getText().trim();
        if (!mensagem.isEmpty() && saida != null) {
            saida.println(mensagem);
            campoMensagem.setText("");
        }
    }

    private void abrirJanelaRelatorio() {
        JDialog dialogRelatorio = new JDialog(frame, "Criar Relatório Ambiental", true);
        dialogRelatorio.setSize(600, 500);
        dialogRelatorio.setLayout(new BorderLayout());

        JTextArea areaRelatorio = new JTextArea();
        areaRelatorio.setLineWrap(true);
        areaRelatorio.setWrapStyleWord(true);
        JScrollPane scrollPane = new JScrollPane(areaRelatorio);

        // Adiciona template básico para o relatório
        areaRelatorio.setText("RELATÓRIO DE INSPEÇÃO AMBIENTAL\n\n" +
                "Local Inspecionado: [Detalhar local específico]\n" +
                "Data/Hora: [Automático]\n\n" +
                "OBSERVAÇÕES:\n" +
                "1. \n\n" +
                "PARÂMETROS ANALISADOS:\n" +
                "- pH: \n" +
                "- Temperatura: \n" +
                "- Oxigênio Dissolvido: \n" +
                "- Turbidez: \n\n" +
                "FONTES DE POLUIÇÃO IDENTIFICADAS:\n" +
                "- \n\n" +
                "RECOMENDAÇÕES:\n" +
                "- \n\n" +
                "CONCLUSÃO:\n");

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnCancelar = new JButton("Cancelar");
        JButton btnEnviar = new JButton("Enviar Relatório");

        btnCancelar.addActionListener(e -> dialogRelatorio.dispose());

        btnEnviar.addActionListener(e -> {
            String conteudoRelatorio = areaRelatorio.getText().trim();
            if (!conteudoRelatorio.isEmpty()) {
                if (saida != null) {
                    // Adiciona o relatório localmente primeiro
                    adicionarRelatorio(conteudoRelatorio);
                    adicionarMensagem("Relatório enviado com sucesso.");
                    
                    // Envia apenas uma mensagem simples para o servidor
                    saida.println("RELATORIO_ENVIADO: Um relatório ambiental foi enviado pelo inspetor " + nomeInspetor);
                    
                    dialogRelatorio.dispose();
                }
            } else {
                JOptionPane.showMessageDialog(dialogRelatorio,
                        "O relatório não pode estar vazio.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        });
    
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnEnviar);

        dialogRelatorio.add(scrollPane, BorderLayout.CENTER);
        dialogRelatorio.add(painelBotoes, BorderLayout.SOUTH);

        dialogRelatorio.setLocationRelativeTo(frame);
        dialogRelatorio.setVisible(true);
    }

    private void abrirJanelaAlerta() {
        String mensagemAlerta = JOptionPane.showInputDialog(frame,
                "Digite a mensagem de alerta ambiental:",
                "ALERTA AMBIENTAL",
                JOptionPane.WARNING_MESSAGE);

        if (mensagemAlerta != null && !mensagemAlerta.trim().isEmpty()) {
            if (saida != null) {
                saida.println("ALERTA:" + mensagemAlerta);
            }
        }
    }

    private void abrirChatInspetores() {
        // Abre a janela de chat entre inspetores
        if (chatInspetores != null) {
            chatInspetores.toggle();
        } else {
            JOptionPane.showMessageDialog(frame,
                    "O chat de inspetores não está disponível no momento.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void selecionarArquivo() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Selecione o arquivo para enviar");
        fileChooser.setFileFilter(new FileNameExtensionFilter(
                "Arquivos de Imagem e Documentos", "jpg", "png", "pdf", "txt", "doc", "docx"));

        int resultado = fileChooser.showOpenDialog(frame);
        if (resultado == JFileChooser.APPROVE_OPTION) {
            File arquivoSelecionado = fileChooser.getSelectedFile();

            // Verifica o tamanho do arquivo (limitado a 10MB para este exemplo)
            if (arquivoSelecionado.length() > 10 * 1024 * 1024) {
                JOptionPane.showMessageDialog(frame,
                        "O arquivo é muito grande. Tamanho máximo: 10MB",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Inicia o envio em uma thread separada para não bloquear a interface
            new Thread(() -> {
                try {
                    // Notifica o servidor que está iniciando uma transferência
                    saida.println("ARQUIVO:" + arquivoSelecionado.getName() + ":" + arquivoSelecionado.length());

                    // Aguarda confirmação do servidor para iniciar transferência
                    // Em uma implementação real, deve-se esperar por uma resposta específica do servidor
                    Thread.sleep(1000);

                    // Cria um socket temporário para transferência (em um caso real, seria por outro canal)
                    Socket socketTransferencia = new Socket(SERVIDOR_IP, 9876);

                    // Usa a classe de transferência para enviar o arquivo
                    boolean sucesso = TransferenciaArquivos.enviarArquivo(
                            socketTransferencia,
                            arquivoSelecionado,
                            "CENTRAL");

                    socketTransferencia.close();

                    if (sucesso) {
                        SwingUtilities.invokeLater(() -> {
                            adicionarMensagem("Arquivo " + arquivoSelecionado.getName() + " enviado com sucesso.");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            adicionarMensagem("Falha ao enviar o arquivo " + arquivoSelecionado.getName());
                        });
                    }

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        adicionarMensagem("Erro ao enviar arquivo: " + e.getMessage());
                    });
                }
            }).start();
        }
    }

    private void desconectar() {
        try {
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

    // Novo método para adicionar relatórios formatados
    private void adicionarRelatorio(String relatorio) {
        // Adiciona timestamp ao relatório
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        String timestamp = sdf.format(new Date());

        areaMensagens.append("\n================ RELATÓRIO AMBIENTAL ================\n");
        areaMensagens.append("[" + timestamp + "]\n\n");
        areaMensagens.append(relatorio + "\n");
        areaMensagens.append("==================================================\n\n");

        // Auto-scroll para a última linha
        areaMensagens.setCaretPosition(areaMensagens.getDocument().getLength());
    }

    // Getters para uso pelo ChatInspetores
    public String getNomeInspetor() {
        return nomeInspetor;
    }

    public String getLocalMonitorado() {
        return localMonitorado;
    }

    public JFrame getFrame() {
        return frame;
    }
}
