package com.ignis.collab;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CollabClient - Convidado de uma sessao colaborativa.
 *
 * <p>Conecta ao host por TCP e troca mensagens de linha (JSON). Ao conectar,
 * envia um {@code hello} com o nome de exibicao; a partir dai recebe presenca,
 * chat e eventos de canal, delegando ao {@link CollabSession} para notificar a
 * UI/editor.</p>
 */
final class CollabClient {

    private final String host;
    private final int port;
    private final String displayName;
    private final CollabSession session;

    private Socket socket;
    private OutputStream out;
    private volatile boolean open;
    private volatile List<String> participants = new ArrayList<>();

    private final String token;

    CollabClient(String host, int port, String displayName, CollabSession session, String token) {
        this.host = host;
        this.port = port;
        this.displayName = displayName;
        this.session = session;
        this.token = (token == null) ? "" : token;
    }

    void connect() throws Exception {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 8000);
        out = socket.getOutputStream();
        open = true;

        Thread reader = new Thread(this::readLoop, "Collab-Reader");
        reader.setDaemon(true);
        reader.start();

        // Apresenta-se ao host (com o token da sessao, se houver).
        send(new JSONObject().put("type", "hello").put("name", displayName).put("token", token));
    }

    private void readLoop() {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while (open && (line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                JSONObject msg = new JSONObject(line);
                if ("denied".equals(msg.optString("type", ""))) {
                    session.fireStatus("Recusado pelo host: " + msg.optString("reason", "token invalido"), false);
                    break; // encerra: token invalido
                }
                if ("presence".equals(msg.optString("type", ""))) {
                    JSONArray arr = msg.optJSONArray("participants");
                    if (arr != null) {
                        List<String> names = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) names.add(arr.optString(i, "?"));
                        participants = names;
                    }
                    session.firePresence();
                    continue;
                }
                session.dispatchInbound(msg);
            }
        } catch (Exception e) {
            // conexao caiu
        } finally {
            boolean wasOpen = open;
            close();
            if (wasOpen) session.fireStatus("Conexao com o host perdida", false);
        }
    }

    void send(JSONObject msg) {
        if (!open) return;
        try {
            synchronized (this) {
                out.write((msg.toString() + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
        } catch (Exception e) {
            close();
        }
    }

    List<String> participantNames() {
        return new ArrayList<>(participants);
    }

    int participantCount() {
        return Math.max(1, participants.size());
    }

    void close() {
        open = false;
        try { if (socket != null) socket.close(); } catch (Exception ignore) {}
    }
}
