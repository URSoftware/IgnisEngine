package com.ignis.editor.fx;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.function.Predicate;

/**
 * Painel de Console do editor JavaFX: mostra logs do engine, saida dos scripts e
 * erros de compilacao, classificados por nivel (INFO/AVISO/ERRO) com cor e
 * filtraveis. Captura {@code System.out}/{@code System.err} via "tee" — a saida
 * original do terminal continua intacta; apenas espelhamos cada linha aqui.
 *
 * <p>Aditivo: nada em {@code com.ignis.core} depende desta classe. O tema escuro
 * unificado aplica-se via styleClass {@code ignis-panel} (igual aos outros paineis).
 */
public final class FxConsolePanel extends VBox {

    /** Severidade de uma linha do console. */
    public enum Level { INFO, WARN, ERROR }

    private static final int MAX_LINES = 2000;

    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private final FilteredList<Entry> filtered = new FilteredList<>(entries, e -> true);
    private final ListView<Entry> list = new ListView<>(filtered);

    private final ToggleButton showInfo = new ToggleButton("Info");
    private final ToggleButton showWarn = new ToggleButton("Avisos");
    private final ToggleButton showErr = new ToggleButton("Erros");
    private final CheckBox autoScroll = new CheckBox("Auto-scroll");
    private final Label counter = new Label();

    private PrintStream originalOut;
    private PrintStream originalErr;
    private boolean capturing;

    private static final class Entry {
        final Level level;
        final String text;
        Entry(Level level, String text) { this.level = level; this.text = text; }
    }

