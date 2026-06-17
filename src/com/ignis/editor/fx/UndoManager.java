package com.ignis.editor.fx;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pilha de desfazer/refazer do editor JavaFX (padrao Command).
 *
 * <p>Cada {@link Command} carrega duas acoes: {@code undo} (reverte) e {@code redo}
 * (reaplica). O chamador executa a mutacao e registra o comando via
 * {@link #push(String, Runnable, Runnable)} — a acao ja esta aplicada, entao
 * registrar nao a re-executa; {@code redo} so roda em refacoes futuras.
 *
 * <p>Aditivo: nada em {@code com.ignis.core}. As acoes operam sobre o estado vivo
 * do editor (Game + UI) atraves das lambdas fornecidas.
 */
public final class UndoManager {

    /** Um passo reversivel, com rotulo para a UI. */
    public static final class Command {
        final String name;
        final Runnable undo;
        final Runnable redo;
        Command(String name, Runnable undo, Runnable redo) {
            this.name = name;
            this.undo = undo;
            this.redo = redo;
        }
    }

    private static final int MAX = 100; // limite de historico (memoria limitada)

    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();
    private Runnable onChange;

    /** Callback chamado sempre que as pilhas mudam (para atualizar a UI). */
    public void setOnChange(Runnable r) { this.onChange = r; }

    /**
     * Registra um comando JA executado pelo chamador. Limpa a pilha de refazer
     * (novo ramo de historico) e descarta o mais antigo se exceder o limite.
     */
    public void push(String name, Runnable undoAction, Runnable redoAction) {
        undoStack.push(new Command(name, undoAction, redoAction));
        while (undoStack.size() > MAX) undoStack.removeLast();
        redoStack.clear();
        fire();
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    public String peekUndoName() { return undoStack.isEmpty() ? null : undoStack.peek().name; }
    public String peekRedoName() { return redoStack.isEmpty() ? null : redoStack.peek().name; }

    public void undo() {
        if (undoStack.isEmpty()) return;
        Command c = undoStack.pop();
        c.undo.run();
        redoStack.push(c);
        fire();
    }

    public void redo() {
        if (redoStack.isEmpty()) return;
        Command c = redoStack.pop();
        c.redo.run();
        undoStack.push(c);
        fire();
    }

    /** Esvazia o historico (ex: ao trocar/fechar projeto — comandos viram obsoletos). */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
        fire();
    }

    private void fire() {
        if (onChange != null) onChange.run();
    }
}
