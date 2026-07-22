package com.ignis.mcp;

import com.ignis.core.Game;
import com.ignis.core.Input;

import java.awt.event.KeyEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ferramentas MCP de teste de runtime por agente (roadmap de producao agentica, P0
 * "input e tempo determinísticos"): injeta teclado/mouse sem depender do foco da
 * janela, avanca a simulacao quadro-a-quadro de forma determinista e pausa/retoma o
 * mundo. Fecha a lacuna do TensuraGame em que o QA narrativo precisava de bypass em
 * scripts para exercitar o jogo.
 *
 * <p>Fluxo determinista: {@code play_game} &rarr; {@code pause_game} &rarr;
 * {@code inject_input} &rarr; {@code advance_frames} &rarr; ler estado
 * ({@code list_runtime_objects}/{@code capture_viewport}) &rarr;
 * {@code release_all_inputs} &rarr; {@code stop_game}.</p>
 */
final class RuntimeTestingTools {

    private final IgnisToolRegistry reg;

    RuntimeTestingTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    // Acoes semanticas -> tecla VK, seguindo a convencao de movimento do Input da
    // engine (WASD/setas). Permite o agente pensar em "up"/"left" em vez de codigos.
    private static final Map<String, Integer> ACTION_KEYS = buildActionKeys();

