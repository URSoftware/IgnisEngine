package com.rimurusurvivors.domain;

/**
 * Comandos taticos disponiveis durante o duelo contra o lider da matilha.
 */
public enum BattleCommand {
    ANALYZE("Analisar", "Revela intencao, fragilidades e condicao de rendicao sem consumir recurso nem RNG."),
    WATER_BLADE("Hidrolamina", "Ataque preciso com lamina de agua. Consome 10 magicules."),
    PREDATOR("Predador", "Habilidade devoradora finalizadora. Exige ruptura de moral ou HP critico."),
    DEFEND("Defender", "Abre janela reativa para bloquear, esquivar ou aparar a investida."),
    GOBLIN_SUPPORT("Apoio Goblin", "Comando de suporte cuja eficacia e determinada pelas preparacoes."),
    NEGOTIATE("Negociar", "Tenta rendicao narrativa. Liberado apenas durante a ruptura de moral.");

    private final String name;
    private final String description;

    BattleCommand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String commandName() {
        return name;
    }

    public String description() {
        return description;
    }
}
