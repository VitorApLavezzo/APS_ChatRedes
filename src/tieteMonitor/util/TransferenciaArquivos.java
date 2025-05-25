package tieteMonitor.util;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.Base64;

/**
 * Utilitário para transferência de arquivos entre clientes e servidor
 * do sistema de monitoramento do Rio Tietê
 */
public class TransferenciaArquivos {

    private static final int BUFFER_SIZE = 8192; // 8KB de buffer

    /**
     * Interface para callback de progresso
     */
    public interface ProgressCallback {
        /**
         * Chamado durante o progresso da transferência
         * @param percentual Percentual de conclusão (0-100)
         * @return true para continuar, false para cancelar
         */
        boolean onProgress(double percentual);
    }

    /**
     * Envia um arquivo para o servidor
     * @param socket Socket de conexão
     * @param arquivo Arquivo a ser enviado
     * @param destinatario Destinatário do arquivo
     * @param remetente Nome do inspetor remetente
     * @return true se o envio foi bem sucedido, false caso contrário
     */
    public static boolean enviarArquivo(Socket socket, File arquivo, String destinatario, String remetente) {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            // Envia o comando de arquivo com nome, destinatário e remetente
            out.writeUTF("ARQUIVO:" + arquivo.getName() + ":" + destinatario + ":" + remetente);
            
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
            return resposta.equals("ARQUIVO_RECEBIDO");
            
        } catch (IOException e) {
            System.err.println("Erro ao enviar arquivo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Recebe um arquivo do servidor
     * @param socket Socket de conexão
     * @param nomeArquivo Nome do arquivo a ser recebido
     * @param pastaDestino Pasta onde o arquivo será salvo
     * @return true se o download foi bem sucedido, false caso contrário
     */
    public static boolean receberArquivo(Socket socket, String nomeArquivo, String pastaDestino) {
        try {
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            
            // Lê o tamanho do arquivo
            long tamanhoArquivo = in.readLong();
            System.out.println("Tamanho do arquivo recebido: " + tamanhoArquivo + " bytes");
            
            // Se o tamanho for 0, o arquivo não existe no servidor
            if (tamanhoArquivo == 0) {
                System.err.println("Arquivo não encontrado no servidor");
                return false;
            }
            
            // Cria o arquivo de destino no local escolhido pelo usuário
            File arquivoDestino = new File(pastaDestino, nomeArquivo);
            System.out.println("Salvando arquivo em: " + arquivoDestino.getAbsolutePath());
            
            // Cria o arquivo e escreve o conteúdo
            try (FileOutputStream fileOut = new FileOutputStream(arquivoDestino)) {
                byte[] buffer = new byte[8192];
                long bytesRestantes = tamanhoArquivo;
                int bytesLidos;
                long totalRecebido = 0;
                
                while (bytesRestantes > 0) {
                    bytesLidos = in.read(buffer, 0, (int) Math.min(buffer.length, bytesRestantes));
                    if (bytesLidos == -1) {
                        System.err.println("Conexão fechada inesperadamente");
                        break;
                    }
                    fileOut.write(buffer, 0, bytesLidos);
                    bytesRestantes -= bytesLidos;
                    totalRecebido += bytesLidos;
                    System.out.println("Recebidos " + totalRecebido + " de " + tamanhoArquivo + " bytes");
                }
                
                // Verifica se todos os bytes foram lidos
                if (bytesRestantes > 0) {
                    System.err.println("Erro: arquivo incompleto. Faltam " + bytesRestantes + " bytes");
                    return false;
                }
                
                fileOut.flush(); // Garante que todos os dados sejam escritos
            }
            
            System.out.println("Arquivo salvo com sucesso em: " + arquivoDestino.getAbsolutePath());
            
            // Envia confirmação para o servidor
            try {
                out.writeUTF("ARQUIVO_RECEBIDO");
                out.flush(); // Garante que a confirmação seja enviada
                System.out.println("Confirmação enviada para o servidor");
            } catch (IOException e) {
                System.err.println("Erro ao enviar confirmação para o servidor: " + e.getMessage());
                return false;
            }
            
            return true;
            
        } catch (IOException e) {
            System.err.println("Erro ao receber arquivo: " + e.getMessage());
            e.printStackTrace();
            return false;
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

    /**
     * Envia um arquivo pelo socket com feedback de progresso
     * @param socket Socket conectado ao destinatário
     * @param arquivo Arquivo a ser enviado
     * @param destinatario Identificador do destinatário (nome do inspetor ou "CENTRAL")
     * @param callback Callback para reportar progresso
     * @return true se o envio foi bem sucedido, false caso contrário
     */
    public static boolean enviarArquivoComProgresso(Socket socket, File arquivo, String destinatario, ProgressCallback callback) {
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

                // Calcula e reporta o progresso
                double percentual = (double) totalEnviado * 100 / tamanhoTotal;
                if (callback != null) {
                    boolean continuar = callback.onProgress(percentual);
                    if (!continuar) {
                        return false; // Transferência cancelada
                    }
                }
            }

            dataOut.flush();
            return true;

        } catch (IOException e) {
            System.err.println("Erro ao enviar arquivo: " + e.getMessage());
            return false;
        }
    }

    /**
     * Envia um arquivo solicitado para o cliente (download)
     * @param socket Socket de conexão
     * @param nomeArquivo Nome do arquivo a ser enviado
     * @param pastaOrigem Pasta onde o arquivo está salvo
     * @return true se o envio foi bem sucedido, false caso contrário
     */
    public static boolean enviarArquivoParaCliente(Socket socket, String nomeArquivo, String pastaOrigem) {
        try {
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            
            // Verifica se o arquivo existe usando caminho absoluto
            File pasta = new File(pastaOrigem);
            File arquivo = new File(pasta, nomeArquivo);
            
            System.out.println("Tentando encontrar arquivo em: " + arquivo.getAbsolutePath());
            
            if (!arquivo.exists() || !arquivo.isFile()) {
                System.err.println("Arquivo não encontrado: " + arquivo.getAbsolutePath());
                out.writeLong(0); // Indica que o arquivo não existe
                return false;
            }
            
            System.out.println("Arquivo encontrado: " + arquivo.getAbsolutePath() + " (tamanho: " + arquivo.length() + " bytes)");
            
            // Envia o tamanho do arquivo
            out.writeLong(arquivo.length());
            out.flush(); // Garante que o tamanho seja enviado
            
            // Envia o conteúdo do arquivo
            try (FileInputStream fileIn = new FileInputStream(arquivo)) {
                byte[] buffer = new byte[8192];
                int bytesLidos;
                long totalEnviado = 0;
                
                while ((bytesLidos = fileIn.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesLidos);
                    totalEnviado += bytesLidos;
                    System.out.println("Enviados " + totalEnviado + " de " + arquivo.length() + " bytes");
                }
                out.flush(); // Garante que todos os dados sejam enviados
            }
            
            // Aguarda confirmação do cliente
            try {
                String confirmacao = in.readUTF();
                System.out.println("Confirmação recebida do cliente: " + confirmacao);
                if (!confirmacao.equals("ARQUIVO_RECEBIDO")) {
                    System.err.println("Cliente não confirmou recebimento do arquivo");
                    return false;
                }
            } catch (IOException e) {
                System.err.println("Erro ao aguardar confirmação do cliente: " + e.getMessage());
                return false;
            }
            
            System.out.println("Arquivo enviado com sucesso e confirmado pelo cliente");
            return true;
            
        } catch (IOException e) {
            System.err.println("Erro ao enviar arquivo: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}