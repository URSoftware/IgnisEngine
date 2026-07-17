package com.ignis.core;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.HashSet;
import java.util.Set;

/**
 * Sistema de Input para o motor Ignis.
 * Fornece acesso ao estado do teclado e mouse para os scripts.
 * 
 * Uso em scripts:
 *   if (Input.isKeyPressed(KeyEvent.VK_SPACE)) { ... }
 *   if (Input.isKeyJustPressed(KeyEvent.VK_A)) { ... }
 *   int mouseX = Input.getMouseX();
 */
public class Input implements KeyListener, MouseListener, MouseMotionListener {

    // Singleton instance
    private static Input instance;

    // Teclas atualmente pressionadas
    private Set<Integer> keysPressed = new HashSet<>();
    
    // Teclas que acabaram de ser pressionadas neste frame
    private Set<Integer> keysJustPressed = new HashSet<>();
    
    // Teclas que acabaram de ser soltas neste frame
    private Set<Integer> keysJustReleased = new HashSet<>();
    
    // Buffer para atualizar no prÃ³ximo frame
    private Set<Integer> keysToAdd = new HashSet<>();
    private Set<Integer> keysToRemove = new HashSet<>();

    // Estado do mouse
    private int mouseX = 0;
    private int mouseY = 0;
    private boolean[] mouseButtons = new boolean[4]; // 0=unused, 1=left, 2=middle, 3=right
    private boolean[] mouseJustPressed = new boolean[4];
    private boolean[] mouseJustReleased = new boolean[4];

    private Input() {
        // Construtor privado para singleton
    }

    /**
     * ObtÃ©m a instÃ¢ncia singleton do Input
     */
    public static Input getInstance() {
        if (instance == null) {
            instance = new Input();
        }
        return instance;
    }

