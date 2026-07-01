import com.ignis.core.IgnisScript;
import com.ignis.core.Serialize;
import com.ignis.core.Input;
import java.awt.event.KeyEvent;

public class Player extends IgnisScript {

    @Serialize
    private float movementSpeed = 4.0f; // Speed at which the player moves
    
    @Serialize
    public GameObject other;


    @Override
    public void start() {
        // Initialization code for the player, if any.
        // For simple movement, no specific setup is required here.
    }

    @Override
    public void tick() {
        // Get the singleton instance of the Input manager
        Input input = Input.getInstance();

        // Handle movement based on WASD keys
        if (input.isKeyPressed(KeyEvent.VK_W)) {
            transform.y += movementSpeed; // Move up (Y increases upwards in Ignis)
        }
        if (input.isKeyPressed(KeyEvent.VK_S)) {
            transform.y -= movementSpeed; // Move down
        }
        if (input.isKeyPressed(KeyEvent.VK_A)) {
            transform.x -= movementSpeed; // Move left
        }
        if (input.isKeyPressed(KeyEvent.VK_D)) {
            transform.x += movementSpeed; // Move right
        }
    }
}
        