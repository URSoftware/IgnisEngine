package com.ignis.mcp;

import com.ignis.core.Game;
import com.ignis.core.GameObject;
import com.ignis.cutscene.Cutscene;
import com.ignis.cutscene.CutsceneIO;
import com.ignis.cutscene.CutscenePlayer;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Ferramentas MCP de autoria e execucao de cutscenes (roadmap P1, passo 5 —
 * "timeline de cutscene"). API-first: o agente autora a timeline por tracks e
 * keyframes num JSON do projeto ({@code cutscenes/<nome>.cutscene.json}), valida
 * ator/asset ausente e executa/preve de forma deterministica. A futura timeline
 * visual do editor consumira o MESMO modelo ({@link Cutscene}).
 *
 * <p>Autoria mexe em ARQUIVOS do projeto (persistente, independente do Play);
 * {@code run_cutscene} mexe no RUNTIME (requer Play/pausado; o Stop restaura o
 * snapshot da cena, como qualquer efeito de Play).</p>
 */
final class CutsceneTools {

    private final IgnisToolRegistry reg;

    CutsceneTools(IgnisToolRegistry reg) {
        this.reg = reg;
    }

    void registerAll() {
        registerAuthoring();
        registerInspection();
        registerRun();
    }

    // ------------------------------------------------------------------
    // Autoria (arquivos do projeto)
    // ------------------------------------------------------------------

    private void registerAuthoring() {
        Map<String, String> createProps = new LinkedHashMap<>();
        createProps.put("name", "Nome da cutscene (letras/digitos/_/-; vira cutscenes/<nome>.cutscene.json).");
        createProps.put("durationFrames", "Duracao em frames de simulacao (60/s). Padrao 300 (5s).");
        reg.add("create_cutscene",
            "Cria uma cutscene vazia no projeto (timeline por tracks/keyframes, 60 frames/s). Depois use "
            + "add_cutscene_keyframe para animar atores/camera e disparar dialogo/audio/sinais; "
            + "validate_cutscene confere atores/assets; run_cutscene executa no Play.",
            IgnisToolRegistry.schemaWith(createProps, List.of("name")),
            args -> {
                String name = args.optString("name", "").trim();
                if (!CutsceneIO.isValidName(name)) {
                    return "Erro: nome invalido (use letras, digitos, '_' ou '-').";
                }
                if (CutsceneIO.exists(reg.projectFolder, name)) {
                    return "Erro: ja existe cutscene '" + name + "'.";
                }
                Cutscene cs = new Cutscene(name, args.optInt("durationFrames", 300));
                CutsceneIO.save(reg.projectFolder, cs);
                return "Cutscene '" + name + "' criada (" + cs.getDurationFrames() + " frames = "
                        + String.format(Locale.ROOT, "%.1f", cs.getDurationFrames() / 60.0) + "s).";
            });

        Map<String, String> durProps = new LinkedHashMap<>();
        durProps.put("name", "Nome da cutscene.");
        durProps.put("durationFrames", "Nova duracao em frames (60/s).");
        reg.add("set_cutscene_duration",
            "Altera a duracao total de uma cutscene (frames de simulacao, 60/s).",
            IgnisToolRegistry.schemaWith(durProps, List.of("name", "durationFrames")),
            args -> {
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);
                cs.setDurationFrames(args.optInt("durationFrames", cs.getDurationFrames()));
                CutsceneIO.save(reg.projectFolder, cs);
                return "Duracao de '" + cs.getName() + "' agora e " + cs.getDurationFrames() + " frames.";
            });

