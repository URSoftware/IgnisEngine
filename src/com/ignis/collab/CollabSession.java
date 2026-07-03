package com.ignis.collab;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * CollabSession - Fachada de colaboracao em tempo real do IgnisEngine (estilo
 * "CodeTogether Cloud").
 *
 * <p>Concentra o estado de uma sessao colaborativa, seja como <b>host</b>
 * (iniciando um {@link CollabServer}) ou como <b>convidado</b> (conectando um
 * {@link CollabClient}). Convidados e host trocam mensagens de linha (JSON) por
 * TCP, o que funciona tanto por IP direto quanto por qualquer VPN de camada de
 * rede (ex.: Radmin VPN, Hamachi, Tailscale) sem configuracao extra.</p>
 *
 * <p>O transporte e generico: alem de presenca e chat, retransmite "eventos" em
 * canais nomeados ({@code scene}, {@code script}, {@code play}, {@code cursor}),
 * que o editor liga aos seus subsistemas. Isso permite compartilhar edicoes de
 * codigo, movimentacao de objetos e o estado de Play do host. O plano completo de
 * sincronizacao esta em {@code doc/COLLABORATION_GUIDE.md}.</p>
 */
public final class CollabSession {

    /** Papel do usuario na sessao. */
    public enum Role { HOST, GUEST, NONE }

    /** Canais logicos de evento retransmitidos entre os participantes. */
    public static final String CH_SCENE   = "scene";
    public static final String CH_SCRIPT  = "script";
    public static final String CH_PLAY    = "play";
    public static final String CH_CURSOR  = "cursor";
    /** Sincronizacao de arquivos do projeto (manifesto + transferencia + watcher). */
    public static final String CH_PROJECT = "project";

    /** Ouvinte de eventos da sessao (a UI/editor implementa). */
    public interface Listener {
        /** Lista de participantes mudou (entrou/saiu). */
        default void onPresence(List<String> participants) {}
        /** Mensagem de chat recebida. */
        default void onChat(String from, String text) {}
        /** Evento de canal recebido (scene/script/play/cursor). */
        default void onEvent(String channel, String from, JSONObject payload) {}
        /** Status da sessao mudou (conectado/desconectado/erro). */
        default void onStatus(String message, boolean connected) {}
    }

    private static final CollabSession INSTANCE = new CollabSession();
    public static CollabSession get() { return INSTANCE; }

    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private Role role = Role.NONE;
    private CollabServer server;
    private CollabClient client;
    private String displayName = System.getProperty("user.name", "Usuario");

    // Identificador unico deste participante na sessao. O host e sempre "host";
    // convidados geram um id aleatorio no join. Usado para mensagens direcionadas
    // (campo "to"), evitando retransmitir arquivos grandes a quem nao pediu.
    private String localUid = "host";
    // Endereco remoto do host (apenas como convidado) — chave do cache de projeto.
    private String remoteAddress = null;

    private CollabSession() {}

    public void addListener(Listener l) { if (l != null) listeners.add(l); }
    public void removeListener(Listener l) { listeners.remove(l); }

    public Role getRole() { return role; }
    public boolean isActive() { return role != Role.NONE; }
    public String getLocalUid() { return localUid; }
    /** Endereco {@code host_porta} da sessao em que se entrou (null se nao for convidado). */
    public String getRemoteAddress() { return remoteAddress; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String name) {
        if (name != null && !name.isBlank()) this.displayName = name.trim();
    }

    // ------------------------------------------------------------------
    // Host / Guest
    // ------------------------------------------------------------------

    /** Inicia a sessao como host, escutando na porta informada. Token opcional (senha). */
    public synchronized void host(int port, String token) throws Exception {
        stop();
        localUid = "host";
        remoteAddress = null;
        server = new CollabServer(port, displayName, this, token);
        server.start();
        role = Role.HOST;
        fireStatus("Hospedando sessao na porta " + port + " (" + CollabServer.hostHint(port) + ")"
                + (token != null && !token.isEmpty() ? " [com senha]" : ""), true);
        firePresence();
    }

