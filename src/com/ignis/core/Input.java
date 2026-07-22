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

    // Eventos AWT chegam pela Event Dispatch Thread, enquanto update() e os
    // leitores rodam no loop do jogo. Um unico lock protege o snapshot inteiro.
    private final Object stateLock = new Object();

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
    // Buffer do mouse (simetrico ao do teclado): eventos AWT e injecao enfileiram
    // aqui e a update() os PROMOVE a just-pressed/just-released. Sem isto, o "just"
    // era limpo no fim da update() antes de os scripts lerem no mesmo tick — o evento
    // de um frame (real e injetado) nunca chegava. Indices 1..3.
    private Set<Integer> mouseToPress = new HashSet<>();
    private Set<Integer> mouseToRelease = new HashSet<>();

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
        synchronized (stateLock) {
            keysPressed.clear();
            keysToAdd.clear();
            keysToRemove.clear();
            keysJustPressed.clear();
            keysJustReleased.clear();
            mouseToPress.clear();
            mouseToRelease.clear();
            for (int i = 0; i < mouseButtons.length; i++) {
                mouseButtons[i] = false;
                mouseJustPressed[i] = false;
                mouseJustReleased[i] = false;
            }
        }
    }

    /**
     * Atualiza o estado do Input (deve ser chamado uma vez por frame)
     */
    public static void update() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            // Limpar estados "just pressed" e "just released" (teclado e mouse) ANTES
            // de promover o buffer deste frame: assim os flags refletem os eventos
            // processados AGORA e sobrevivem ate os scripts lerem no mesmo tick.
            input.keysJustPressed.clear();
            input.keysJustReleased.clear();
            for (int i = 0; i < 4; i++) {
                input.mouseJustPressed[i] = false;
                input.mouseJustReleased[i] = false;
            }

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

            // Processar botoes do mouse pressionados (mesma promocao do teclado).
            for (Integer b : input.mouseToPress) {
                if (b >= 1 && b <= 3 && !input.mouseButtons[b]) {
                    input.mouseJustPressed[b] = true;
                }
                if (b >= 1 && b <= 3) input.mouseButtons[b] = true;
            }
            input.mouseToPress.clear();

            // Processar botoes do mouse soltos.
            for (Integer b : input.mouseToRelease) {
                if (b >= 1 && b <= 3 && input.mouseButtons[b]) {
                    input.mouseJustReleased[b] = true;
                }
                if (b >= 1 && b <= 3) input.mouseButtons[b] = false;
            }
            input.mouseToRelease.clear();
        }
    }

    // ==================== INJECAO PROGRAMATICA (MCP / testes por agente) ====================

    /**
     * Injeta uma tecla como se o teclado a tivesse pressionado/soltado. Passa pelo
     * mesmo buffer dos eventos AWT ({@code keysToAdd}/{@code keysToRemove}), de modo
     * que a proxima {@link #update()} promove a tecla para "just pressed" e depois
     * "pressed" — semantica identica ao input real. Usado pela ferramenta MCP
     * {@code inject_input} para QA narrativo sem depender do foco da janela.
     *
     * @param keyCode codigo VK (java.awt.event.KeyEvent.VK_*)
     * @param pressed true = pressionar, false = soltar
     */
    public static void injectKey(int keyCode, boolean pressed) {
        Input input = getInstance();
        synchronized (input.stateLock) {
            if (pressed) {
                input.keysToRemove.remove(keyCode);
                input.keysToAdd.add(keyCode);
            } else {
                input.keysToAdd.remove(keyCode);
                input.keysToRemove.add(keyCode);
            }
        }
    }

    /**
     * Injeta um botao do mouse (1=esquerdo, 2=meio, 3=direito). Passa pelo mesmo
     * buffer dos eventos AWT, entao a proxima {@link #update()} promove para
     * "just pressed"/"just released" por um frame — semantica identica ao mouse real.
     */
    public static void injectMouseButton(int button, boolean pressed) {
        if (button < 1 || button > 3) return;
        Input input = getInstance();
        synchronized (input.stateLock) {
            if (pressed) {
                input.mouseToRelease.remove(button);
                input.mouseToPress.add(button);
            } else {
                input.mouseToPress.remove(button);
                input.mouseToRelease.add(button);
            }
        }
    }

    /** Posiciona o cursor virtual (coordenadas do viewport). */
    public static void injectMouseMove(int x, int y) {
        Input input = getInstance();
        synchronized (input.stateLock) {
            input.mouseX = x;
            input.mouseY = y;
        }
    }

    /**
     * Zera TODO o estado de teclado e mouse (mesma limpeza da perda de foco). Usado
     * pela ferramenta MCP {@code release_all_inputs} para garantir que nenhuma tecla
     * fique "presa" ao fim de uma sessao de teste, mesmo apos excecao.
     */
    public static void resetAll() {
        getInstance().clearAll();
    }

    // ==================== KEYBOARD API ====================

    /**
     * Verifica se uma tecla estÃ¡ atualmente pressionada
     * @param keyCode CÃ³digo da tecla (use KeyEvent.VK_*)
     * @return true se a tecla estÃ¡ pressionada
     */
    public static boolean isKeyPressed(int keyCode) {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.keysPressed.contains(keyCode);
        }
    }

    /**
     * Verifica se uma tecla acabou de ser pressionada neste frame
     * @param keyCode CÃ³digo da tecla (use KeyEvent.VK_*)
     * @return true se a tecla acabou de ser pressionada
     */
    public static boolean isKeyJustPressed(int keyCode) {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.keysJustPressed.contains(keyCode);
        }
    }

    /**
     * Verifica se uma tecla acabou de ser solta neste frame
     * @param keyCode CÃ³digo da tecla (use KeyEvent.VK_*)
     * @return true se a tecla acabou de ser solta
     */
    public static boolean isKeyJustReleased(int keyCode) {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.keysJustReleased.contains(keyCode);
        }
    }

    /**
     * Verifica se a tecla de movimento para cima estÃ¡ pressionada (W ou UP)
     */
    public static boolean isUpPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.keysPressed.contains(KeyEvent.VK_W) || input.keysPressed.contains(KeyEvent.VK_UP);
        }
    }

    /**
     * Verifica se a tecla de movimento para baixo estÃ¡ pressionada (S ou DOWN)
     */
    public static boolean isDownPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.keysPressed.contains(KeyEvent.VK_S) || input.keysPressed.contains(KeyEvent.VK_DOWN);
        }
    }

    /**
     * Verifica se a tecla de movimento para esquerda estÃ¡ pressionada (A ou LEFT)
     */
    public static boolean isLeftPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.keysPressed.contains(KeyEvent.VK_A) || input.keysPressed.contains(KeyEvent.VK_LEFT);
        }
    }

    /**
     * Verifica se a tecla de movimento para direita estÃ¡ pressionada (D ou RIGHT)
     */
    public static boolean isRightPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.keysPressed.contains(KeyEvent.VK_D) || input.keysPressed.contains(KeyEvent.VK_RIGHT);
        }
    }

    /**
     * Retorna o eixo horizontal (-1, 0, 1)
     * -1 = esquerda, 0 = nenhum, 1 = direita
     */
    public static int getHorizontalAxis() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            int axis = 0;
            if (input.keysPressed.contains(KeyEvent.VK_A) || input.keysPressed.contains(KeyEvent.VK_LEFT)) axis -= 1;
            if (input.keysPressed.contains(KeyEvent.VK_D) || input.keysPressed.contains(KeyEvent.VK_RIGHT)) axis += 1;
            return axis;
        }
    }

    /**
     * Retorna o eixo vertical (-1, 0, 1)
     * -1 = cima (W), 0 = nenhum, 1 = baixo (S)
     * Em coordenadas de tela, Y aumenta para baixo.
     */
    public static int getVerticalAxis() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            int axis = 0;
            if (input.keysPressed.contains(KeyEvent.VK_W) || input.keysPressed.contains(KeyEvent.VK_UP)) axis -= 1;
            if (input.keysPressed.contains(KeyEvent.VK_S) || input.keysPressed.contains(KeyEvent.VK_DOWN)) axis += 1;
            return axis;
        }
    }

    // ==================== MOUSE API ====================

    /**
     * Retorna a posiÃ§Ã£o X do mouse
     */
    public static int getMouseX() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.mouseX;
        }
    }

    /**
     * Retorna a posiÃ§Ã£o Y do mouse
     */
    public static int getMouseY() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.mouseY;
        }
    }

    /**
     * Verifica se o botÃ£o esquerdo do mouse estÃ¡ pressionado
     */
    public static boolean isMouseLeftPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.mouseButtons[1];
        }
    }

    /**
     * Verifica se o botÃ£o direito do mouse estÃ¡ pressionado
     */
    public static boolean isMouseRightPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.mouseButtons[3];
        }
    }

    /**
     * Verifica se o botÃ£o do meio do mouse estÃ¡ pressionado
     */
    public static boolean isMouseMiddlePressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.mouseButtons[2];
        }
    }

    /**
     * Verifica se o botÃ£o esquerdo do mouse acabou de ser pressionado
     */
    public static boolean isMouseLeftJustPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.mouseJustPressed[1];
        }
    }

    /**
     * Verifica se o botÃ£o direito do mouse acabou de ser pressionado
     */
    public static boolean isMouseRightJustPressed() {
        Input input = getInstance();
        synchronized (input.stateLock) {
            return input.mouseJustPressed[3];
        }
    }

    // ==================== KEY LISTENER IMPLEMENTATION ====================

    @Override
    public void keyPressed(KeyEvent e) {
        synchronized (stateLock) {
            keysToAdd.add(e.getKeyCode());
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        synchronized (stateLock) {
            keysToRemove.add(e.getKeyCode());
        }
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
            synchronized (stateLock) {
                mouseToRelease.remove(button);
                mouseToPress.add(button);
            }
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        int button = e.getButton();
        if (button >= 1 && button <= 3) {
            synchronized (stateLock) {
                mouseToPress.remove(button);
                mouseToRelease.add(button);
            }
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
        synchronized (stateLock) {
            mouseX = e.getX();
            mouseY = e.getY();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        synchronized (stateLock) {
            mouseX = e.getX();
            mouseY = e.getY();
        }
    }
}
