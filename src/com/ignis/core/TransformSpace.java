package com.ignis.core;

/**
 * TransformSpace - Define o espaço de referência para transformações.
 * 
 * Usado para determinar se as transformações (movimento, rotação, escala)
 * devem ser aplicadas em relação ao sistema de coordenadas do mundo (global)
 * ou em relação ao próprio objeto (local).
 * 
 * Exemplo de uso:
 * ```java
 * // Mover objeto 10 unidades para frente na direção que ele está olhando
 * transform.translate(10, 0, TransformSpace.LOCAL);
 * 
 * // Mover objeto 10 unidades para a direita no mundo
 * transform.translate(10, 0, TransformSpace.WORLD);
 * ```
 */
public enum TransformSpace {
    
    /**
     * WORLD - Espaço global/mundo.
     * 
     * As transformações são aplicadas usando o sistema de coordenadas global.
     * - Eixo X aponta para a direita
     * - Eixo Y aponta para baixo
     * - Rotação é absoluta em relação ao mundo
     * 
     * Use quando:
     * - Quiser mover um objeto em uma direção fixa independente de sua rotação
     * - Precisar posicionar objetos em coordenadas absolutas
     */
    WORLD,
    
    /**
     * LOCAL - Espaço local/próprio do objeto.
     * 
     * As transformações são aplicadas usando o sistema de coordenadas do objeto.
     * - Eixo X aponta para a "frente" do objeto (baseado na rotação)
     * - Eixo Y aponta para a "esquerda" do objeto
     * - Movimento e rotação são relativos à orientação atual
     * 
     * Use quando:
     * - Quiser mover um objeto "para frente" na direção que ele está olhando
     * - Precisar de movimento relativo à orientação do objeto
     * - Quiser fazer um objeto "andar" em sua direção de visão
     */
    LOCAL,
    
    /**
     * PARENT - Espaço do objeto pai (para hierarquias futuras).
     * 
     * Reservado para implementação futura de hierarquia de objetos.
     */
    PARENT;
    
    /**
     * Verifica se este espaço é local.
     * @return true se LOCAL
     */
    public boolean isLocal() {
        return this == LOCAL;
    }
    
    /**
     * Verifica se este espaço é global/mundo.
     * @return true se WORLD
     */
    public boolean isWorld() {
        return this == WORLD;
    }
    
    /**
     * Obtém o espaço oposto.
     * @return LOCAL se for WORLD, WORLD se for LOCAL
     */
    public TransformSpace opposite() {
        return this == WORLD ? LOCAL : WORLD;
    }
    
    /**
     * Converte string para TransformSpace.
     * @param str String representando o espaço ("world", "local", "parent")
     * @return TransformSpace correspondente, ou WORLD se inválido
     */
    public static TransformSpace fromString(String str) {
        if (str == null) return WORLD;
        switch (str.toLowerCase().trim()) {
            case "local":
            case "self":
                return LOCAL;
            case "parent":
                return PARENT;
            case "world":
            case "global":
            default:
                return WORLD;
        }
    }
}
