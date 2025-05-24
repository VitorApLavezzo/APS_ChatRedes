@echo off
echo Iniciando o servidor...

:: Executar o servidor com preview features habilitadas
java --enable-preview -cp "bin;lib/*" tieteMonitor.server.ServidorMonitoramento

pause 