    /** Conecta como convidado a {@code host:port}. Token deve bater com o do host (se houver). */
    public synchronized void join(String host, int port, String token) throws Exception {
        stop();
        localUid = "g-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        remoteAddress = host + "_" + port;
        client = new CollabClient(host, port, displayName, localUid, this, token);
        client.connect();
        role = Role.GUEST;
        fireStatus("Conectado a " + host + ":" + port, true);
    }

    /** Encerra qualquer sessao ativa (host ou convidado). */
    public synchronized void stop() {
        if (server != null) { server.stop(); server = null; }
        if (client != null) { client.close(); client = null; }
        if (role != Role.NONE) fireStatus("Sessao encerrada", false);
        role = Role.NONE;
    }

    /** Sugestao de URL/endereco para compartilhar (host). */
    public synchronized String getShareAddress() {
        if (role == Role.HOST && server != null) return server.getShareAddress();
        return null;
    }

    /** Numero de participantes conhecidos (inclui host). */
    public synchronized int participantCount() {
        if (role == Role.HOST && server != null) return server.participantCount();
        if (role == Role.GUEST && client != null) return client.participantCount();
        return 0;
    }

    // ------------------------------------------------------------------
    // Envio
    // ------------------------------------------------------------------

    public synchronized void sendChat(String text) {
        JSONObject msg = new JSONObject().put("type", "chat").put("from", displayName).put("text", text);
        route(msg);
    }

    /** Envia um evento de canal (scene/script/play/cursor/project) para os demais. */
    public synchronized void sendEvent(String channel, JSONObject payload) {
        sendEventTo(channel, payload, null);
    }

    /**
     * Envia um evento de canal direcionado a um unico participante ({@code toUid}).
     * Com {@code toUid} null, o evento vai a todos (broadcast). Usado pela
     * sincronizacao de projeto para nao inundar os demais convidados com arquivos
     * que so um deles pediu.
     */
    public synchronized void sendEventTo(String channel, JSONObject payload, String toUid) {
        JSONObject msg = new JSONObject()
                .put("type", "event")
                .put("channel", channel)
                .put("from", displayName)
                .put("payload", payload == null ? new JSONObject() : payload);
        if (toUid != null && !toUid.isEmpty()) msg.put("to", toUid);
        route(msg);
    }

    private void route(JSONObject msg) {
        if (role == Role.HOST && server != null) server.broadcast(msg, null);
        else if (role == Role.GUEST && client != null) client.send(msg);
    }

    // ------------------------------------------------------------------
    // Recepcao (chamado por server/client) e notificacao dos listeners
    // ------------------------------------------------------------------

    void dispatchInbound(JSONObject msg) {
        // Mensagens direcionadas: ignora as que nao sao para este participante.
        String to = msg.optString("to", "");
        if (!to.isEmpty() && !to.equals(localUid)) return;
        String type = msg.optString("type", "");
        switch (type) {
            case "chat":
                fireChat(msg.optString("from", "?"), msg.optString("text", ""));
                break;
            case "event":
                fireEvent(msg.optString("channel", ""), msg.optString("from", "?"),
                        msg.optJSONObject("payload"));
                break;
            case "presence":
                firePresence();
                break;
            default:
                // ignora tipos desconhecidos (forward-compat)
        }
    }

    void firePresence() {
        List<String> names = (role == Role.HOST && server != null) ? server.participantNames()
                : (role == Role.GUEST && client != null) ? client.participantNames()
                : List.of();
        for (Listener l : listeners) safe(() -> l.onPresence(names));
    }

    void fireChat(String from, String text) {
        for (Listener l : listeners) safe(() -> l.onChat(from, text));
    }

    void fireEvent(String channel, String from, JSONObject payload) {
        JSONObject safe = payload == null ? new JSONObject() : payload;
        for (Listener l : listeners) safe(() -> l.onEvent(channel, from, safe));
    }

    void fireStatus(String message, boolean connected) {
        for (Listener l : listeners) safe(() -> l.onStatus(message, connected));
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Exception ignore) { /* listener nao deve derrubar a sessao */ }
    }
}
