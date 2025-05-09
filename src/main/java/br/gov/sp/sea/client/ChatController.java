package br.gov.sp.sea.client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.io.*;
import java.net.*;
import java.nio.file.Files;

public class ChatController {
    @FXML private TextField usernameField;
    @FXML private TextField serverField;
    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private Button connectButton;
    @FXML private Button sendButton;
    @FXML private Button fileButton;
    @FXML private Label statusLabel;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;

    @FXML
    private void handleConnect() {
        if (!connected) {
            try {
                String username = usernameField.getText().trim();
                String serverAddress = serverField.getText().trim();
                
                if (username.isEmpty()) {
                    showAlert("Erro", "Por favor, insira seu nome.");
                    return;
                }
                
                if (serverAddress.isEmpty()) {
                    serverAddress = "localhost"; // Usa localhost como padrão se nenhum endereço for fornecido
                }

                socket = new Socket(serverAddress, 5000);
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Envia o nome do usuário
                out.println(username);

                // Inicia thread para receber mensagens
                new Thread(this::receiveMessages).start();

                connected = true;
                connectButton.setText("Desconectar");
                statusLabel.setText("Conectado a " + serverAddress);
                messageField.setDisable(false);
                sendButton.setDisable(false);
                fileButton.setDisable(false);
                usernameField.setDisable(true);
                serverField.setDisable(true);

            } catch (IOException e) {
                showAlert("Erro de Conexão", "Não foi possível conectar ao servidor: " + e.getMessage());
            }
        } else {
            disconnect();
        }
    }

    @FXML
    private void handleSendMessage() {
        if (connected && !messageField.getText().trim().isEmpty()) {
            out.println(messageField.getText());
            messageField.clear();
        }
    }

    @FXML
    private void handleFileUpload() {
        if (!connected) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Selecione um arquivo para enviar");
        File file = fileChooser.showOpenDialog(null);

        if (file != null) {
            try {
                // Envia comando especial para indicar que é um arquivo
                out.println("FILE:" + file.getName());
                
                // Envia o conteúdo do arquivo
                byte[] fileContent = Files.readAllBytes(file.toPath());
                out.println(new String(fileContent));
                
                chatArea.appendText("Arquivo enviado: " + file.getName() + "\n");
            } catch (IOException e) {
                showAlert("Erro", "Erro ao enviar arquivo: " + e.getMessage());
            }
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String finalMessage = message;
                javafx.application.Platform.runLater(() -> {
                    chatArea.appendText(finalMessage + "\n");
                });
            }
        } catch (IOException e) {
            if (connected) {
                javafx.application.Platform.runLater(() -> {
                    showAlert("Erro", "Conexão perdida com o servidor");
                    disconnect();
                });
            }
        }
    }

    private void disconnect() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        connected = false;
        connectButton.setText("Conectar");
        statusLabel.setText("Desconectado");
        messageField.setDisable(true);
        sendButton.setDisable(true);
        fileButton.setDisable(true);
        usernameField.setDisable(false);
        serverField.setDisable(false);
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
} 