    public FxConsolePanel() {
        setSpacing(4);
        setPadding(new Insets(4));
        getStyleClass().add("ignis-panel");

        Label title = new Label("Console");
        title.getStyleClass().add("panel-title");

        showInfo.setSelected(true);
        showWarn.setSelected(true);
        showErr.setSelected(true);
        showInfo.setTooltip(new Tooltip("Mostrar mensagens de informacao"));
        showWarn.setTooltip(new Tooltip("Mostrar avisos"));
        showErr.setTooltip(new Tooltip("Mostrar erros"));
        showInfo.setOnAction(e -> applyFilter());
        showWarn.setOnAction(e -> applyFilter());
        showErr.setOnAction(e -> applyFilter());

        autoScroll.setSelected(true);

        Button clear = new Button("Limpar");
        clear.setOnAction(e -> clear());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        counter.getStyleClass().add("toolbar-label");

        HBox bar = new HBox(6, title, new Region(), showInfo, showWarn, showErr,
                        autoScroll, spacer, counter, clear);
                bar.setAlignment(Pos.CENTER_LEFT);

                list.setFocusTraversable(false);
                list.setCellFactory(lv -> new ConsoleCell());

                // Duplo clique em linha de erro abre o script na linha correspondente
                list.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2) {
                        Entry selectedEntry = list.getSelectionModel().getSelectedItem();
                        if (selectedEntry != null && selectedEntry.level == Level.ERROR) {
                            openScriptAtErrorLine(selectedEntry.text);
                        }
                    }
                });

                VBox.setVgrow(list, Priority.ALWAYS);

                getChildren().addAll(bar, list);
                updateCounter();

                // Registrar ouvinte do logger privado e centralizado do IgnisEngine
                com.ignis.core.IgnisLogger.addListener((level, message) -> {
                    Level fxLevel;
                    switch (level) {
                        case ERROR: fxLevel = Level.ERROR; break;
                        case WARN:  fxLevel = Level.WARN; break;
                        default:    fxLevel = Level.INFO; break;
                    }
                    log(fxLevel, message);
                });
            }

            // Tenta extrair nome do script e número da linha de uma mensagem de erro
            // Ex.: "PlayerMovement.java -> Line 42: cannot find symbol"
            private void openScriptAtErrorLine(String errorLine) {
                try {
                    // Padrão: "ScriptName.java -> Line N: mensagem"
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([A-Za-z0-9_]+)\\.java\\s*->\\s*Line\\s*(\\d+)");
                    java.util.regex.Matcher matcher = pattern.matcher(errorLine);
                    if (matcher.find()) {
                        String scriptName = matcher.group(1);
                        int lineNumber = Integer.parseInt(matcher.group(2));

                        // Obter a instância do editor atual. FxConsolePanel é criado
                        // pelo IgnisEditorApp; usamos o singleton acessível estaticamente.
                        com.ignis.editor.fx.IgnisEditorApp editorApp = com.ignis.editor.fx.IgnisEditorApp.getInstance();
                        if (editorApp != null) {
                            editorApp.openScriptAtLine(scriptName, lineNumber);
                        }
                    }
                } catch (Exception ex) {
                    // Silenciosamente ignorar falhas de parsing/navegação
                    com.ignis.core.IgnisLogger.warn("Nao foi possivel abrir script na linha do erro: " + ex.getMessage());
                }
            }

    // ---------------- API publica ----------------

    /** Registra uma linha com nivel explicito (uso programatico, ex.: build). */
    public void log(Level level, String message) {
        if (message == null) return;
        for (String line : message.split("\n", -1)) {
            appendOnFx(level, line.replace("\r", ""));
        }
    }

    /** Limpa o console. */
    public void clear() {
        entries.clear();
        updateCounter();
    }

    /**
     * Nao faz mais nada pois agora o terminal da ferramenta e isolado (privado)
     * e os logs sao direcionados explicitamente via IgnisLogger.
     */
    public void startCapture() {
        capturing = true;
    }

    /** Restaura logs originais (no-op). */
    public void stopCapture() {
        capturing = false;
    }

    // Heuristica de severidade: stderr e erro por padrao (mas "warning" vira aviso);
    // stdout e info, exceto quando o texto sinaliza erro/excecao/falha ou aviso.
    private Level classify(boolean errStream, String line) {
        String l = line.toLowerCase();
        if (errStream) {
            return l.contains("warn") ? Level.WARN : Level.ERROR;
        }
        if (l.contains("error") || l.contains("exception") || l.contains("failed")
                || l.contains("falha") || l.contains("erro")) {
            return Level.ERROR;
        }
        if (l.contains("warn") || l.contains("aviso")) return Level.WARN;
        return Level.INFO;
    }

    private void appendOnFx(Level level, String line) {
        if (line == null || line.isEmpty()) return;
        Platform.runLater(() -> add(new Entry(level, line)));
    }

    private void add(Entry e) {
        entries.add(e);
        int over = entries.size() - MAX_LINES;
        if (over > 0) entries.remove(0, over);
        updateCounter();
        if (autoScroll.isSelected() && !filtered.isEmpty()) {
            list.scrollTo(filtered.size() - 1);
        }
    }

    private void applyFilter() {
        Predicate<Entry> p = e -> {
            switch (e.level) {
                case WARN:  return showWarn.isSelected();
                case ERROR: return showErr.isSelected();
                default:    return showInfo.isSelected();
            }
        };
        filtered.setPredicate(p);
        if (autoScroll.isSelected() && !filtered.isEmpty()) list.scrollTo(filtered.size() - 1);
    }

    private void updateCounter() {
        int errors = 0, warns = 0;
        for (Entry e : entries) {
            if (e.level == Level.ERROR) errors++;
            else if (e.level == Level.WARN) warns++;
        }
        counter.setText(errors + " erro(s), " + warns + " aviso(s)");
    }

    // Celula colorida por nivel; cor inline para funcionar em qualquer tema (claro/escuro).
    private static final class ConsoleCell extends ListCell<Entry> {
        @Override protected void updateItem(Entry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle(null);
                return;
            }
            setText(item.text);
            String base = "-fx-font-family: 'Consolas','Menlo','monospace'; -fx-font-size: 12px;"
                    + " -fx-background-color: transparent;";
            switch (item.level) {
                case ERROR: setStyle(base + " -fx-text-fill: #e06c75;"); break;
                case WARN:  setStyle(base + " -fx-text-fill: #d9a441;"); break;
                default:    setStyle(base); // INFO: cor de texto herda do tema
            }
        }
    }
}
