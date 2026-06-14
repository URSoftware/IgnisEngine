package com.ignis.marketplace;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

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

    private MarketplaceClient() {
        this.baseUrl = resolveBaseUrl();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
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
     * Publica um pacote no marketplace (POST /api/items). Best-effort: retorna
     * false se a API estiver offline.
     */
    public boolean publish(MarketplaceItem item) {
        try {
            JSONObject payload = new JSONObject()
                    .put("type", item.type)
                    .put("name", item.name)
                    .put("author", item.author)
                    .put("description", item.description)
                    .put("version", item.version)
                    .put("gitUrl", item.gitUrl)
                    .put("coverImageText", item.coverImageText)
                    .put("dependencies", item.dependencies);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/items"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() == 200 || resp.statusCode() == 201;
        } catch (Exception e) {
            return false;
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
