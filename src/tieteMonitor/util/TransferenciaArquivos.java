package tieteMonitor.util;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.UUID;

/**
 * Utilitário para transferência de arquivos entre clientes e servidor
 * do sistema de monitoramento do Rio Tietê
 */
public class TransferenciaArquivos {

    private static final int BUFFER_SIZE = 8192; // 8KB de buffer

    /**
     * Envia um arquivo pelo socket
     * @param socket Socket conectado ao destinatário
     * @param arquivo Arquivo a ser enviado
     * @param destinatario Identificador do destinatário (nome do inspetor ou "CENTRAL")
     * @return true se o envio foi bem sucedido, false caso contrário
     */
    public static boolean enviarArquivo(Socket socket, File arquivo, String destinatario) {
        try (
                OutputStream out = socket.getOutputStream();
                DataOutputStream dataOut = new DataOutputStream(out);
                FileInputStream fileIn = new FileInputStream(arquivo)
        ) {
            // Envia metadados do arquivo
            dataOut.writeUTF(destinatario);
            dataOut.writeUTF(arquivo.getName());
            dataOut.writeLong(arquivo.length());

            // Envia o conteúdo do arquivo
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalEnviado = 0;
            long tamanhoTotal = arquivo.length();

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
                totalEnviado += bytesRead;

                // Poderia atualizar uma barra de progresso aqui
                System.out.println("Progresso: " + (totalEnviado * 100 / tamanhoTotal) + "%");
            }

            dataOut.flush();
            return true;

        } catch (IOException e) {
            System.err.println("Erro ao enviar arquivo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recebe um arquivo pelo socket
     * @param socket Socket conectado ao remetente
     * @param diretorioDestino Diretório onde o arquivo será salvo
     * @return Arquivo recebido ou null em caso de erro
     */
    public static File receberArquivo(Socket socket, String diretorioDestino) {
        try (
                InputStream in = socket.getInputStream();
                DataInputStream dataIn = new DataInputStream(in)
        ) {
            // Recebe metadados do arquivo
            String remetente = dataIn.readUTF();
            String nomeArquivo = dataIn.readUTF();
            long tamanhoArquivo = dataIn.readLong();

            // Cria um nome temporário para evitar colisões
            String nomeTemporario = UUID.randomUUID().toString() + "_" + nomeArquivo;
            File arquivoDestino = new File(diretorioDestino, nomeTemporario);

            // Cria o arquivo de destino
            try (FileOutputStream fileOut = new FileOutputStream(arquivoDestino)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalRecebido = 0;

                // Recebe o conteúdo do arquivo
                while (totalRecebido < tamanhoArquivo &&
                        (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, tamanhoArquivo - totalRecebido))) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                    totalRecebido += bytesRead;

                    // Poderia atualizar uma barra de progresso aqui
                    System.out.println("Progresso: " + (totalRecebido * 100 / tamanhoArquivo) + "%");
                }

                fileOut.flush();
            }

            return arquivoDestino;

        } catch (IOException e) {
            System.err.println("Erro ao receber arquivo: " + e.getMessage());
            return null;
        }
    }

    /**
     * Verifica se o arquivo recebido está corrompido através da verificação do tamanho
     * @param arquivo Arquivo recebido
     * @param tamanhoEsperado Tamanho esperado em bytes
     * @return true se o arquivo está íntegro, false caso contrário
     */
    public static boolean verificarIntegridade(File arquivo, long tamanhoEsperado) {
        return arquivo.length() == tamanhoEsperado;
    }
}