# Chat dos Inspetores - Secretaria do Meio Ambiente

Este é um sistema de chat desenvolvido para a Secretaria do Meio Ambiente, permitindo que inspetores troquem informações sobre atividades industriais que podem estar causando poluição no Rio Tietê.

## Funcionalidades

- Chat em tempo real entre inspetores
- Interface gráfica amigável
- Envio de arquivos
- Sistema de nomes de usuário
- Indicador de status de conexão

## Requisitos

- Java 11 ou superior
- Maven

## Como Executar

1. Primeiro, inicie o servidor:
```bash
mvn exec:java -Dexec.mainClass="br.gov.sp.sea.server.ChatServer"
```

2. Em seguida, inicie o cliente (você pode abrir múltiplas instâncias para simular diferentes inspetores):
```bash
mvn javafx:run
```

## Como Usar

1. Digite seu nome no campo "Nome do Inspetor"
2. Clique em "Conectar"
3. Digite suas mensagens no campo de texto e clique em "Enviar" ou pressione Enter
4. Para enviar arquivos, clique no botão "Enviar Arquivo"
5. Para desconectar, clique no botão "Desconectar"

## Estrutura do Projeto

- `src/main/java/br/gov/sp/sea/server/` - Código do servidor
- `src/main/java/br/gov/sp/sea/client/` - Código do cliente
- `src/main/resources/br/gov/sp/sea/client/` - Arquivos de interface gráfica

## Tecnologias Utilizadas

- Java 11
- JavaFX para interface gráfica
- Sockets TCP/IP para comunicação
- Maven para gerenciamento de dependências 