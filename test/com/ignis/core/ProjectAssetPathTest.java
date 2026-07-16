package com.ignis.core;

import com.ignis.core.ui.UIImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectAssetPathTest {

    @TempDir
    Path projectFolder;

    @AfterEach
    void clearProjectFolder() {
        AssetResolver.setProjectFolder(null);
    }

    @Test
    void uiImagesAndAudioResolveRelativeToProjectFolder() throws Exception {
        Path imagePath = projectFolder.resolve("assets/ui/icon.png");
        Path audioPath = projectFolder.resolve("assets/music/theme.wav");
        Files.createDirectories(imagePath.getParent());
        Files.createDirectories(audioPath.getParent());
        ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_ARGB), "png", imagePath.toFile());
        Files.write(audioPath, new byte[] { 1, 2, 3 });
        AssetResolver.setProjectFolder(projectFolder.toFile());

        UIImage image = new UIImage("assets/ui/icon.png", 0, 0, 10, 10);

        assertNotNull(image.getImage());
        assertEquals(imagePath.toFile().getCanonicalFile(),
                AssetResolver.resolve("assets/ui/icon.png").getCanonicalFile());
        assertEquals(audioPath.toFile().getCanonicalFile(),
                IgnisSoundEngine.resolveAudioFile("assets/music/theme.wav").getCanonicalFile());
    }
}