    private static Map<String, Integer> buildActionKeys() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("up", KeyEvent.VK_W);
        m.put("down", KeyEvent.VK_S);
        m.put("left", KeyEvent.VK_A);
        m.put("right", KeyEvent.VK_D);
        m.put("jump", KeyEvent.VK_SPACE);
        m.put("confirm", KeyEvent.VK_ENTER);
        m.put("cancel", KeyEvent.VK_ESCAPE);
        m.put("interact", KeyEvent.VK_E);
        m.put("attack", KeyEvent.VK_J);
        return m;
    }

    void registerAll() {
        registerInjectInput();
        registerReleaseAllInputs();
        registerAdvanceFrames();
        registerPauseResume();
        registerRunInputTape();
        registerMouse();
    }

    private void registerInjectInput() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("action", "Acao semantica: up/down/left/right/jump/confirm/cancel/interact/attack. "
                + "Alternativa a 'key'.");
        props.put("key", "Tecla crua: nome VK (ex: VK_SPACE, SPACE, A) ou codigo numerico. Alternativa a 'action'.");
        props.put("state", "'down' (pressionar) ou 'up' (soltar). Padrao: down.");
        props.put("mouseButton", "Em vez de tecla: 'left'/'right'/'middle' para um botao do mouse.");
        props.put("x", "Opcional: reposiciona o cursor virtual em X (pixels de tela) antes de aplicar. "
                + "Scripts leem via Input.getMouseX(). Para CLICAR num botao de UI use click_ui.");
        props.put("y", "Opcional: reposiciona o cursor virtual em Y (pixels de tela) antes de aplicar.");
        props.put("durationFrames", "Segura por N frames e solta sozinho (press -> avanca N passos -> release). "
                + "Requer o mundo em Play/pausado. Ignora 'state'.");
        reg.add("inject_input",
            "Injeta teclado ou mouse na simulacao como se o jogador tivesse pressionado, sem depender do foco "
            + "da janela. A tecla vira 'just pressed' no proximo advance_frames. Com durationFrames, segura e "
            + "solta sozinho avancando a simulacao. Pareie com pause_game + advance_frames para QA "
            + "determinista; lembre de release_all_inputs no fim.",
            IgnisToolRegistry.schemaWith(props, List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                String state = args.optString("state", "down").trim().toLowerCase(Locale.ROOT);
                boolean pressed = !state.equals("up");
                int hold = Math.max(0, Math.min(600, args.optInt("durationFrames", 0)));

                // Reposiciona o cursor virtual antes de aplicar (posicao pura; para
                // clicar num widget de UI use click_ui, que roteia press+release).
                if (args.has("x") && args.has("y")) {
                    Input.injectMouseMove(args.optInt("x"), args.optInt("y"));
                }

                String mouseBtn = args.optString("mouseButton", "").trim().toLowerCase(Locale.ROOT);
                if (!mouseBtn.isEmpty()) {
                    int b = switch (mouseBtn) {
                        case "left" -> 1;
                        case "middle" -> 2;
                        case "right" -> 3;
                        default -> -1;
                    };
                    if (b < 0) return "Erro: mouseButton deve ser left, middle ou right.";
                    if (hold > 0) {
                        return holdAndRelease("mouse " + mouseBtn,
                                () -> Input.injectMouseButton(b, true),
                                () -> Input.injectMouseButton(b, false), hold);
                    }
                    Input.injectMouseButton(b, pressed);
                    return "Mouse " + mouseBtn + " -> " + (pressed ? "down" : "up") + " (aplica no proximo frame).";
                }

                Integer keyCode = resolveKey(args);
                if (keyCode == null) {
                    return "Erro: informe 'action' (up/down/left/right/jump/...), 'key' (VK_*/codigo) ou 'mouseButton'.";
                }
                if (hold > 0) {
                    int kc = keyCode;
                    return holdAndRelease("tecla " + KeyEvent.getKeyText(kc),
                            () -> Input.injectKey(kc, true),
                            () -> Input.injectKey(kc, false), hold);
                }
                Input.injectKey(keyCode, pressed);
                return "Tecla " + KeyEvent.getKeyText(keyCode) + " -> " + (pressed ? "down" : "up")
                        + " (vira 'just pressed' no proximo advance_frames).";
            });
    }

    // Pressiona, avanca 'frames' passos deterministas e solta — mesmo se um tick de
    // script lancar, o release acontece (regra do roadmap: ferramentas de teste
    // restauram o input apos excecao).
    private String holdAndRelease(String what, Runnable press, Runnable release, int frames) {
        if (reg.liveGame.getGameState() == Game.GameState.EDITING) {
            return "Erro: durationFrames requer o mundo em Play ou pausado (chame play_game antes).";
        }
        press.run();
        int done = 0;
        RuntimeException failure = null;
        try {
            for (; done < frames; done++) {
                reg.liveGame.stepSimulationOnce();
            }
        } catch (RuntimeException ex) {
            failure = ex;
        } finally {
            release.run();
            // Promove o release para o input nao ficar preso no estado dos scripts.
            try {
                reg.liveGame.stepSimulationOnce();
            } catch (RuntimeException ignore) { /* ja reportamos a falha original */ }
            if (reg.refreshHook != null) reg.refreshHook.run();
        }
        String base = "Segurou " + what + " por " + done + "/" + frames + " frame(s) e soltou.";
        if (failure != null) {
            return base + " INTERROMPIDO no frame " + done + ": " + failure.getMessage();
        }
        return base;
    }

    // Resolve o keyCode a partir de 'action' (semantica) ou 'key' (VK_*/nome/codigo).
    private Integer resolveKey(org.json.JSONObject args) {
        String action = args.optString("action", "").trim().toLowerCase(Locale.ROOT);
        if (!action.isEmpty()) return ACTION_KEYS.get(action);

        String key = args.optString("key", "").trim();
        if (key.isEmpty()) return null;
        // Codigo numerico direto.
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignore) { /* nao e numero */ }
        // Nome VK: aceita "VK_SPACE", "SPACE" ou uma unica letra/digito.
        String vk = key.toUpperCase(Locale.ROOT);
        if (!vk.startsWith("VK_")) vk = "VK_" + vk;
        try {
            java.lang.reflect.Field f = KeyEvent.class.getField(vk);
            return f.getInt(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void registerReleaseAllInputs() {
        reg.add("release_all_inputs",
            "Solta TODAS as teclas e botoes do mouse (zera o estado de input). Chame ao fim de uma sessao de "
            + "teste para nenhuma tecla ficar 'presa'.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                Input.resetAll();
                return "Input zerado (todas as teclas/botoes soltos).";
            });
    }

    private void registerAdvanceFrames() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("count", "Quantos passos de simulacao avancar (1-600; padrao 1).");
        props.put("fixedDelta", "Informativo: a simulacao e fixa em 1/60s por passo (a engine ignora outros valores).");
        reg.add("advance_frames",
            "Avanca a simulacao N passos de 1/60s de forma determinista (mesmo com o mundo pausado). Use apos "
            + "inject_input para aplicar o input e observar o resultado quadro-a-quadro. Requer o mundo em Play "
            + "ou pausado (play_game antes).",
            IgnisToolRegistry.schemaWith(props, List.of()),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                Game.GameState st = reg.liveGame.getGameState();
                if (st == Game.GameState.EDITING) {
                    return "Erro: o mundo nao esta em Play. Chame play_game (e opcionalmente pause_game) antes de "
                            + "advance_frames.";
                }
                int count = Math.max(1, Math.min(600, args.optInt("count", 1)));
                int done = 0;
                RuntimeException failure = null;
                try {
                    for (; done < count; done++) {
                        reg.liveGame.stepSimulationOnce();
                    }
                } catch (RuntimeException ex) {
                    // Um tick de script pode lancar: paramos, mas garantimos o refresh e
                    // relatamos ate onde avancamos (o estado do mundo ja foi restaurado
                    // por stepSimulationOnce). Nao deixa a sessao de teste num limbo.
                    failure = ex;
                } finally {
                    if (reg.refreshHook != null) reg.refreshHook.run();
                }
                String base = "Avancados " + done + "/" + count + " frame(s). Estado: "
                        + reg.liveGame.getGameState() + ". Objetos na cena: "
                        + reg.liveGame.getEntities().size() + ".";
                if (failure != null) {
                    return base + " INTERROMPIDO no frame " + done + ": " + failure.getMessage();
                }
                return base;
            });
    }

    private void registerPauseResume() {
        reg.add("pause_game",
            "Pausa a simulacao (o mundo para de avancar sozinho, mas continua desenhado). Base do teste "
            + "determinista: pause e use advance_frames para andar passo a passo.",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getGameState() == Game.GameState.EDITING) {
                    return "Erro: nada para pausar (mundo em edicao). Chame play_game antes.";
                }
                reg.liveGame.pauseWorld();
                return "Mundo pausado. Use advance_frames para avancar passo a passo, ou resume_game para retomar.";
            });

        reg.add("resume_game",
            "Retoma a simulacao pausada (volta a avancar sozinha a 60Hz).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                reg.liveGame.resumeWorld();
                return "Mundo retomado (estado: " + reg.liveGame.getGameState() + ").";
            });
    }

    private void registerRunInputTape() {
        Map<String, String> props = new LinkedHashMap<>();
        props.put("tape", "Array JSON de eventos: [{\"at\": frame, \"action\"|\"key\"|\"mouseButton\": ..., "
                + "\"state\": \"down\"|\"up\"}]. 'at' e o frame (0-based) em que o evento e injetado.");
        props.put("maxFrames", "Ate onde avancar a simulacao (padrao: ultimo 'at'+1; teto 3600).");
        reg.add("run_input_tape",
            "Reproduz uma fita de input determinista: injeta cada evento no frame marcado e avanca a simulacao "
            + "frame a frame ate maxFrames. Ao final (mesmo apos excecao de script) TODO o input e zerado. "
            + "Requer o mundo em Play ou pausado. Relata eventos aplicados e frames avancados.",
            IgnisToolRegistry.schemaWith(props, List.of("tape")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getGameState() == Game.GameState.EDITING) {
                    return "Erro: o mundo nao esta em Play. Chame play_game (e opcionalmente pause_game) antes.";
                }
                org.json.JSONArray tape = args.optJSONArray("tape");
                if (tape == null) {
                    // O bridge HTTP pode entregar o array como string JSON.
                    try {
                        tape = new org.json.JSONArray(args.optString("tape", ""));
                    } catch (Exception e) {
                        return "Erro: 'tape' deve ser um array JSON de eventos {at, action|key|mouseButton, state}.";
                    }
                }
                if (tape.isEmpty()) return "Erro: fita vazia.";

                // Frame -> eventos daquele frame (ordenados pela posicao na fita).
                Map<Integer, List<org.json.JSONObject>> byFrame = new java.util.TreeMap<>();
                int lastAt = 0;
                for (int i = 0; i < tape.length(); i++) {
                    org.json.JSONObject ev = tape.optJSONObject(i);
                    if (ev == null) return "Erro: evento " + i + " nao e um objeto JSON.";
                    int at = Math.max(0, ev.optInt("at", 0));
                    lastAt = Math.max(lastAt, at);
                    byFrame.computeIfAbsent(at, k -> new java.util.ArrayList<>()).add(ev);
                }
                int maxFrames = Math.max(1, Math.min(3600, args.optInt("maxFrames", lastAt + 1)));

                int applied = 0, done = 0;
                List<String> skipped = new java.util.ArrayList<>();
                RuntimeException failure = null;
                try {
                    for (; done < maxFrames; done++) {
                        List<org.json.JSONObject> evs = byFrame.get(done);
                        if (evs != null) {
                            for (org.json.JSONObject ev : evs) {
                                if (applyTapeEvent(ev)) applied++;
                                else skipped.add("frame " + done + ": " + ev);
                            }
                        }
                        reg.liveGame.stepSimulationOnce();
                    }
                } catch (RuntimeException ex) {
                    failure = ex;
                } finally {
                    // Regra do roadmap: a sessao de teste restaura o input mesmo apos
                    // excecao — nenhuma tecla fica presa para o proximo teste.
                    Input.resetAll();
                    if (reg.refreshHook != null) reg.refreshHook.run();
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Fita reproduzida: ").append(applied).append('/').append(tape.length())
                  .append(" evento(s) aplicados em ").append(done).append('/').append(maxFrames)
                  .append(" frame(s). Input zerado ao final.");
                if (!skipped.isEmpty()) {
                    sb.append(" Ignorados (invalidos): ").append(skipped);
                }
                if (failure != null) {
                    sb.append(" INTERROMPIDO no frame ").append(done).append(": ").append(failure.getMessage());
                }
                sb.append(" Objetos na cena: ").append(reg.liveGame.getEntities().size()).append('.');
                return sb.toString();
            });
    }

    private void registerMouse() {
        Map<String, String> clickProps = new LinkedHashMap<>();
        clickProps.put("x", "Coordenada X em pixels de tela (0 = esquerda).");
        clickProps.put("y", "Coordenada Y em pixels de tela (0 = topo).");
        clickProps.put("button", "'left' (padrao), 'middle' ou 'right'.");
        reg.add("click_ui",
            "Clica na UI in-game numa COORDENADA de tela (press+release sinteticos, roteados aos "
            + "CanvasComponents e ao canvas global), como se o jogador clicasse — dispara o onClick de botoes "
            + "(ex: escolha de dialogo, 'nomear'). Requer o mundo em Play ou pausado. Use get_ui_tree para achar "
            + "as bounds do widget. Retorna se algum widget consumiu o clique.",
            IgnisToolRegistry.schemaWith(clickProps, List.of("x", "y")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getGameState() == Game.GameState.EDITING) {
                    return "Erro: o mundo nao esta em Play. Chame play_game (pause_game opcional) antes de click_ui.";
                }
                if (!args.has("x") || !args.has("y")) return "Erro: informe x e y (coordenadas de tela).";
                int x = args.optInt("x");
                int y = args.optInt("y");
                int b = switch (args.optString("button", "left").trim().toLowerCase(Locale.ROOT)) {
                    case "left" -> 1;
                    case "middle" -> 2;
                    case "right" -> 3;
                    default -> -1;
                };
                if (b < 0) return "Erro: button deve ser left, middle ou right.";
                boolean consumed = reg.liveGame.injectUiClickAt(x, y, b);
                if (reg.refreshHook != null) reg.refreshHook.run();
                return consumed
                        ? "Clique de UI em (" + x + "," + y + ") consumido por um widget (onClick disparado)."
                        : "Clique em (" + x + "," + y + ") NAO atingiu nenhum widget (fora de botao/painel). "
                                + "Confira as bounds com get_ui_tree.";
            });

        Map<String, String> moveProps = new LinkedHashMap<>();
        moveProps.put("x", "Coordenada X em pixels de tela.");
        moveProps.put("y", "Coordenada Y em pixels de tela.");
        reg.add("move_mouse",
            "Move o cursor virtual para uma coordenada de tela e roteia hover para a UI (destaca botoes sob o "
            + "ponto). Scripts leem a posicao via Input.getMouseX()/getMouseY(). Requer Play/pausado para o hover.",
            IgnisToolRegistry.schemaWith(moveProps, List.of("x", "y")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (!args.has("x") || !args.has("y")) return "Erro: informe x e y (coordenadas de tela).";
                int x = args.optInt("x");
                int y = args.optInt("y");
                reg.liveGame.moveMouseTo(x, y);
                return "Cursor movido para (" + x + "," + y + ").";
            });
    }

    // Aplica um evento da fita (tecla, acao semantica, mouse ou clique de UI por
    // coordenada). @return false se invalido.
    private boolean applyTapeEvent(org.json.JSONObject ev) {
        boolean pressed = !ev.optString("state", "down").trim().toLowerCase(Locale.ROOT).equals("up");

        // Clique de UI por coordenada (dispara onClick de botao/escolha de dialogo).
        if (ev.optBoolean("clickUi", false)) {
            if (!ev.has("x") || !ev.has("y")) return false;
            int b = switch (ev.optString("button", "left").trim().toLowerCase(Locale.ROOT)) {
                case "left" -> 1;
                case "middle" -> 2;
                case "right" -> 3;
                default -> -1;
            };
            if (b < 0) return false;
            reg.liveGame.injectUiClickAt(ev.optInt("x"), ev.optInt("y"), b);
            return true;
        }
        // Reposiciona o cursor virtual, se o evento trouxer coordenada.
        boolean moved = false;
        if (ev.has("x") && ev.has("y")) {
            Input.injectMouseMove(ev.optInt("x"), ev.optInt("y"));
            moved = true;
        }

        String mouseBtn = ev.optString("mouseButton", "").trim().toLowerCase(Locale.ROOT);
        if (!mouseBtn.isEmpty()) {
            int b = switch (mouseBtn) {
                case "left" -> 1;
                case "middle" -> 2;
                case "right" -> 3;
                default -> -1;
            };
            if (b < 0) return false;
            Input.injectMouseButton(b, pressed);
            return true;
        }
        Integer keyCode = resolveKey(ev);
        if (keyCode == null) return moved; // evento so-de-movimento e valido
        Input.injectKey(keyCode, pressed);
        return true;
    }
}
