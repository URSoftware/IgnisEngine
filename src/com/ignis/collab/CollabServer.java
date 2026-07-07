package com.ignis.collab;

import com.ignis.core.IgnisLogger;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CollabServer - Host de uma sessao colaborativa.
 *
 * <p>Escuta conexoes TCP e mantem uma lista de {@link ClientHandler}. Cada
 * mensagem (uma linha JSON) recebida de um convidado e retransmitida aos demais
 * (padrao "relay star": o host e o hub). Presenca e recalculada quando alguem
 * entra/sai. O protocolo por linha e agnostico de transporte, funcionando em IP
 * direto ou por VPN (Radmin/Hamachi/Tailscale).</p>
 */
final class CollabServer {

    private final int port;
    private final String hostName;
    private final CollabSession session;
    private final CopyOnWriteArrayList<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger idSeq = new AtomicInteger(1);

    private ServerSocket serverSocket;
    private volatile boolean running;

    private final String token; // null/vazio = sem senha

    CollabServer(int port, String hostName, CollabSession session, String token) {
        this.port = port;
        this.hostName = hostName;
        this.session = session;
        this.token = (token == null || token.isEmpty()) ? null : token;
    }

    void start() throws Exception {
        serverSocket = new ServerSocket(port);
        running = true;
        Thread accept = new Thread(this::acceptLoop, "Collab-Accept");
        accept.setDaemon(true);
        accept.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, idSeq.getAndIncrement());
                clients.add(handler);
                handler.start();
            } catch (Exception e) {
                if (running) IgnisLogger.error("[Collab] accept: " + e.getMessage());
            }
        }
    }

    void stop() {
        running = false;
        for (ClientHandler c : clients) c.close();
        clients.clear();
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignore) {}
    }

    /**
     * Retransmite uma mensagem a todos os clientes, exceto {@code except}.
     * Mensagens com campo {@code to} (uid) sao entregues apenas ao destinatario —
     * transferencias de arquivos de projeto nao inundam os demais convidados.
     */
    void broadcast(JSONObject msg, ClientHandler except) {
        String line = msg.toString();
        String to = msg.optString("to", "");
        for (ClientHandler c : clients) {
            if (c == except) continue;
            if (!to.isEmpty() && !to.equals(c.uid)) continue;
            c.sendLine(line);
        }
    }

    int participantCount() {
        return clients.size() + 1; // +1 = host
    }

    List<String> participantNames() {
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        names.add(hostName + " (host)");
        for (ClientHandler c : clients) names.add(c.name);
        return names;
    }

    String getShareAddress() {
        return com.ignis.mcp.McpHttpBridge.localLanAddress() + ":" + port;
    }

    static String hostHint(int port) {
        return com.ignis.mcp.McpHttpBridge.localLanAddress() + ":" + port;
    }

    // ------------------------------------------------------------------
    // Conexao individual de um convidado
    // ------------------------------------------------------------------

    private final class ClientHandler {
        private final Socket socket;
        private final int id;
        private String name = "convidado";
        private String uid = "";
        private OutputStream out;
        private volatile boolean open = true;

        ClientHandler(Socket socket, int id) {
            this.socket = socket;
            this.id = id;
        }

        void start() {
            Thread t = new Thread(this::readLoop, "Collab-Client-" + id);
            t.setDaemon(true);
            t.start();
        }

        private void readLoop() {
            try {
                this.out = socket.getOutputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while (open && (line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JSONObject msg = new JSONObject(line);
                    String type = msg.optString("type", "");
                    if ("hello".equals(type)) {
                        // Verifica o token da sessao (se o host definiu um).
                        if (token != null && !token.equals(msg.optString("token", ""))) {
                            try {
                                sendLine(new JSONObject().put("type", "denied")
                                        .put("reason", "token invalido").toString());
                            } catch (Exception ignore) { /* melhor esforco */ }
                            break; // encerra a conexao deste convidado
                        }
                        this.name = msg.optString("name", "convidado");
                        this.uid = msg.optString("uid", "g-" + id);
                        sendPresence();
                        session.firePresence();
                        continue;
                    }
                    // Repassa a todos os outros e processa localmente no host.
                    broadcast(msg, this);
                    session.dispatchInbound(msg);
                }
            } catch (Exception e) {
                // conexao caiu
            } finally {
                close();
                clients.remove(this);
                sendPresence();
                session.firePresence();
            }
        }

        void sendLine(String line) {
            if (!open) return;
            try {
                synchronized (this) {
                    out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            } catch (Exception e) {
                close();
            }
        }

        void close() {
            open = false;
            try { socket.close(); } catch (Exception ignore) {}
        }
    }

    // Envia a lista atualizada de participantes a todos (dispara recalculo de presenca).
    private void sendPresence() {
        JSONObject msg = new JSONObject().put("type", "presence");
        msg.put("participants", new org.json.JSONArray(participantNames()));
        broadcast(msg, null);
    }
}
