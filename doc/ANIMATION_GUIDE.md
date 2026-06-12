# Sistema de Animação

Módulo de animação 2D do IgnisEngine (item 4 do [Roadmap](ROADMAP.md)): timeline de frames, keyframes com duração por quadro, reprodução em loop e transições suaves entre animações.

---

## Como Usar (Editor)

1. Selecione (opcional) o objeto que receberá a animação.
2. Menu **Tools → Animation Editor** (`Ctrl+Shift+A`).
3. Defina nome, FPS e marque **Loop** se desejar.
4. **Add...** adiciona frames a partir de `assets/sprites` (multi-seleção). Use **Up/Down/Remove** para ordenar.
5. **Play/Stop** pré-visualiza a animação.
6. **Save** grava em `project/assets/animations/<nome>.anim.json`.
7. **Assign to '<objeto>'** anexa um `Animator` ao objeto selecionado (autoplay). Pressione **Play** no editor para ver a animação rodando na cena.

---

## Arquitetura

```
com.ignis.animation/           (modelo puro — sem Swing, sem dependência do core)
├── AnimationFrame.java   # Keyframe: sprite (relativo) + duração (s)
├── SpriteAnimation.java  # Timeline nomeada de frames + loop; spritePathAt(t)
├── Animator.java         # Componente runtime: anima e expõe o frame atual
└── AnimationIO.java      # Persistência em assets/animations/*.anim.json
```

### Decisões-chave

- **Desacoplamento total**: o `Animator` nunca importa `GameObject`. Ele expõe `getCurrentSpritePath()`; o `GameObject` aplica o frame ao próprio sprite (`tickAnimator`). Isso mantém o módulo reutilizável (ex.: futuro sistema 3D, ferramentas).
- **Passo fixo**: o loop do jogo roda a 60 ticks/s; o `Animator` avança `1/60 s` por tick — determinístico.
- **Sprites compartilhados**: frames resolvem via `AssetResolver.loadImage` (cache com invalidação por mtime), então trocar de frame a cada quadro não relê o disco.
- **Caminhos relativos**: frames são gravados relativos ao projeto (`assets/sprites/...`), mantendo a animação portável entre máquinas e no Git.
- **Restauração de estado**: ao parar a simulação, o `Animator` é resetado e o sprite original do objeto (antes da animação) é restaurado, preservando o estado do editor.
- **Blend/transição**: `Animator.play(nome, waitForCurrent=true)` enfileira a próxima animação até a atual (não-loop) terminar, evitando cortes bruscos.

---

## Uso em Scripts (IgnisScript)

```java
@Override
public void start() {
    gameObject.getOrCreateAnimator().play("walk");
}

@Override
public void tick() {
    if (/* parou de andar */) {
        gameObject.getAnimator().play("idle");
    }
}
```

---

## Serialização

O `Animator` é persistido por entidade dentro da cena (`animator` no JSON da entidade) — aditivo e retrocompatível: cenas antigas sem o campo carregam normalmente. As definições de animação também podem viver como assets independentes em `assets/animations/`.

---

## 3D (Planejado)

O motor é 2D; o tier 3D do roadmap (skeletal animation, animation graph, state machine, retargeting, blend trees) é planejamento futuro. O modelo atual (`Animator` desacoplado, blend por transição, IO próprio) foi desenhado para receber esse tier sem reescrever a integração com o core.
