package com.ignis.core;

import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Utility class to load, scale, and apply the Ignis application icon.
 * Supports multiple resolutions to ensure proper Windows DPI scaling
 * and rendering across Explorer, taskbar, main window, and Builder outputs.
 */
public final class AppIconHelper {

    private static List<Image> cachedIcons = null;

    private AppIconHelper() {
    }

    /**
     * Gets a list of scaled images for the Ignis application icon
     * in various resolutions: 16x16, 24x24, 32x32, 48x48, 64x64, 128x128, 256x256.
     */
    public static synchronized List<Image> getAppIcons() {
        if (cachedIcons != null) {
            return cachedIcons;
        }

        cachedIcons = new ArrayList<>();
        URL iconUrl = AppIconHelper.class.getResource("/com/ignis/core_assets/icons/IconeIgnis.png");
        if (iconUrl == null) {
            IgnisLogger.error("[AppIconHelper] IconeIgnis.png not found in classpath");
            return cachedIcons;
        }

        try {
            BufferedImage baseImage = ImageIO.read(iconUrl);
            if (baseImage != null) {
                int[] resolutions = {16, 24, 32, 48, 64, 128, 256};
                for (int size : resolutions) {
                    BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                    java.awt.Graphics2D g2 = scaled.createGraphics();
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                    g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.drawImage(baseImage, 0, 0, size, size, null);
                    g2.dispose();
                    cachedIcons.add(scaled);
                }
            }
        } catch (IOException e) {
            IgnisLogger.error("[AppIconHelper] Failed to load application icons: " + e.getMessage());
        }

        return cachedIcons;
    }

    /**
     * Sets the application icons on the given window.
     */
    public static void setWindowIcon(Window window) {
        try {
            List<Image> icons = getAppIcons();
            if (icons != null && !icons.isEmpty()) {
                window.setIconImages(icons);
            }
        } catch (Exception e) {
            IgnisLogger.error("[AppIconHelper] Error setting icon for window " + window.getClass().getName() + ": " + e.getMessage());
        }
    }
}
