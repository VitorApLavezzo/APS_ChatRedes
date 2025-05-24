@echo off
echo Baixando dependencias...

:: Criar pasta lib se não existir
if not exist "lib" mkdir lib

:: Baixar FlatLaf
curl -L "https://repo1.maven.org/maven2/com/formdev/flatlaf/3.4/flatlaf-3.4.jar" -o "lib/flatlaf-3.4.jar"

:: Baixar JavaMail e suas dependências
curl -L "https://repo1.maven.org/maven2/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar" -o "lib/javax.mail-1.6.2.jar"
curl -L "https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/activation-1.1.1.jar" -o "lib/activation-1.1.1.jar"

:: Baixar Webcam Capture e suas dependências
curl -L "https://repo1.maven.org/maven2/com/github/sarxos/webcam-capture/0.3.12/webcam-capture-0.3.12.jar" -o "lib/webcam-capture-0.3.12.jar"
curl -L "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar" -o "lib/slf4j-api-1.7.36.jar"
curl -L "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar" -o "lib/slf4j-simple-1.7.36.jar"

echo Dependencias baixadas com sucesso!
pause 