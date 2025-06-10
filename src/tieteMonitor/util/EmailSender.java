package tieteMonitor.util;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.Properties;

/**
 * Utilitário para envio de e-mails no sistema de monitoramento do Rio Tietê
 */
public class EmailSender {
    private static final String HOST = "smtp.gmail.com";
    private static final int PORT = 587;
    
    /**
     * @param remetente Email do remetente
     * @param senha Senha do remetente (ou senha de app para Gmail)
     * @param destinatario Email do destinatário
     * @param assunto Assunto do e-mail
     * @param corpo Corpo do e-mail
     * @return true se o envio foi bem sucedido, false caso contrário
     */
    public static boolean enviarEmail(String remetente, String senha, String destinatario, String assunto, String corpo) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", HOST);
        props.put("mail.smtp.port", PORT);
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(remetente, senha);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(remetente));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            message.setSubject(assunto);
            message.setText(corpo);
            Transport.send(message);
            return true;
        } catch (MessagingException e) {
            System.err.println("Erro ao enviar e-mail: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * @param remetente Email do remetente
     * @param senha Senha do remetente
     * @param destinatario Email do destinatário
     * @param assunto Assunto do e-mail
     * @param corpo Corpo do e-mail
     * @param caminhoAnexo Caminho do arquivo a ser anexado
     * @param nomeAnexo Nome do anexo
     * @return true se o envio foi bem sucedido, false caso contrário
     */
    public static boolean enviarEmailComAnexo(String remetente, String senha, String destinatario, 
                                             String assunto, String corpo, String caminhoAnexo, String nomeAnexo) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", HOST);
        props.put("mail.smtp.port", PORT);
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(remetente, senha);
            }
        });
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(remetente));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(destinatario));
            message.setSubject(assunto);
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setText(corpo);
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(caminhoAnexo);
            attachmentPart.setFileName(nomeAnexo);
            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(messageBodyPart);
            multipart.addBodyPart(attachmentPart);
            message.setContent(multipart);
            Transport.send(message);
            return true;
        } catch (Exception e) {
            System.err.println("Erro ao enviar e-mail com anexo: " + e.getMessage());
            return false;
        }
    }
}