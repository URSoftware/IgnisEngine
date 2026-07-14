package com.ignis.core;

/**
 * Fábrica de Entidades (EntityFactory).
 * Responsável por criar GameObjects a partir do tipo (nome da classe).
 */
public class EntityFactory {

    /**
     * Cria uma nova instância de GameObject baseado no tipo.
     * 
     * @param type Nome do tipo da entidade (ex: "GameObject", "Camera")
     * @return Nova instância do GameObject
     */
    public static GameObject create(String type) {
        if (type == null) {
            return new GameObject();
        }
        return switch (type) {
            case "Camera" -> new Camera();
            case "BackgroundLayer" -> new BackgroundLayer();
            case "ParticleEmitter" -> new ParticleEmitter();
            case "TilemapObject" -> new TilemapObject();
            case "TextObject" -> new TextObject();
            case "LightObject" -> new LightObject();
            // Para todos os outros tipos (primitivos antigos e GameObject novo),
            // criamos uma entidade genérica GameObject.
            // O desserializador Scene.fromJSON cuidará da migração automática.
            default -> new GameObject();
        };
    }

    /**
     * Verifica se um tipo de entidade é suportado.
     *
     * @param type Nome do tipo
     * @return true se o tipo é suportado
     */
    public static boolean isSupported(String type) {
        if (type == null) return false;
        return switch (type) {
            case "GameObject", "Camera", "BackgroundLayer", "ParticleEmitter", "TilemapObject", "TextObject", "LightObject" -> true;
            default -> false;
        };
    }

    /**
     * Retorna todos os tipos de entidades suportados para criação.
     *
     * @return Array com os nomes dos tipos suportados
     */
    public static String[] getSupportedTypes() {
        return new String[] {
            "GameObject",
            "Camera",
            "BackgroundLayer",
            "ParticleEmitter",
            "TilemapObject",
            "TextObject",
            "LightObject"
        };
    }
}
