package com.ignis.core;

/**
 * Classe base conceitual para o padrao Entidade-Componente do IgnisEngine.
 * Cada componente representa uma peca modular que pode ser acoplada a um GameObject.
 */
public abstract class Component {
    
    // Referencia de volta para o GameObject dono deste componente
    public GameObject gameObject;

    // Ciclo de vida basico
    
    /**
     * Chamado imediatamente quando o componente e adicionado ao GameObject.
     */
    public void awake() {
    }

    /**
     * Chamado antes do primeiro frame/tick de simulacao.
     */
    public void start() {
    }

    /**
     * Chamado a cada frame da simulacao com o delta time correspondente.
     * @param deltaTime Tempo decorrido em segundos desde o ultimo frame.
     */
    public void update(float deltaTime) {
    }
}
