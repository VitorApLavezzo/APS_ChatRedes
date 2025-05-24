package tieteMonitor.util;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Base64; // Para codificar/decodificar dados binários como texto
import java.util.Date;

/**
 * Gerenciador de webcam para o sistema de monitoramento do Rio Tietê
 */
public class WebcamManager {
    private Webcam webcam;
    private WebcamPanel webcamPanel;
    private JFrame janela;
    
    private PrintWriter saidaServidor; // Referência para o canal de saída para o servidor
    private volatile boolean isSharing = false; // Flag para controlar o compartilhamento
    private Thread shareThread = null; // Thread para a captura e envio

    /**
     * Inicializa a webcam com resolução padrão
     * @param saidaServidor Canal de saída para o servidor
     */
    public WebcamManager(PrintWriter saidaServidor) {
        this.saidaServidor = saidaServidor;
        try {
            webcam = Webcam.getDefault();
            if (webcam != null) {
                webcam.setViewSize(WebcamResolution.VGA.getSize());
                webcamPanel = new WebcamPanel(webcam);
                webcamPanel.setFPSDisplayed(true);
                webcamPanel.setDisplayDebugInfo(true);
                webcamPanel.setImageSizeDisplayed(true);
                webcamPanel.setMirrored(true);
            }
        } catch (Exception e) {
            System.err.println("Erro ao inicializar webcam: " + e.getMessage());
        }
    }

    /**
     * Alterna o estado de compartilhamento de vídeo
     */
    public void toggleCompartilharVideo() {
        if (webcam == null) {
            System.err.println("Webcam não disponível para compartilhar.");
            return;
        }

        if (isSharing) {
            // Parar compartilhamento
            isSharing = false;
            if (shareThread != null) {
                shareThread.interrupt(); // Interrompe a thread de compartilhamento
            }
            System.out.println("Compartilhamento de vídeo parado.");
        } else {
            // Iniciar compartilhamento
            isSharing = true;
            shareThread = new Thread(this::shareVideo); // Cria uma nova thread para compartilhar
            shareThread.start();
            System.out.println("Compartilhamento de vídeo iniciado.");
        }
    }

    /**
     * Método executado pela thread de compartilhamento para capturar e enviar frames
     */
    private void shareVideo() {
        if (saidaServidor == null) {
            System.err.println("Canal de saída para o servidor não configurado. Não é possível compartilhar vídeo.");
            isSharing = false; // Para o compartilhamento se o canal de saída não estiver pronto
            return;
        }

        webcam.open(); // Abre a webcam para captura

        try {
            while (isSharing && !Thread.currentThread().isInterrupted()) {
                BufferedImage image = webcam.getImage();
                if (image != null) {
                    // Converte a imagem para bytes (JPEG)
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(image, "JPG", baos);
                    byte[] imageBytes = baos.toByteArray();

                    // Codifica os bytes para Base64 para enviar como string
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                    // Envia para o servidor com um prefixo
                    saidaServidor.println("VIDEO:" + base64Image);
                    
                    // Pausa um pouco para controlar o FPS (ex: 100ms para 10 FPS)
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restaura a flag de interrupção
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erro ao capturar ou enviar frame de vídeo: " + e.getMessage());
        } finally {
            webcam.close(); // Fecha a webcam ao parar o compartilhamento
            isSharing = false; // Garante que a flag seja false ao sair
            System.out.println("Thread de compartilhamento de vídeo finalizada.");
        }
    }
    
    /**
     * Abre a janela da webcam
     * 
     * @param titulo Título da janela
     * @param parent Componente pai para centralizar a janela
     */
    public void abrirJanela(String titulo, Component parent) {
        if (webcam == null) {
             JOptionPane.showMessageDialog(parent, 
                     "Não foi possível acessar a webcam. Verifique se ela está conectada.",
                     "Erro", JOptionPane.ERROR_MESSAGE);
             return;
         }

        // Se a janela já existe e está visível, apenas foca
        if (janela != null && janela.isVisible()) {
            janela.toFront();
            janela.requestFocus();
            return;
        }
        
        if (janela == null) {
            janela = new JFrame(titulo);
            janela.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            janela.setLayout(new BorderLayout());
            
            // Painel da webcam
            // Certifique-se de que o webcamPanel foi inicializado
            if (webcamPanel != null) {
                 janela.add(webcamPanel, BorderLayout.CENTER);
            } else {
                 // Adiciona um placeholder ou mensagem de erro se o painel não foi criado
                 janela.add(new JLabel("Erro ao exibir feed da webcam."), BorderLayout.CENTER);
                 System.err.println("WebcamPanel não inicializado.");
            }
            
            // Painel de botões
            JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.CENTER));
            
            JButton btnCapturar = new JButton("Capturar Foto");
            btnCapturar.addActionListener(e -> capturarFoto(parent));
            painelBotoes.add(btnCapturar);
            
            JButton btnFechar = new JButton("Fechar");
            btnFechar.addActionListener(e -> fecharJanela());
            painelBotoes.add(btnFechar);
            
            janela.add(painelBotoes, BorderLayout.SOUTH);
            janela.pack();
            janela.setLocationRelativeTo(parent);
        }
        
        janela.setVisible(true);
    }
    
    /**
     * Captura uma foto da webcam e salva em arquivo
     * 
     * @param parent Componente pai para exibir diálogos
     * @return Caminho do arquivo salvo ou null em caso de erro
     */
    public String capturarFoto(Component parent) {
        if (webcam == null || !webcam.isOpen()) {
            JOptionPane.showMessageDialog(parent, 
                    "A webcam não está disponível.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return null;
        }
        
        try {
            // Captura a imagem
            BufferedImage imagem = webcam.getImage();
            
            // Cria pasta de capturas se não existir
            File pastaCapturas = new File("capturas");
            if (!pastaCapturas.exists()) {
                pastaCapturas.mkdir();
            }
            
            // Cria nome de arquivo único baseado em data e hora
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String nomeArquivo = "capturas/Captura_" + sdf.format(new Date()) + ".jpg";
            
            // Salva a imagem
            File arquivo = new File(nomeArquivo);
            ImageIO.write(imagem, "JPG", arquivo);
            
            JOptionPane.showMessageDialog(parent, 
                    "Foto capturada e salva em: " + nomeArquivo,
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            
            return nomeArquivo;
        } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, 
                    "Erro ao capturar foto: " + e.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
    
    /**
     * Fecha a janela da webcam
     */
    public void fecharJanela() {
        if (janela != null) {
            janela.setVisible(false);
            // Não descartar a janela, apenas ocultar, para poder reabri-la
            // janela.dispose(); 
        }
    }
    
    /**
     * Libera os recursos da webcam
     */
    public void fechar() {
        fecharJanela();
        // Parar compartilhamento se estiver ativo
        if (isSharing) {
            toggleCompartilharVideo();
        }
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }
}