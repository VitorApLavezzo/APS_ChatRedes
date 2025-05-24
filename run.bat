@echo off
echo Executando o programa...

:: Executar o programa com preview features habilitadas
java --enable-preview -cp "bin;lib/*" tieteMonitor.client.ClienteMonitoramento

pause 