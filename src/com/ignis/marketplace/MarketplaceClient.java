package com.ignis.marketplace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cliente HTTP do marketplace online (backend Next.js na Vercel + Neon).
 *
 * <p>Consome a API REST do repositorio dedicado
 * {@code https://github.com/ThyagoToledo/IginisMarketePlace} (deploy na Vercel).
 * Se a API estiver offline ou nao configurada, cai automaticamente no catalogo
 * mock embutido, de modo que o editor nunca quebra sem internet.
 *
 * <p>URL base resolvida nesta ordem:
 * <ol>
 *   <li>propriedade de sistema {@code -Dignis.marketplace.url=...}</li>
 *   <li>variavel de ambiente {@code IGNIS_MARKETPLACE_URL}</li>
 *   <li>{@link #DEFAULT_BASE_URL} (ajuste apos o deploy na Vercel)</li>
 * </ol>
 */
public final class MarketplaceClient {

    /** Ajuste para a URL real do deploy na Vercel apos publicar. */
    public static final String DEFAULT_BASE_URL = "https://iginis-markete-place.vercel.app";

    private static final MarketplaceClient INSTANCE = new MarketplaceClient();

    private final HttpClient http;
    private final String baseUrl;
    private boolean lastFetchOnline = false;

    /** Armazena o token de publicacao localmente, por usuario do SO. */
    private final Preferences prefs = Preferences.userRoot().node("com/ignis/marketplace");
    private static final String TOKEN_KEY = "publishToken";

    private MarketplaceClient() {
        this.baseUrl = resolveBaseUrl();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /** URL da pagina web de publicacao (abrir no navegador). */
    public String getPublishUrl() {
        return baseUrl + "/publish";
    }

    /** URL da pagina de conta (gerar token). */
    public String getAccountUrl() {
        return baseUrl + "/account";
    }

    // ---- Token de publicacao (persistido via Preferences) ----

    public String getToken() {
        // Prioridade: env/propriedade (CI) -> Preferences salvas.
        String sys = System.getProperty("ignis.marketplace.token");
        if (sys != null && !sys.isBlank()) return sys.trim();
        String env = System.getenv("IGNIS_MARKETPLACE_TOKEN");
        if (env != null && !env.isBlank()) return env.trim();
        String saved = prefs.get(TOKEN_KEY, "");
        return saved.isBlank() ? null : saved;
    }

    public boolean hasToken() {
        return getToken() != null;
    }

    public void setToken(String token) {
        if (token == null || token.isBlank()) {
            prefs.remove(TOKEN_KEY);
        } else {
            prefs.put(TOKEN_KEY, token.trim());
        }
    }

    public void clearToken() {
        prefs.remove(TOKEN_KEY);
    }

    /** Resultado de uma tentativa de publicacao, com mensagem e motivos do gate. */
    public static class PublishResult {
        public final boolean ok;
        public final String message;
        public final List<String> reasons;

        public PublishResult(boolean ok, String message, List<String> reasons) {
            this.ok = ok;
            this.message = message;
            this.reasons = reasons != null ? reasons : new ArrayList<>();
        }
    }

    public static MarketplaceClient getInstance() {
        return INSTANCE;
    }

    private static String resolveBaseUrl() {
        String prop = System.getProperty("ignis.marketplace.url");
        if (prop != null && !prop.isBlank()) return stripTrailingSlash(prop);
        String env = System.getenv("IGNIS_MARKETPLACE_URL");
        if (env != null && !env.isBlank()) return stripTrailingSlash(env);
        return DEFAULT_BASE_URL;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /** True se o ultimo {@link #fetchCatalog()} veio realmente da API online. */
    public boolean isLastFetchOnline() {
        return lastFetchOnline;
    }

    /**
     * Busca o catalogo na API. Em qualquer falha (rede, timeout, JSON invalido),
     * retorna o catalogo mock embutido.
     */
    public List<MarketplaceItem> fetchCatalog() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/items"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                List<MarketplaceItem> items = parseCatalog(resp.body());
                lastFetchOnline = true;
                return items;
            }
        } catch (Exception ignored) {
            // cai no fallback abaixo
        }
        lastFetchOnline = false;
        return mockCatalog();
    }

    private static List<MarketplaceItem> parseCatalog(String body) {
        List<MarketplaceItem> items = new ArrayList<>();
        JSONArray arr = new JSONArray(body);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            items.add(new MarketplaceItem(
                    o.optInt("id", 0),
                    o.optString("type", "asset"),
                    o.optString("name", ""),
                    o.optString("author", ""),
                    o.optString("description", ""),
                    o.optString("version", "1.0.0"),
                    o.optString("gitUrl", ""),
                    o.optString("coverImageText", ""),
                    o.optString("dependencies", "None"),
                    o.optInt("downloads", 0)));
        }
        return items;
    }

    /**
     * Publica um pacote no marketplace (POST /api/items) usando o token salvo.
     * Envia "Authorization: Bearer &lt;token&gt;" e o aceite dos termos.
     * Retorna um {@link PublishResult} com sucesso/mensagem/motivos do gate.
     */
    public PublishResult publish(MarketplaceItem item, boolean acceptTerms) {
        String token = getToken();
        if (token == null) {
            return new PublishResult(false,
                    "Nenhum token configurado. Gere um token no site e cole no editor.", null);
        }
        try {
            JSONObject payload = new JSONObject()
                    .put("type", item.type)
                    .put("name", item.name)
                    .put("author", item.author)
                    .put("description", item.description)
                    .put("version", item.version)
                    .put("gitUrl", item.gitUrl)
                    .put("coverImageText", item.coverImageText)
                    .put("dependencies", item.dependencies)
                    .put("acceptTerms", acceptTerms);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/items"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();
            if (code == 200 || code == 201) {
                return new PublishResult(true, "Pacote publicado com sucesso!", null);
            }

            // Extrai mensagem e motivos do gate de seguranca, se houver.
            String message = "Falha ao publicar (HTTP " + code + ").";
            List<String> reasons = new ArrayList<>();
            try {
                JSONObject body = new JSONObject(resp.body());
                if (body.has("error")) message = body.getString("error");
                if (body.has("report")) {
                    JSONObject report = body.getJSONObject("report");
                    if (report.has("reasons")) {
                        JSONArray rs = report.getJSONArray("reasons");
                        for (int i = 0; i < rs.length(); i++) reasons.add(rs.getString(i));
                    }
                }
            } catch (Exception ignore) {
                // resposta nao-JSON
            }
            if (code == 401) message = "Token invalido ou expirado. Gere um novo no site.";
            return new PublishResult(false, message, reasons);
        } catch (Exception e) {
            return new PublishResult(false,
                    "Nao foi possivel contatar o marketplace (" + e.getMessage() + ").", null);
        }
    }

    /**
     * Avisa o backend que um pacote foi instalado (incrementa downloads).
     * Best-effort; ignora falhas.
     */
    public void notifyInstall(int itemId) {
        if (itemId <= 0) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/items/" + itemId))
                    .timeout(Duration.ofSeconds(5))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // sem efeito se offline
        }
    }

    /** Catalogo de fallback usado quando a API esta indisponivel. */
    public static List<MarketplaceItem> mockCatalog() {
        List<MarketplaceItem> items = new ArrayList<>();
        items.add(new MarketplaceItem("workshop", "Pixel Fantasy Trees Pack", "Arthur_Art",
                "Beautiful hand-drawn 2D sprite pack containing 16 unique fantasy trees.", "1.2.0",
                "https://github.com/ArthurArt/fantasy-trees-pack.git", "Sprite Pack", "None"));
        items.add(new MarketplaceItem("workshop", "Retro Sound FX Library", "ChiptuneHero",
                "Collection of 40 chiptune sound effects (.wav) for retro games.", "1.0.0",
                "https://github.com/ChiptuneHero/retro-sfx-lib.git", "SFX Library", "None"));
        items.add(new MarketplaceItem("plugin", "Advanced Physics 2D", "PhysTech",
                "Decoupled rigidbodies and collision solver plugin with friction and bounce.", "2.1.0",
                "https://github.com/PhysTech/advanced-physics-2d.git", "Physics Engine", "Core-Physics >= 1.0"));
        items.add(new MarketplaceItem("plugin", "Virtual Gamepad UI Overlay", "MobileDev",
                "Adds a mobile-friendly virtual joystick overlay to screen automatically.", "1.0.5",
                "https://github.com/MobileDev/virtual-gamepad-ignis.git", "Mobile Gamepad", "UI-Canvas >= 2.0"));
        items.add(new MarketplaceItem("asset", "Cyberpunk Tilemap 32x32", "NeonPixel",
                "32x32 tileset containing city backgrounds, neon lights and pavements.", "1.1.0",
                "https://github.com/NeonPixel/cyberpunk-tilemap.git", "Neon Tileset", "None"));
        return items;
    }
}
