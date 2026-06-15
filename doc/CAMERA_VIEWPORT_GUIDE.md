# Guia de Câmera e Viewport (Camera & Viewport Guide)

> Guia prático de utilização e programação do sistema de câmeras e viewports da IgnisEngine. Complementa a especificação técnica [CAMERA_SYSTEM_DOCS.md](CAMERA_SYSTEM_DOCS.md).

---

## 1. O Componente Viewport

A classe `Viewport` (`com.ignis.core.Viewport`) gerencia a janela de visualização do jogo. Ela mapeia o tamanho da janela física da tela (em pixels) para a resolução virtual de referência do projeto (padrão: 1920x1080).

### Comportamento de Redimensionamento:
A engine utiliza o modo **Expand Viewport**. Ao redimensionar a janela do editor ou do jogo em runtime:
- As proporções visuais dos objetos **não** são esticadas.
- O campo de visão (FOV) do mundo se expande ou contrai, mostrando mais ou menos cenário para preencher o novo espaço físico da tela.
- A resolução de referência é mantida como guia para escalas de interface de usuário (UI).

---

## 2. A Câmera no Jogo (`Camera.java`)

A câmera (`com.ignis.core.Camera`) é um tipo especial de `GameObject`. Ela possui coordenadas físicas e pode ser movida, rotacionada e escalada (Zoom) usando as propriedades padrão de transformação.

### Funcionalidades de Câmera Suportadas:
- **Posição (Target Follow):** Define o foco da tela. Coordenada centralizada: posicionar a câmera em `(0, 0)` centraliza o ponto zero do mundo exatamente no centro do viewport da tela.
- **Zoom (Scale):** Valores maiores que `1.0` ampliam a visualização (aproximam a câmera), enquanto valores menores que `1.0` reduzem (afastam a câmera).
- **Rotação:** Gira a visualização do mundo ao redor do ponto focal central da câmera.
- **Limites (Bounds):** Restrições físicas para impedir que a câmera se afaste para áreas vazias do mapa (ex: limitar movimento entre as coordenadas X `[0, 5000]`).
- **Suavização (Camera Lerp):** Interpolação linear da posição para criar uma câmera suave que segue o jogador com inércia.
- **Tremor (Camera Shake):** Efeito de vibração física na tela (ideal para colisões de impacto, explosões ou feedbacks visuais de gameplay).

---

## 3. API de Câmera no IgnisScript

Dentro de seus scripts de comportamento, a classe `IgnisScript` fornece uma API direta para controle do sistema de câmeras:

| Assinatura do Método | Descrição |
|---|---|
| `setCameraPosition(double x, double y)` | Move a câmera principal para as coordenadas informadas. |
| `getCameraX()` / `getCameraY()` | Retorna a posição atual da câmera principal no mundo. |
| `setCameraZoom(double zoom)` | Altera o fator de escala de zoom da câmera. |
| `getCameraZoom()` | Retorna o fator de zoom atual. |
| `setCameraRotation(double deg)` | Gira a visualização da câmera principal em graus. |
| `cameraFollowThis()` | Configura a câmera principal para seguir automaticamente o GameObject dono deste script. |
| `cameraFollow(GameObject target)` | Configura a câmera principal para seguir a entidade alvo especificada. |
| `setCameraBounds(double minX, double minY, double maxX, double maxY)` | Define os limites mínimos e máximos da posição da câmera no mundo. |
| `cameraShake(double intensity, double duration)` | Aplica um efeito de tremor (shake) na tela com intensidade e duração (em segundos) definidas. |
| `worldToScreen(double x, double y)` | Converte coordenadas globais do mundo para pixels de tela. |
| `screenToWorld(double x, double y)` | Converte pixels de tela (como posição do mouse) para coordenadas do mundo. |

---

## 4. Exemplos Práticos de Implementação

### A. Câmera de Acompanhamento Suave com Limites (Camera Lerp + Bounds)
```java
public class SmoothCameraFollow extends IgnisScript {
    @Serialize
    private String targetObjectName = "Player";
    
    @Serialize
    private double lerpFactor = 0.1; // Fator de suavização (quanto menor, mais suave/inércia)
    
    private GameObject target;

    @Override
    public void start() {
        target = findObject(targetObjectName);
        
        // Define os limites máximos do cenário em pixels do mundo
        // Evita que a câmera mostre o "vazio" fora do cenário do jogo
        setCameraBounds(0, 0, 4000, 2000);
    }

    @Override
    public void update() {
        if (target == null) return;
        
        // Posição desejada (o jogador)
        double targetX = target.getX();
        double targetY = target.getY();
        
        // Posição atual da câmera
        double currentX = getCameraX();
        double currentY = getCameraY();
        
        // Interpolação Linear (LERP) para suavizar a transição
        double nextX = currentX + (targetX - currentX) * lerpFactor;
        double nextY = currentY + (targetY - currentY) * lerpFactor;
        
        // Aplica a nova posição à câmera
        setCameraPosition(nextX, nextY);
    }
}
```

### B. Efeito de dynamic Zoom baseados na Velocidade do Objeto
```java
public class DynamicZoomController extends IgnisScript {
    @Serialize
    private double minZoom = 0.8;
    
    @Serialize
    private double maxZoom = 1.3;
    
    private Player playerObject;

    @Override
    public void start() {
        // Encontra o player na cena
        java.util.List<GameObject> players = findObjectsByType("Player");
        if (!players.isEmpty()) {
            playerObject = (Player) players.get(0);
        }
    }

    @Override
    public void update() {
        if (playerObject == null) return;
        
        // Lógica: Se o jogador corre rápido, a câmera afasta (menor zoom)
        // Se o jogador está parado, a câmera aproxima (maior zoom)
        double speed = Math.abs(playerObject.getSpeed());
        double targetZoom = maxZoom - (speed / 10.0) * (maxZoom - minZoom);
        
        // Garante que o zoom fique dentro dos limites seguros
        targetZoom = Math.max(minZoom, Math.min(maxZoom, targetZoom));
        
        // Aplica o zoom interpolado suavemente
        double currentZoom = getCameraZoom();
        double nextZoom = currentZoom + (targetZoom - currentZoom) * 0.05;
        setCameraZoom(nextZoom);
    }
}
```

### C. Tremor de Tela ao Sofrer Dano (Camera Shake Trigger)
```java
public class DestructiveObstacle extends IgnisScript {
    @Serialize
    private double damageAmount = 25.0;

    @Override
    public void onCollisionEnter(CollisionEvent event) {
        // Verifica se colidiu com o jogador
        if (event.getOther().getType().equals("Player")) {
            // Dispara um tremor forte na câmera principal
            // Intensidade 15 pixels de vibração, por 0.4 segundos
            cameraShake(15.0, 0.4);
            
            // Lógica de dano
            Player p = (Player) event.getOther();
            p.applyDamage(damageAmount);
        }
    }
}
```
