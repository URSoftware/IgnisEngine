package com.ignis.marketplace;

/**
 * Modelo de um item do marketplace (plugin, asset ou workshop).
 *
 * <p>Espelha a tabela {@code items} do backend Vercel/Neon e o JSON retornado
 * por {@code GET /api/items}. Mantido como POJO simples (campos publicos) para
 * uso direto pela UI Swing/JavaFX sem boilerplate de getters.
 *
 * <p>Regra do roadmap: um item carrega apenas a URL do repositorio Git, nunca
 * binarios.
 */
public class MarketplaceItem {

    /** "plugin", "workshop" ou "asset". */
    public final String type;
    public final String name;
    public final String author;
    public final String description;
    public final String version;
    public final String gitUrl;
    public final String coverImageText;
    public final String dependencies;

    /** Id no backend (0 quando vindo do catalogo mock offline). */
    public final int id;
    public final int downloads;

    public MarketplaceItem(String type, String name, String author, String description,
                           String version, String gitUrl, String coverImageText, String dependencies) {
        this(0, type, name, author, description, version, gitUrl, coverImageText, dependencies, 0);
    }

    public MarketplaceItem(int id, String type, String name, String author, String description,
                           String version, String gitUrl, String coverImageText, String dependencies,
                           int downloads) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.author = author;
        this.description = description;
        this.version = version;
        this.gitUrl = gitUrl;
        this.coverImageText = coverImageText;
        this.dependencies = dependencies;
        this.downloads = downloads;
    }
}
