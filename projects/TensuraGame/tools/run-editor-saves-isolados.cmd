@echo off
REM ============================================================================
REM  TensuraGame - launcher de QA com diretorio de save ISOLADO (tarefa #21)
REM
REM  Sobe o editor com TENSURA_SAVE_DIR fora de C:, de modo que nenhum smoke
REM  toque em %LOCALAPPDATA%\TensuraGame\saves. A variavel e herdada pelo
REM  processo filho, entao nao e preciso mexer no pom nem no launcher padrao.
REM
REM  Uso:
REM    run-editor-saves-isolados.cmd                 diretorio unico por execucao
REM    run-editor-saves-isolados.cmd <dir absoluto>  diretorio informado
REM    run-editor-saves-isolados.cmd --prepare-only  valida e cria, nao abre editor
REM    run-editor-saves-isolados.cmd --no-pause      nunca pausa no fim
REM
REM  Este launcher NUNCA apaga, move ou restaura save nenhum.
REM ============================================================================
setlocal EnableExtensions EnableDelayedExpansion

REM Guardado ANTES do parser: `shift` desloca %0 junto, e depois disso %~dp0 deixa de
REM apontar para este script.
set "SCRIPT_DIR=%~dp0"

set "PREPARE_ONLY=0"
set "NO_PAUSE=0"
set "INPUT_DIR="
set "ERRMSG="

:parse_args
if "%~1"=="" goto args_parsed
if /I "%~1"=="--prepare-only" (set "PREPARE_ONLY=1" & shift & goto parse_args)
if /I "%~1"=="--no-pause" (set "NO_PAUSE=1" & shift & goto parse_args)
if not defined INPUT_DIR (set "INPUT_DIR=%~1" & shift & goto parse_args)
set "ERRMSG=Argumento desconhecido: %~1"
goto abort

:args_parsed
if defined INPUT_DIR (call :use_given_directory) else (call :pick_unique_directory)
if errorlevel 1 goto abort

REM Canonicaliza ANTES de qualquer decisao: e a canonicalizacao que revela que um
REM caminho relativo como "qa\saves" na verdade cai na unidade corrente.
for %%I in ("!SAVE_DIR!") do set "SAVE_DIR=%%~fI"
set "SAVE_ROOT=!SAVE_DIR:~0,2!"

if not "!SAVE_DIR:~1,1!"==":" (
    set "ERRMSG=Caminho sem letra de unidade apos canonicalizacao: !SAVE_DIR!"
    goto abort
)
if /I "!SAVE_ROOT!"=="C:" (
    set "ERRMSG=Recusado: !SAVE_DIR! fica em C:, onde vivem os saves reais."
    goto abort
)
if not exist "!SAVE_ROOT!\" (
    set "ERRMSG=Unidade !SAVE_ROOT! nao existe nesta maquina."
    goto abort
)
if exist "!SAVE_DIR!\" (
    set "ERRMSG=Diretorio ja existe e seria reaproveitado: !SAVE_DIR!"
    goto abort
)

mkdir "!SAVE_DIR!" 2>nul
if errorlevel 1 (
    set "ERRMSG=mkdir falhou em !SAVE_DIR! (permissao, unidade cheia ou caminho invalido)."
    goto abort
)
if not exist "!SAVE_DIR!\" (
    set "ERRMSG=mkdir nao criou !SAVE_DIR!."
    goto abort
)

set "TENSURA_SAVE_DIR=!SAVE_DIR!"
echo TENSURA_SAVE_DIR=!TENSURA_SAVE_DIR!

if "%PREPARE_ONLY%"=="1" (
    echo Modo --prepare-only: diretorio validado e criado, nenhum editor foi aberto.
    call :maybe_pause
    endlocal & exit /b 0
)

echo Subindo o editor; os saves deste processo ficam fora de C:.
echo.
call "%SCRIPT_DIR%..\..\..\run-editor-javafx.bat"
set "EXITCODE=%ERRORLEVEL%"
if not "%EXITCODE%"=="0" echo [FALHA] O editor terminou com codigo %EXITCODE%.
call :maybe_pause
endlocal & exit /b %EXITCODE%

:abort
echo.
echo [ERRO] !ERRMSG!
echo        Nenhum save foi criado, movido ou apagado.
echo.
call :maybe_pause
endlocal & exit /b 1

:use_given_directory
REM Caminho informado tem de ser absoluto: so assim o operador sabe onde vai gravar.
set "SECOND=!INPUT_DIR:~1,1!"
set "THIRD=!INPUT_DIR:~2,1!"
set "PREFIX=!INPUT_DIR:~0,2!"
if "!PREFIX!"=="\\" goto given_ok
if not "!SECOND!"==":" goto given_relative
if "!THIRD!"=="\" goto given_ok
if "!THIRD!"=="/" goto given_ok
:given_relative
set "ERRMSG=Caminho relativo recusado: !INPUT_DIR!. Informe um caminho absoluto, ex.: E:\TensuraGame-qa\saves."
exit /b 1
:given_ok
set "SAVE_DIR=!INPUT_DIR!"
exit /b 0

:pick_unique_directory
REM Preferencia E:, fallback D:. Nunca C:.
set "PICKED_ROOT="
if exist "E:\" set "PICKED_ROOT=E:"
if not defined PICKED_ROOT if exist "D:\" set "PICKED_ROOT=D:"
if not defined PICKED_ROOT (
    set "ERRMSG=Nenhuma unidade E: ou D: disponivel para isolar os saves. Informe um caminho absoluto fora de C:."
    exit /b 1
)
set "STAMP=%DATE%%TIME%"
set "STAMP=!STAMP:/=!"
set "STAMP=!STAMP::=!"
set "STAMP=!STAMP:.=!"
set "STAMP=!STAMP:,=!"
set "STAMP=!STAMP:-=!"
set "STAMP=!STAMP: =!"
set "SAVE_DIR=!PICKED_ROOT!\TensuraGame-qa\run-!STAMP!-!RANDOM!\saves"
exit /b 0

:maybe_pause
if "%NO_PAUSE%"=="1" goto :eof
echo !CMDCMDLINE! | find /I "/c" >nul && goto :eof
pause
goto :eof
