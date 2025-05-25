# TieteMonitor

Um sistema de monitoramento e chat em rede desenvolvido em Java.

## 📋 Descrição

O TieteMonitor é um projeto que implementa um sistema de monitoramento com funcionalidades de chat em rede, desenvolvido em Java utilizando Maven como gerenciador de dependências.

## 🚀 Tecnologias Utilizadas

- Java 11
- Maven
- FlatLaf (Interface gráfica moderna)
- JavaMail API
- JUnit (para testes)
- Ngrok (para tunelamento TCP)

## 📦 Estrutura do Projeto

```
APS_ChatRedes/
├── src/                    # Código fonte
│   ├── resources/         # Recursos do projeto
│   └── tieteMonitor/      # Código principal
│       ├── client/        # Componentes do cliente
│       │   ├── ClienteMonitoramento.java    # Interface principal do cliente
│       │   └── ChatInspetores.java          # Sistema de chat entre inspetores
│       ├── server/        # Componentes do servidor
│       │   └── ServidorMonitoramento.java   # Servidor principal
│       └── util/          # Utilitários
│           ├── TransferenciaArquivos.java   # Gerenciamento de transferência de arquivos
│           ├── MulticastManager.java        # Gerenciamento de comunicação multicast
│           └── EmailSender.java             # Sistema de envio de emails
├── bin/                   # Arquivos compilados
├── lib/                   # Bibliotecas externas
├── ngrok/                 # Configurações do ngrok
└── pom.xml               # Configuração do Maven
```

## 🛠️ Requisitos

- Java 11 ou superior
- Maven
- Conexão com internet
- Ngrok (para conexão remota)

## ⚙️ Instalação

1. Clone o repositório
2. Execute o script `download_deps.bat` para baixar as dependências
3. Execute o script `compile.bat` para compilar o projeto
4. Configure o ngrok para tunelamento TCP na porta 12345

## 🚀 Executando o Projeto

### Configuração do Ngrok
Para permitir conexões remotas, é necessário configurar o ngrok para fazer o tunelamento TCP na porta 12345. Execute o seguinte comando no diretório do ngrok:

```bash
ngrok tcp 12345
```

### Para iniciar o servidor:
```bash
run_server.bat
```

### Para iniciar o cliente:
```bash
run.bat
```

## 📝 Scripts Disponíveis

- `compile.bat`: Compila o projeto
- `download_deps.bat`: Baixa as dependências necessárias
- `run_server.bat`: Inicia o servidor
- `run.bat`: Inicia o cliente

## 📄 Licença

Este projeto está sob a licença MIT. Veja o arquivo [LICENSE](LICENSE) para mais detalhes.
