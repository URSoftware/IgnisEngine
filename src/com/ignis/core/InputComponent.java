package com.ignis.core;

import java.awt.event.KeyEvent;

/**
 * Componente funcional de movimentacao por input de teclado (W, A, S, D).
 * Atualiza a posicao do GameObject pai de forma suave usando deltaTime.
 */
public class InputComponent extends IgnisScript {
    
    @Serialize
    private float speed;

    /**
     * Cria um InputComponent definindo a velocidade inicial de movimento.
     * @param speed Velocidade em pixels por segundo.
     */
    public InputComponent(float speed) {
        this.speed = speed;
    }

    /**
     * Construtor padrao com velocidade padrao.
     */
    public InputComponent() {
        this.speed = 200.0f;
    }

    /**
     * Sobrescreve o metodo update para capturar o input e movimentar o GameObject pai.
     */
    @Override
    public void update(float deltaTime) {
        if (gameObject == null) return;
        
        float dx = 0;
        float dy = 0;
        
        // Verifica teclas pressionadas usando o singleton Input do motor
        if (Input.isKeyPressed(KeyEvent.VK_W)) {
            dy -= 1;
        }
        if (Input.isKeyPressed(KeyEvent.VK_S)) {
            dy += 1;
        }
        if (Input.isKeyPressed(KeyEvent.VK_A)) {
            dx -= 1;
        }
        if (Input.isKeyPressed(KeyEvent.VK_D)) {
            dx += 1;
        }
        
        // Normaliza movimento diagonal simples se necessário, ou move diretamente
        if (dx != 0 || dy != 0) {
            double newX = gameObject.getX() + dx * speed * deltaTime;
            double newY = gameObject.getY() + dy * speed * deltaTime;
            gameObject.setX(newX);
            gameObject.setY(newY);
        }
    }

    /**
     * Retorna a velocidade de movimento.
     */
    public float getSpeed() {
        return speed;
    }

    /**
     * Define a velocidade de movimento.
     */
    public void setSpeed(float speed) {
        this.speed = speed;
    }
}
