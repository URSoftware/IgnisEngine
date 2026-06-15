@echo off
REM ============================================================================
REM  IgnisEngine - Editor JavaFX (em migracao)
REM  Executa: mvnw javafx:run  (mainClass com.ignis.editor.fx.IgnisEditorApp)
REM
REM  Observacao: o JAVA_HOME desta maquina costuma apontar para o SDK do JavaFX
REM  (que NAO e um JDK). Este script detecta um JDK valido antes de rodar.
REM ============================================================================
setlocal EnableDelayedExpansion

REM Vai para a pasta deste .bat (raiz do projeto)
cd /d "%~dp0"

REM Se JAVA_HOME atual nao tiver javac, procura um JDK instalado.
if not exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVA_HOME="
    for %%J in (
        "C:\Program Files\Java\jdk-24"
        "C:\Program Files\Java\jdk-25"
        "C:\Program Files\Java\jdk-21"
        "C:\Program Files\Java\jdk-17"
        "C:\Program Files\Eclipse Adoptium\jdk-17"
        "C:\Program Files\Microsoft\jdk-17"
    ) do (
        if not defined JAVA_HOME if exist "%%~J\bin\javac.exe" set "JAVA_HOME=%%~J"
    )
)

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo [ERRO] Nenhum JDK valido encontrado.
    echo        Defina JAVA_HOME para um JDK 17+ e tente novamente.
    pause
    exit /b 1
)

echo Usando JAVA_HOME=%JAVA_HOME%
echo Iniciando o editor JavaFX (a 1a execucao baixa dependencias)...
echo.

call "%~dp0mvnw.cmd" javafx:run
set "EXITCODE=%ERRORLEVEL%"

if not "%EXITCODE%"=="0" (
    echo.
    echo [FALHA] Build/execucao terminou com erro ^(codigo %EXITCODE%^).
    pause
)

endlocal
exit /b %EXITCODE%