    /**
     * Inicializa o sistema de Input com um Game canvas
     */
    public static void init(Game game) {
        Input input = getInstance();
        game.addKeyListener(input);
        game.addMouseListener(input);
        game.addMouseMotionListener(input);
        game.setFocusable(true);
        game.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                input.clearAll();
            }
        });
    }

    /**
     * Limpa todo o estado de teclado e mouse. Chamado ao perder foco da janela
     * para evitar que uma tecla solta fora da janela fique "presa" como pressionada
     * (ex.: Alt-Tab enquanto segura W, ou perda de foco durante recompilacao/restart).
     */
    private void clearAll() {
        keysPressed.clear();
        keysToAdd.clear();
        keysToRemove.clear();
        keysJustPressed.clear();
        keysJustReleased.clear();
        for (int i = 0; i < mouseButtons.length; i++) {
            mouseButtons[i] = false;
            mouseJustPressed[i] = false;
            mouseJustReleased[i] = false;
        }
    }

    /**
     * Atualiza o estado do Input (deve ser chamado uma vez por frame)
     */
    public static void update() {
        Input input = getInstance();
        
        // Limpar estados "just pressed" e "just released"
        input.keysJustPressed.clear();
        input.keysJustReleased.clear();
        
        // Processar teclas que foram pressionadas
        for (Integer key : input.keysToAdd) {
            if (!input.keysPressed.contains(key)) {
                input.keysJustPressed.add(key);
            }
            input.keysPressed.add(key);
        }
        input.keysToAdd.clear();
        
        // Processar teclas que foram soltas
        for (Integer key : input.keysToRemove) {
            if (input.keysPressed.contains(key)) {
                input.keysJustReleased.add(key);
            }
            input.keysPressed.remove(key);
        }
        input.keysToRemove.clear();
        
        // Limpar estados "just" do mouse
        for (int i = 0; i < 4; i++) {
            input.mouseJustPressed[i] = false;
            input.mouseJustReleased[i] = false;
        }
    }

    // ==================== KEYBOARD API ====================

    /**
     * Verifica se uma tecla estÃ¡ atualmente pressionada
     * @param keyCode CÃ³digo da tecla (use KeyEvent.VK_*)
     * @return true se a tecla estÃ¡ pressionada
     */
    public static boolean isKeyPressed(int keyCode) {
        return getInstance().keysPressed.contains(keyCode);
    }

    /**
     * Verifica se uma tecla acabou de ser pressionada neste frame
     * @param keyCode CÃ³digo da tecla (use KeyEvent.VK_*)
     * @return true se a tecla acabou de ser pressionada
     */
    public static boolean isKeyJustPressed(int keyCode) {
        return getInstance().keysJustPressed.contains(keyCode);
    }

    /**
     * Verifica se uma tecla acabou de ser solta neste frame
     * @param keyCode CÃ³digo da tecla (use KeyEvent.VK_*)
     * @return true se a tecla acabou de ser solta
     */
    public static boolean isKeyJustReleased(int keyCode) {
        return getInstance().keysJustReleased.contains(keyCode);
    }

    /**
     * Verifica se a tecla de movimento para cima estÃ¡ pressionada (W ou UP)
     */
    public static boolean isUpPressed() {
        return isKeyPressed(KeyEvent.VK_W) || isKeyPressed(KeyEvent.VK_UP);
    }

    /**
     * Verifica se a tecla de movimento para baixo estÃ¡ pressionada (S ou DOWN)
     */
    public static boolean isDownPressed() {
        return isKeyPressed(KeyEvent.VK_S) || isKeyPressed(KeyEvent.VK_DOWN);
    }

    /**
     * Verifica se a tecla de movimento para esquerda estÃ¡ pressionada (A ou LEFT)
     */
    public static boolean isLeftPressed() {
        return isKeyPressed(KeyEvent.VK_A) || isKeyPressed(KeyEvent.VK_LEFT);
    }

    /**
     * Verifica se a tecla de movimento para direita estÃ¡ pressionada (D ou RIGHT)
     */
    public static boolean isRightPressed() {
        return isKeyPressed(KeyEvent.VK_D) || isKeyPressed(KeyEvent.VK_RIGHT);
    }

    /**
     * Retorna o eixo horizontal (-1, 0, 1)
     * -1 = esquerda, 0 = nenhum, 1 = direita
     */
    public static int getHorizontalAxis() {
        int axis = 0;
        if (isLeftPressed()) axis -= 1;
        if (isRightPressed()) axis += 1;
        return axis;
    }

    /**
     * Retorna o eixo vertical (-1, 0, 1)
     * -1 = cima (W), 0 = nenhum, 1 = baixo (S)
     * Em coordenadas de tela, Y aumenta para baixo.
     */
    public static int getVerticalAxis() {
        int axis = 0;
        if (isUpPressed()) axis -= 1;
        if (isDownPressed()) axis += 1;
        return axis;
    }

    // ==================== MOUSE API ====================

    /**
     * Retorna a posiÃ§Ã£o X do mouse
     */
    public static int getMouseX() {
        return getInstance().mouseX;
    }

    /**
     * Retorna a posiÃ§Ã£o Y do mouse
     */
    public static int getMouseY() {
        return getInstance().mouseY;
    }

    /**
     * Verifica se o botÃ£o esquerdo do mouse estÃ¡ pressionado
     */
    public static boolean isMouseLeftPressed() {
        return getInstance().mouseButtons[1];
    }

    /**
     * Verifica se o botÃ£o direito do mouse estÃ¡ pressionado
     */
    public static boolean isMouseRightPressed() {
        return getInstance().mouseButtons[3];
    }

    /**
     * Verifica se o botÃ£o do meio do mouse estÃ¡ pressionado
     */
    public static boolean isMouseMiddlePressed() {
        return getInstance().mouseButtons[2];
    }

    /**
     * Verifica se o botÃ£o esquerdo do mouse acabou de ser pressionado
     */
    public static boolean isMouseLeftJustPressed() {
        return getInstance().mouseJustPressed[1];
    }

    /**
     * Verifica se o botÃ£o direito do mouse acabou de ser pressionado
     */
    public static boolean isMouseRightJustPressed() {
        return getInstance().mouseJustPressed[3];
    }

    // ==================== KEY LISTENER IMPLEMENTATION ====================

    @Override
    public void keyPressed(KeyEvent e) {
        keysToAdd.add(e.getKeyCode());
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keysToRemove.add(e.getKeyCode());
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // Not used
    }

    // ==================== MOUSE LISTENER IMPLEMENTATION ====================

    @Override
    public void mousePressed(MouseEvent e) {
        int button = e.getButton();
        if (button >= 1 && button <= 3) {
            mouseButtons[button] = true;
            mouseJustPressed[button] = true;
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int button = e.getButton();
        if (button >= 1 && button <= 3) {
            mouseButtons[button] = false;
            mouseJustReleased[button] = true;
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Not used
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // Not used
    }

    @Override
    public void mouseExited(MouseEvent e) {
        // Not used
    }

    // ==================== MOUSE MOTION LISTENER IMPLEMENTATION ====================

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }
}
