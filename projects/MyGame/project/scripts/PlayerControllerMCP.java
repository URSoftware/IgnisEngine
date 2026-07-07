import com.ignis.core.IgnisScript;
import com.ignis.core.*;
import com.fxutilities.fxevents.core.*;

/**
 * PlayerControllerMCP - criado remotamente pelo agente via bridge HTTP do MCP.
 * WASD/setas movem (px/s, independente de FPS); Q/E giram.
 * Obs.: no mundo do IgnisEngine o eixo Y cresce para cima, por isso o eixo
 * vertical do input e invertido (W/cima aumenta Y).
 */
public class PlayerControllerMCP extends IgnisScript {

    private double speed = 220.0;
    private double turnSpeed = 120.0;

    @Override
    public void start() {
        println("[PlayerControllerMCP] pronto: WASD/setas movem, Q/E giram.");
    }

    @Override
    public void tick() {
        handleInput();
    }

    private void handleInput() {
        double dt = getDeltaTime();
        double dx = getHorizontalAxis() * speed * dt;
        double dy = -getVerticalAxis() * speed * dt;
        if (dx != 0 || dy != 0) setPosition(getX() + dx, getY() + dy);
        if (isKeyPressed("E")) setRotation(getRotation() - turnSpeed * dt);
        if (isKeyPressed("Q")) setRotation(getRotation() + turnSpeed * dt);
    }
}
