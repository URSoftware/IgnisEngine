package com.ignis.core;

/**
 * Fabrica de Entidades (EntityFactory).
 * Responsavel por criar GameObjects a partir do tipo (nome da classe).
 * 
 * IMPORTANTE: Toda nova entidade deve ser registrada aqui!
 */
public class EntityFactory {

    /**
     * Cria uma nova instancia de GameObject baseado no tipo.
     * 
     * @param type Nome da classe da entidade (ex: "Player", "Enemy")
     * @return Nova instancia do GameObject
     * @throws RuntimeException se o tipo nao for reconhecido
     */
    public static GameObject create(String type) {
        return switch (type) {
            case "Player" -> new Player();
            case "Square" -> new Square();
            case "Circle" -> new Circle();
            case "Triangle" -> new Triangle();
            case "Star" -> new Star();
            case "Pentagon" -> new Pentagon();
            case "MergedShape" -> new MergedShape();
            case "Camera" -> new Camera();
            // Adicione novas entidades aqui:
            // case "Enemy" -> new Enemy();
            // case "NPC" -> new NPC();
            default -> throw new RuntimeException("Tipo de entidade desconhecido: " + type);
        };
    }

    /**
     * Verifica se um tipo de entidade e suportado.
     * 
     * @param type Nome da classe da entidade
     * @return true se o tipo e suportado
     */
    public static boolean isSupported(String type) {
        return switch (type) {
            case "Player" -> true;
            case "Square" -> true;
            case "Circle" -> true;
            case "Triangle" -> true;
            case "Star" -> true;
            case "Pentagon" -> true;
            case "MergedShape" -> true;
            case "Camera" -> true;
            // Adicione novas entidades aqui:
            // case "Enemy" -> true;
            // case "NPC" -> true;
            default -> false;
        };
    }

    /**
     * Retorna todos os tipos de entidades suportados.
     * Util para o Editor exibir opcoes de criacao.
     * 
     * @return Array com os nomes dos tipos suportados
     */
    public static String[] getSupportedTypes() {
        return new String[] {
                "Player",
                "Square",
                "Circle",
                "Triangle",
                "Star",
                "Pentagon",
                "MergedShape",
                "Camera"
                // Adicione novas entidades aqui:
                // "Enemy",
                // "NPC"
        };
    }
}
