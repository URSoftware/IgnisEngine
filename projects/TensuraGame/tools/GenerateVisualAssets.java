import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/** Gera texturas autorais e reproduziveis usadas pela primeira arena. */
public final class GenerateVisualAssets {

    private GenerateVisualAssets() {
    }

    public static void main(String[] args) throws Exception {
        File project = new File(args.length == 0 ? "../project" : args[0]);
        File sprites = new File(project, "assets/sprites");
        if (!sprites.isDirectory() && !sprites.mkdirs()) {
            throw new IllegalStateException("Nao foi possivel criar " + sprites);
        }
        ImageIO.write(createJuraFloor(), "png", new File(sprites, "jura_forest_ground.png"));
    }

    private static BufferedImage createJuraFloor() {
        int size = 128;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        Random random = new Random(0x4A555241L);
        graphics.setColor(new Color(20, 57, 43));
        graphics.fillRect(0, 0, size, size);

        Color[] grass = {
                new Color(24, 68, 48), new Color(27, 76, 51),
                new Color(17, 50, 39), new Color(35, 83, 55)
        };
        for (int i = 0; i < 480; i++) {
            graphics.setColor(grass[random.nextInt(grass.length)]);
            int x = random.nextInt(size);
            int y = random.nextInt(size);
            int length = 1 + random.nextInt(4);
            graphics.drawLine(x, y, x + random.nextInt(3) - 1, y - length);
        }

        for (int i = 0; i < 18; i++) {
            int x = random.nextInt(size);
            int y = random.nextInt(size);
            graphics.setColor(new Color(11, 42, 35, 150));
            graphics.fillOval(x - 4, y - 2, 9, 5);
            graphics.setColor(new Color(42, 105, 67, 180));
            graphics.fillOval(x - 2, y - 3, 5, 4);
        }

        for (int i = 0; i < 7; i++) {
            int x = 4 + random.nextInt(size - 8);
            int y = 4 + random.nextInt(size - 8);
            graphics.setColor(new Color(63, 83, 73));
            graphics.fillRect(x, y, 3, 2);
            graphics.setColor(new Color(92, 116, 101));
            graphics.fillRect(x, y, 2, 1);
        }

        for (int i = 0; i < 5; i++) {
            int x = 3 + random.nextInt(size - 6);
            int y = 3 + random.nextInt(size - 6);
            graphics.setColor(new Color(76, 204, 220, 170));
            graphics.fillRect(x, y, 2, 2);
            graphics.setColor(new Color(183, 247, 250, 180));
            graphics.fillRect(x, y, 1, 1);
        }

        graphics.dispose();
        return image;
    }
}
