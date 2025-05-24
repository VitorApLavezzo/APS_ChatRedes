package tieteMonitor.util;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Gerenciador de webcam para o sistema de monitoramento do Rio Tietê
 */
public class WebcamManager {
    private Webcam webcam;
    private WebcamPanel webcamPanel;
    private JFrame janela;
    
    /**
     * Inicializa a webcam com resolução padrão
     */
    public WebcamManager() {
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
     * Abre a janela da webcam
     * 
     * @param titulo Título da janela
     * @param parent Componente pai para centralizar a janela
     */
    public void abrirJanela(String titulo, Component parent) {
        if (webcam == null || !webcam.isOpen()) {
            JOptionPane.showMessageDialog(parent, 
                    "Não foi possível acessar a webcam. Verifique se ela está conectada e não está sendo usada por outro aplicativo.",
                    "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (janela == null) {
            janela = new JFrame(titulo);
            janela.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            janela.setLayout(new BorderLayout());
            
            // Painel da webcam
            janela.add(webcamPanel, BorderLayout.CENTER);
            
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
        
        if (!janela.isVisible()) {
            janela.setVisible(true);
        }
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
            janela.dispose();
        }
    }
    
    /**
     * Libera os recursos da webcam
     */
    public void fechar() {
        fecharJanela();
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
    }
}