        Map<String, String> trackProps = new LinkedHashMap<>();
        trackProps.put("name", "Nome da cutscene.");
        trackProps.put("type", "ACTOR (move objeto), CAMERA, DIALOG, AUDIO, SIGNAL ou FLAG.");
        trackProps.put("target", "Alvo da track: nome do objeto (ACTOR), da camera (CAMERA) ou rotulo do canal. "
                + "Opcional para tracks de evento.");
        reg.add("add_cutscene_track",
            "Adiciona uma track vazia a cutscene (ACTOR/CAMERA interpolam x/y; DIALOG/AUDIO/SIGNAL/FLAG "
            + "disparam eventos no frame exato do keyframe).",
            IgnisToolRegistry.schemaWith(trackProps, List.of("name", "type")),
            args -> {
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);
                Cutscene.TrackType type = parseType(args.optString("type", ""));
                if (type == null) return "Erro: type deve ser ACTOR, CAMERA, DIALOG, AUDIO, SIGNAL ou FLAG.";
                String target = args.optString("target", "").trim();
                if (cs.findTrack(type, target) != null) {
                    return "Erro: ja existe track " + type + (target.isEmpty() ? "" : ":" + target) + ".";
                }
                cs.addTrack(new Cutscene.Track(type, target));
                CutsceneIO.save(reg.projectFolder, cs);
                return "Track " + type + (target.isEmpty() ? "" : ":" + target)
                        + " adicionada a '" + cs.getName() + "'.";
            });

        Map<String, String> kfProps = new LinkedHashMap<>();
        kfProps.put("name", "Nome da cutscene.");
        kfProps.put("type", "Tipo da track (ACTOR/CAMERA/DIALOG/AUDIO/SIGNAL/FLAG).");
        kfProps.put("target", "Alvo da track (cria a track se nao existir).");
        kfProps.put("frame", "Frame do keyframe (0-based, 60/s). Substitui keyframe existente no mesmo frame.");
        kfProps.put("x", "Posicao X (tracks ACTOR/CAMERA).");
        kfProps.put("y", "Posicao Y (tracks ACTOR/CAMERA).");
        kfProps.put("visible", "true/false: visibilidade do ator a partir deste keyframe.");
        kfProps.put("easing", "Curva de SAIDA ate o proximo keyframe: LINEAR (padrao), EASE_IN, EASE_OUT, "
                + "EASE_IN_OUT ou STEP (segura o valor).");
        kfProps.put("text", "Texto do dialogo (tracks DIALOG).");
        kfProps.put("data", "Carga do evento: caminho de audio (AUDIO), nome do sinal (SIGNAL) ou "
                + "'flag=valor' (FLAG).");
        reg.add("add_cutscene_keyframe",
            "Adiciona (ou substitui) um keyframe numa track da cutscene; cria a track se preciso. ACTOR/CAMERA "
            + "interpolam x/y entre keyframes com easing; DIALOG/AUDIO/SIGNAL/FLAG disparam no frame exato.",
            IgnisToolRegistry.schemaWith(kfProps, List.of("name", "type", "frame")),
            args -> {
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);
                Cutscene.TrackType type = parseType(args.optString("type", ""));
                if (type == null) return "Erro: type deve ser ACTOR, CAMERA, DIALOG, AUDIO, SIGNAL ou FLAG.";
                String target = args.optString("target", "").trim();
                Cutscene.Track track = cs.findTrack(type, target);
                if (track == null) {
                    track = new Cutscene.Track(type, target);
                    cs.addTrack(track);
                }
                Cutscene.Easing easing;
                try {
                    easing = Cutscene.Easing.valueOf(
                            args.optString("easing", "LINEAR").trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException iae) {
                    return "Erro: easing deve ser LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT ou STEP.";
                }
                int frame = Math.max(0, args.optInt("frame", 0));
                track.addKeyframe(new Cutscene.Keyframe(frame, easing,
                        args.has("x") ? args.optDouble("x") : null,
                        args.has("y") ? args.optDouble("y") : null,
                        args.has("visible") ? args.optBoolean("visible") : null,
                        args.has("text") ? args.optString("text") : null,
                        args.has("data") ? args.optString("data") : null));
                CutsceneIO.save(reg.projectFolder, cs);
                return "Keyframe em frame " + frame + " gravado na track " + type
                        + (target.isEmpty() ? "" : ":" + target) + " de '" + cs.getName() + "'.";
            });

        Map<String, String> rmProps = new LinkedHashMap<>();
        rmProps.put("name", "Nome da cutscene.");
        rmProps.put("type", "Tipo da track.");
        rmProps.put("target", "Alvo da track.");
        rmProps.put("frame", "Frame do keyframe a remover.");
        reg.add("remove_cutscene_keyframe",
            "Remove um keyframe de uma track da cutscene.",
            IgnisToolRegistry.schemaWith(rmProps, List.of("name", "type", "frame")),
            args -> {
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);
                Cutscene.TrackType type = parseType(args.optString("type", ""));
                if (type == null) return "Erro: type invalido.";
                Cutscene.Track track = cs.findTrack(type, args.optString("target", "").trim());
                if (track == null) return "Erro: track nao encontrada.";
                int frame = args.optInt("frame", -1);
                if (!track.removeKeyframe(frame)) {
                    return "Erro: nenhum keyframe no frame " + frame + " dessa track.";
                }
                CutsceneIO.save(reg.projectFolder, cs);
                return "Keyframe do frame " + frame + " removido.";
            });

        reg.add("delete_cutscene",
            "Apaga uma cutscene do projeto (remove o arquivo cutscenes/<nome>.cutscene.json).",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome da cutscene a apagar"), List.of("name")),
            args -> {
                String name = args.optString("name", "").trim();
                if (!CutsceneIO.isValidName(name)) return "Erro: nome invalido.";
                if (!CutsceneIO.delete(reg.projectFolder, name)) {
                    return "Erro: cutscene '" + name + "' nao encontrada.";
                }
                return "Cutscene '" + name + "' apagada.";
            });
    }

    // ------------------------------------------------------------------
    // Inspecao, validacao e preview (read-only)
    // ------------------------------------------------------------------

    private void registerInspection() {
        reg.add("list_cutscenes",
            "Lista as cutscenes do projeto (pasta cutscenes/).",
            IgnisToolRegistry.objectSchema(),
            args -> {
                List<String> names = CutsceneIO.listNames(reg.projectFolder);
                if (names.isEmpty()) return "(nenhuma cutscene; crie com create_cutscene)";
                StringBuilder sb = new StringBuilder(names.size() + " cutscene(s):\n");
                for (String n : names) {
                    Cutscene cs = CutsceneIO.load(reg.projectFolder, n);
                    sb.append("- ").append(n);
                    if (cs != null) {
                        sb.append(" (").append(cs.getDurationFrames()).append(" frames, ")
                          .append(cs.getTracks().size()).append(" tracks)");
                    } else {
                        sb.append(" (CORROMPIDA)");
                    }
                    sb.append('\n');
                }
                return sb.toString();
            });

        reg.add("get_cutscene",
            "Retorna a timeline completa de uma cutscene (tracks e keyframes em JSON).",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome da cutscene"), List.of("name")),
            args -> {
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);
                return cs.toJSON().toString(2);
            });

        reg.add("validate_cutscene",
            "Valida uma cutscene: tracks sem keyframes/alvo, atores que nao existem na cena, keyframes alem da "
            + "duracao, dialogo sem texto, audio com asset ausente. Retorna 'OK' se nada for encontrado.",
            IgnisToolRegistry.schemaWith(Map.of("name", "Nome da cutscene"), List.of("name")),
            args -> {
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);
                Set<String> actors = null;
                if (reg.liveGame != null) {
                    actors = new HashSet<>();
                    for (GameObject go : reg.liveGame.getEntities()) actors.add(go.getName());
                }
                List<String> issues = cs.validate(actors, rel -> {
                    java.io.File f = reg.resolveInProject(rel);
                    return f != null && f.isFile();
                });
                if (issues.isEmpty()) {
                    return "OK: cutscene '" + cs.getName() + "' valida ("
                            + cs.getTracks().size() + " tracks, " + cs.getDurationFrames() + " frames).";
                }
                StringBuilder sb = new StringBuilder(issues.size() + " problema(s):\n");
                for (String i : issues) sb.append("- ").append(i).append('\n');
                return sb.toString();
            });

        Map<String, String> prevProps = new LinkedHashMap<>();
        prevProps.put("name", "Nome da cutscene.");
        prevProps.put("frame", "Frame a amostrar (padrao 0).");
        reg.add("preview_cutscene",
            "Scrub read-only: calcula o estado da cutscene num frame (pose interpolada de cada ator/camera e "
            + "eventos que disparam nesse frame) SEM tocar a cena nem exigir Play.",
            IgnisToolRegistry.schemaWith(prevProps, List.of("name")),
            args -> {
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);
                int frame = Math.max(0, Math.min(cs.getDurationFrames(), args.optInt("frame", 0)));
                StringBuilder sb = new StringBuilder("Frame " + frame + "/" + cs.getDurationFrames()
                        + " de '" + cs.getName() + "':\n");
                boolean any = false;
                for (Cutscene.Track track : cs.getTracks()) {
                    String label = track.type + (track.target.isEmpty() ? "" : ":" + track.target);
                    if (track.type == Cutscene.TrackType.ACTOR
                            || track.type == Cutscene.TrackType.CAMERA) {
                        Cutscene.Pose pose = Cutscene.samplePose(track, frame);
                        if (pose.x == null && pose.y == null && pose.visible == null) continue;
                        sb.append("- ").append(label).append(" -> ");
                        if (pose.x != null || pose.y != null) {
                            sb.append('(').append(pose.x != null ? String.format(Locale.ROOT, "%.1f", pose.x) : "-")
                              .append(',').append(pose.y != null ? String.format(Locale.ROOT, "%.1f", pose.y) : "-")
                              .append(')');
                        }
                        if (pose.visible != null) sb.append(pose.visible ? " visivel" : " oculto");
                        sb.append('\n');
                        any = true;
                    } else {
                        for (Cutscene.Keyframe kf : cs.eventsAt(track, frame)) {
                            sb.append("- ").append(label).append(" DISPARA: ")
                              .append(kf.text != null ? kf.text : kf.data).append('\n');
                            any = true;
                        }
                    }
                }
                return any ? sb.toString() : sb.append("(nenhuma track com keyframes)").toString();
            });
    }

    // ------------------------------------------------------------------
    // Execucao no runtime (requer Play)
    // ------------------------------------------------------------------

    private void registerRun() {
        Map<String, String> runProps = new LinkedHashMap<>();
        runProps.put("name", "Nome da cutscene.");
        runProps.put("fromFrame", "Frame inicial (padrao 0).");
        runProps.put("toFrame", "Frame final inclusivo (padrao: duracao da cutscene; teto 3600).");
        runProps.put("skip", "true = pula direto ao estado FINAL (mesmo estado da conclusao natural) e lista "
                + "todos os eventos que teriam disparado.");
        reg.add("run_cutscene",
            "Executa uma cutscene no mundo em Play: a cada frame aplica as poses (atores/camera) e avanca a "
            + "simulacao 1 passo determinista; devolve os eventos (dialogo/audio/sinal/flag) na ordem em que "
            + "dispararam. Efeito de runtime: o Stop restaura a cena. Requer play_game (pausado tambem vale).",
            IgnisToolRegistry.schemaWith(runProps, List.of("name")),
            args -> {
                if (reg.liveGame == null) return "Erro: editor nao disponivel.";
                if (reg.liveGame.getGameState() == Game.GameState.EDITING) {
                    return "Erro: o mundo nao esta em Play. Chame play_game (e opcionalmente pause_game) antes.";
                }
                Cutscene cs = loadOrNull(args);
                if (cs == null) return notFound(args);

                if (args.optBoolean("skip", false)) {
                    List<String> events = CutscenePlayer.skipToEnd(reg.liveGame, cs,
                            Math.max(0, args.optInt("fromFrame", 0)));
                    if (reg.refreshHook != null) reg.refreshHook.run();
                    return "Cutscene '" + cs.getName() + "' pulada para o estado final (frame "
                            + cs.getDurationFrames() + "). Eventos que disparariam: "
                            + (events.isEmpty() ? "(nenhum)" : "\n- " + String.join("\n- ", events));
                }

                int from = Math.max(0, args.optInt("fromFrame", 0));
                int to = Math.min(Math.min(cs.getDurationFrames(), args.optInt("toFrame", cs.getDurationFrames())),
                        from + 3600);
                if (to < from) return "Erro: toFrame < fromFrame.";
                List<String> events = new java.util.ArrayList<>();
                int frame = from;
                RuntimeException failure = null;
                try {
                    for (; frame <= to; frame++) {
                        events.addAll(CutscenePlayer.applyFrame(reg.liveGame, cs, frame));
                        reg.liveGame.stepSimulationOnce();
                    }
                } catch (RuntimeException ex) {
                    failure = ex;
                } finally {
                    if (reg.refreshHook != null) reg.refreshHook.run();
                }
                StringBuilder sb = new StringBuilder("Cutscene '" + cs.getName() + "' executada: frames "
                        + from + ".." + (frame - 1) + " de " + cs.getDurationFrames() + ".");
                if (failure != null) {
                    sb.append(" INTERROMPIDA no frame ").append(frame).append(": ").append(failure.getMessage());
                }
                sb.append(" Eventos: ").append(events.isEmpty() ? "(nenhum)"
                        : "\n- " + String.join("\n- ", events));
                return sb.toString();
            });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Cutscene loadOrNull(JSONObject args) {
        String name = args.optString("name", "").trim();
        if (!CutsceneIO.isValidName(name)) return null;
        return CutsceneIO.load(reg.projectFolder, name);
    }

    private String notFound(JSONObject args) {
        String name = args.optString("name", "").trim();
        if (!CutsceneIO.isValidName(name)) {
            return "Erro: nome invalido (use letras, digitos, '_' ou '-').";
        }
        return "Erro: cutscene '" + name + "' nao encontrada. Existentes: "
                + CutsceneIO.listNames(reg.projectFolder) + ".";
    }

    private static Cutscene.TrackType parseType(String raw) {
        try {
            return Cutscene.TrackType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }
}
