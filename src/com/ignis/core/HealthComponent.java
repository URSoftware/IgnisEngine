package com.ignis.core;

/**
 * Componente de jogabilidade que gerencia a vida e reage a impactos de colisão.
 */
public class HealthComponent extends Component {

    private int health = 100;

    /**
     * Construtor padrão do componente de vida.
     */
    public HealthComponent() {
    }

    @Override
    public void awake() {
        if (gameObject != null) {
            // Se inscreve no evento de entrada de colisão do GameObject proprietário
            gameObject.onCollisionEnter.subscribe(this::handleCollision);
        }
    }

    /**
     * Trata o impacto e reduz a vida do GameObject proprietário.
     */
    private void handleCollision(CollisionData data) {
        this.health -= 10;
        System.out.println("[HealthComponent] " + gameObject.getName() + " colidiu com " 
                + (data.getOther() != null ? data.getOther().getName() : "desconhecido")
                + " no ponto " + data.getCollisionPoint() 
                + ". Vida restante: " + health);
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }
}
