package com.ignis.core;

/**
 * Componente de fisica basica para o padrao Entidade-Componente do IgnisEngine.
 *
 * <p>Adiciona velocidade, aceleracao, gravidade e impulsos a um GameObject.
 * O loop do motor ({@link Game#tick}) ja chama {@link GameObject#tickComponents(float)}
 * para cada entidade em Play, o que por sua vez invoca {@link #update(float)} aqui.
 * Apos os componentes avancarem, o {@code CollisionManager.update()} do motor resolve
 * contato e aplica MTV — o Rigbody entao zera a componente de velocidade na normal
 * da superficie via {@link #notifyCollision(CollisionData)} (ponte legado->evento).</p>
 *
 * <p>Modelo de integracao: Euler semiclasse-explicito. Velocidade = fora * dt;
 * posicao = posicao + velocidade * dt. Simples e estavel para jogos 2D com
 * passo fixo de 1/60 s — o padrao deste motor. Massa e arrasto sao opcionais.</p>
 */
public class RigidbodyComponent extends Component {

    @Serialize
    private double velocityX = 0.0;
    @Serialize
    private double velocityY = 0.0;
    @Serialize
    private double accelerationX = 0.0;
    @Serialize
    private double accelerationY = 0.0;
    @Serialize
    private double gravityScale = 1.0;
    @Serialize
    private boolean useGravity = true;
    @Serialize
    private double mass = 1.0;
    @Serialize
    private double linearDrag = 0.0;
    @Serialize
    private boolean frozen = false;

    // Gravidade global do motor em px/s^2. Estatica para que todos os rigidbodies
    // compartilhem o mesmo valor sem que cada instancia precise ser configurada.
    private static double globalGravity = 980.0;

    @Override
    public void awake() {
        if (gameObject != null) {
            gameObject.onCollisionEnter.subscribe(this::onCollision);
        }
    }

    @Override
    public void start() {
    }

    /**
     * Avanca a fisica do corpo em um passo fixo de {@code deltaTime} (segundos).
     * Chamado automaticamente pelo loop do motor via tickComponents.
     *
     * @param deltaTime tempo decorrido em segundos desde o ultimo frame.
     */
    @Override
    public void update(float deltaTime) {
        if (frozen || gameObject == null) return;

        double dt = (double) deltaTime;

        // 1. Aplica gravidade (aceleracao constante para baixo, eixo Y cresce para baixo).
        if (useGravity) {
            accelerationY += globalGravity * gravityScale * dt;
        }

        // 2. Aplica arrasto linear (desaceleracao proporcional a velocidade).
        if (linearDrag > 0.0) {
            double dragFactor = Math.max(0.0, 1.0 - linearDrag * dt);
            velocityX *= dragFactor;
            velocityY *= dragFactor;
        }

        // 3. Integracao Euler semiclasse-explicito: velocidade += aceleracao * dt.
        velocityX += accelerationX * dt;
        velocityY += accelerationY * dt;

        // 4. Posicao += velocidade * dt.
        gameObject.setX(gameObject.getX() + velocityX * dt);
        gameObject.setY(gameObject.getY() + velocityY * dt);
    }

    /**
     * Reage a colisao zerando a componente de velocidade na direcao do impacto.
     * Chamado pelo evento {@link GameObject#onCollisionEnter} (ponte CollisionManager
     * legado -> evento EC). Para resposta fisica mais elaborada (bounce, friccao),
     * sub-classes podem sobrescrever este metodo.
     */
    private void onCollision(CollisionData data) {
        if (data == null || data.getOther() == null || gameObject == null) return;
        // Resolve da normal aproximada: vetor do outro para este objeto.
        double nx = (gameObject.getX() + gameObject.getWidth() / 2.0)
                  - (data.getOther().getX() + data.getOther().getWidth() / 2.0);
        double ny = (gameObject.getY() + gameObject.getHeight() / 2.0)
                  - (data.getOther().getY() + data.getOther().getHeight() / 2.0);
        double len = Math.sqrt(nx * nx + ny * ny);
        if (len < 0.0001) return;
        nx /= len;
        ny /= len;
        // Remove componente de velocidade na direcao da normal (para corpo solido).
        double dot = velocityX * nx + velocityY * ny;
        if (dot < 0.0) {
            velocityX -= dot * nx;
            velocityY -= dot * ny;
        }
    }

    /**
     * Aplica uma forca instantanea (aceleracao em px/s^2) ao corpo.
     * A forca e acumulada em accelerationX/Y e consumida no proximo update.
     *
     * @param fx componente X da forca em pixeis por segundo ao quadrado.
     * @param fy componente Y da forca em pixeis por segundo ao quadrado.
     */
    public void applyForce(double fx, double fy) {
        if (frozen) return;
        accelerationX += fx / mass;
        accelerationY += fy / mass;
    }

    /**
     * Aplica um impulso instantaneo (mudanca direta de velocidade).
     * Diferente de {@link #applyForce}, nao passa pela integracao — e util para saltos,
     *impactos e outros efeitos imediatos.
     *
     * @param ix componente X do impulso em pixeis por segundo.
     * @param iy componente Y do impulso em pixeis por segundo.
     */
    public void applyImpulse(double ix, double iy) {
        if (frozen) return;
        velocityX += ix / mass;
        velocityY += iy / mass;
    }

    /**
     * Zera aceleracao e velocidade. Chamado pelo motor apos cada integracao para
     * evitar acumulo de forca entre frames — scripts devem reaplicar forcas a cada tick.
     * Seefeito colateral desejavel para uso em scripts de usuario (e o padrao em engines
     * 2D modernas).
     */
    public void resetForces() {
        accelerationX = 0.0;
        accelerationY = 0.0;
    }

    // ---- Getters / Setters ----

    public double getVelocityX() {
        return velocityX;
    }

    public void setVelocityX(double velocityX) {
        this.velocityX = velocityX;
    }

    public double getVelocityY() {
        return velocityY;
    }

    public void setVelocityY(double velocityY) {
        this.velocityY = velocityY;
    }

    public double getAccelerationX() {
        return accelerationX;
    }

    public void setAccelerationX(double accelerationX) {
        this.accelerationX = accelerationX;
    }

    public double getAccelerationY() {
        return accelerationY;
    }

    public void setAccelerationY(double accelerationY) {
        this.accelerationY = accelerationY;
    }

    public double getGravityScale() {
        return gravityScale;
    }

    public void setGravityScale(double gravityScale) {
        this.gravityScale = gravityScale;
    }

    public boolean isUseGravity() {
        return useGravity;
    }

    public void setUseGravity(boolean useGravity) {
        this.useGravity = useGravity;
    }

    public double getMass() {
        return mass;
    }

    public void setMass(double mass) {
        this.mass = Math.max(0.01, mass);
    }

    public double getLinearDrag() {
        return linearDrag;
    }

    public void setLinearDrag(double linearDrag) {
        this.linearDrag = Math.max(0.0, linearDrag);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    /**
     * Gravidade global do motor em pixeis por segundo ao quadrado.
     * Padrao: 980 (aproximadamente 100 px por arma de queda por segundo).
     */
    public static double getGlobalGravity() {
        return globalGravity;
    }

    public static void setGlobalGravity(double gravity) {
        globalGravity = gravity;
    }
}
