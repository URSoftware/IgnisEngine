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

    // ------------------------------------------------------------------
    // Serializacao generica (plano item 5): por padrao TODO componente
    // persiste seus campos @Serialize por reflexao — a Scene nao precisa de
    // branches hardcoded por tipo. Componentes com estado complexo (ex.:
    // CanvasComponent, que guarda uma arvore de UI) sobrescrevem os dois.
    // ------------------------------------------------------------------

    /** Serializa as propriedades deste componente para o JSON da cena. */
    public org.json.JSONObject saveProperties() {
        return ScriptSerializationHelper.saveComponentProperties(this);
    }

    /**
     * Restaura as propriedades deste componente a partir do JSON da cena.
     * @param props    Propriedades serializadas (nunca null).
     * @param resolver Resolve referencias a GameObject por nome (pode ser null).
     */
    public void loadProperties(org.json.JSONObject props,
                               ScriptSerializationHelper.GameObjectResolver resolver) {
        ScriptSerializationHelper.loadComponentProperties(this, props,
                resolver != null ? resolver : name -> null);
    }
}
