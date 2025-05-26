@echo off
echo Compilando o projeto...

:: Criar pasta bin se não existir
if not exist "bin" mkdir bin

:: Compilar o projeto
javac --enable-preview --release 22 -cp "lib/*" -d bin src/tieteMonitor/client/*.java src/tieteMonitor/util/*.java src/tieteMonitor/server/*.java

if %errorlevel% equ 0 (
    echo Compilacao concluida com sucesso!
) else (
    echo Erro na compilacao!
)

pause 