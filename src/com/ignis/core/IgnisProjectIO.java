package com.ignis.core;

import org.json.JSONObject;

import java.io.*;
import java.util.zip.*;

/**
 * Central IO class for the .ignis format
 * 
 * Project storage structure:
 * 
 * projects/               <- Root folder where all projects are stored
 *   [ProjectName]/        <- Main project folder
 *     [ProjectName].ignis <- Project file (ZIP containing metadata)
 *     project/            <- Folder where the developed GAME is stored
 *       assets/           <- Game resources
 *         sprites/        <- Sprite images and spritesheets
 *         sounds/         <- Sound effects (SFX)
 *         music/          <- Background music (BGM)
 *         fonts/          <- Custom fonts
 *         tilemaps/       <- Tilemaps and tilesets
 *         ui/             <- Interface elements
 *         animations/     <- Animation files
 *       scripts/          <- User scripts (behaviors, AI)
 *       scenes/           <- Game scenes
 *       prefabs/          <- Reusable prefabricated objects
 *       data/             <- Game data (configs, dialogs, saves)
 * 
 * The .ignis file is technically a ZIP containing:
 * - project.json (project information and settings)
 * - scenes/ (.scene.json scene files)
 * - assets/ (reference to used assets)
 */
public class IgnisProjectIO {

    private static final String PROJECT_FILE = "project.json";
    private static final String SCENES_DIR = "scenes/";
    private static final String ASSETS_DIR = "assets/";

    // Name of the folder where the developed game is stored
    public static final String PROJECT_FOLDER_NAME = "project";

    // Root folder where all projects are stored
    public static final String PROJECTS_ROOT_FOLDER = "projects";

    /**
     * Returns the root folder where all projects are stored
     */
    public static File getProjectsRootFolder() {
        File engineRoot = new File(System.getProperty("user.dir"));
        File projectsFolder = new File(engineRoot, PROJECTS_ROOT_FOLDER);
        if (!projectsFolder.exists()) {
            projectsFolder.mkdirs();
        }
        return projectsFolder;
    }

    /**
         * Saves a project in .ignis format
         * 
         * @param project   The project to be saved
         * @param ignisFile The destination .ignis file
         * @throws IOException In case of IO error
         */
        public static void save(Project project, File ignisFile) throws IOException {
            // If the file is being saved outside the projects folder, move it there
            File projectsRoot = getProjectsRootFolder();
            String projectName = ignisFile.getName().replace(".ignis", "");

            // Create project folder inside projects/
            File projectMainFolder = new File(projectsRoot, projectName);
            if (!projectMainFolder.exists()) {
                projectMainFolder.mkdirs();
            }

            // Create "project" folder inside the project folder
            File projectFolder = new File(projectMainFolder, PROJECT_FOLDER_NAME);
            ensureProjectFolderStructure(projectFolder);

            // Relative asset paths in scenes resolve against this folder
            AssetResolver.setProjectFolder(projectFolder);

            // The .ignis file goes into the project folder
            File actualIgnisFile = new File(projectMainFolder, projectName + ".ignis");

            // Create ZIP file
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(actualIgnisFile))) {

                // 1. Save project.json
                ZipEntry projectEntry = new ZipEntry(PROJECT_FILE);
                zos.putNextEntry(projectEntry);
                byte[] projectBytes = project.toJSON().toString(2).getBytes("UTF-8");
                zos.write(projectBytes);
                zos.closeEntry();

                // 2. Save ALL scenes
                for (Scene scene : project.getScenes()) {
                    String sceneFileName = SCENES_DIR + scene.getSceneName() + ".scene.json";
                    ZipEntry sceneEntry = new ZipEntry(sceneFileName);
                    zos.putNextEntry(sceneEntry);
                    byte[] sceneBytes = scene.toJSON().toString(2).getBytes("UTF-8");
                    zos.write(sceneBytes);
                    zos.closeEntry();
                }

                // 3. Create assets directory (empty for now)
                ZipEntry assetsDir = new ZipEntry(ASSETS_DIR);
                zos.putNextEntry(assetsDir);
                zos.closeEntry();

                // TODO: Copy assets into the ZIP
                // copyAssetsToZip(zos, project);
            }

            // Update file reference in project (use actual file in projects folder)
            project.setProjectFile(actualIgnisFile);

            com.ignis.core.IgnisLogger.info("Projeto salvo em: " + actualIgnisFile.getName());
        }

    /**
     * Returns the "project" folder associated with the .ignis file
     * Structure: projects/[projectName]/project/
     */
    public static File getProjectFolder(File ignisFile) {
        // The .ignis file should be at projects/[projectName]/[projectName].ignis
        // The project folder is at projects/[projectName]/project/
        File projectMainFolder = ignisFile.getParentFile();
        return new File(projectMainFolder, PROJECT_FOLDER_NAME);
    }

    /**
     * Returns the main project folder (where the .ignis and project folder are)
     */
    public static File getProjectMainFolder(File ignisFile) {
        return ignisFile.getParentFile();
    }

    /**
     * Creates the project folder structure if it doesn't exist.
     * This structure contains all resources of the developed game:
     * 
     * project/
     *   assets/
     *     sprites/    - Sprite images and spritesheets
     *     sounds/     - Sound effects (SFX)
     *     music/      - Background music (BGM)
     *     fonts/      - Custom fonts
     *     tilemaps/   - Tile maps and tilesets
     *     ui/         - Interface elements (buttons, icons, etc.)
     *     animations/ - Animation files
     *   scripts/      - User scripts (behaviors, AI, etc.)
     *   scenes/       - Game scenes
     *   prefabs/      - Reusable prefabricated objects
     *   data/         - Game data (configs, saves, dialogs, etc.)
     */
    public static void ensureProjectFolderStructure(File projectFolder) {
        if (!projectFolder.exists()) {
            projectFolder.mkdirs();
        }

        // Create standard subfolders for a complete game project
        String[] subfolders = {
                "assets",
                "assets/sprites",    // Sprites and spritesheets
                "assets/sounds",     // Sound effects (SFX)
                "assets/music",      // Background music (BGM)
                "assets/fonts",      // Custom fonts
                "assets/tilemaps",   // Tilemaps and tilesets
                "assets/ui",         // UI elements
                "assets/animations", // Animation files
                "scripts",           // User scripts
                "scenes",            // Game scenes
                "prefabs",           // Prefabricated objects
                "data"               // Game data (configs, dialogs, etc.)
        };
        for (String subfolder : subfolders) {
            File folder = new File(projectFolder, subfolder);
            if (!folder.exists()) {
                folder.mkdirs();
            }
        }
    }

    /**
         * Loads a project from .ignis format
         * 
         * @param ignisFile The .ignis file to be loaded
         * @param game      Reference to Game for entities
         * @return The loaded project
         * @throws IOException In case of IO error
         */
        public static Project load(File ignisFile, Game game) throws IOException {
            Project project = new Project();
            project.setProjectFile(ignisFile);

            // Must be set before parsing scenes: entities load their sprites
            // immediately, resolving relative paths against the project folder
            AssetResolver.setProjectFolder(getProjectFolder(ignisFile));

            // Extract and read the ZIP
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(ignisFile))) {
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();

                    if (entryName.equals(PROJECT_FILE)) {
                        // Read project.json
                        String json = readZipEntry(zis);
                        JSONObject projectJson = new JSONObject(json);

                        // Validate engine version
                        String fileVersion = projectJson.getString("engineVersion");
                        if (!isVersionCompatible(fileVersion)) {
                            System.err.println("Warning: File version (" + fileVersion +
                                    ") may not be compatible with the current engine (" + Project.ENGINE_VERSION + ")");
                        }

                        project.setProjectName(projectJson.getString("projectName"));
                        project.setMainScene(projectJson.getString("mainScene"));

                    } else if (entryName.startsWith(SCENES_DIR) && entryName.endsWith(".scene.json")) {
                        // Read scene
                        String json = readZipEntry(zis);
                        JSONObject sceneJson = new JSONObject(json);
                        Scene scene = Scene.fromJSON(sceneJson, game);
                        project.addScene(scene);
                    }

                    zis.closeEntry();
                }
            }

            // Set current scene to main scene or first scene
            Scene mainScene = project.getSceneByName(project.getMainScene());
            if (mainScene != null) {
                project.setCurrentScene(mainScene);
            } else if (!project.getScenes().isEmpty()) {
                project.setCurrentScene(project.getScenes().get(0));
            }

            com.ignis.core.IgnisLogger.info("Projeto carregado: " + project.getProjectName() + " (" + project.getScenes().size() + " cenas)");
            return project;
        }

    /**
     * Reads the content of a ZIP entry as String
     */
    private static String readZipEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toString("UTF-8");
    }

    /**
     * Checks if the file version is compatible with the current engine
     */
    private static boolean isVersionCompatible(String fileVersion) {
        // For now, accepts any version 0.x.x
        return fileVersion.startsWith("0.");
    }

    /**
     * Creates a new project with standard structure
     * 
     * @param projectName Project name
     * @return New empty project
     */
    public static Project createNew(String projectName) {
        Project project = new Project(projectName);
        project.setCurrentScene(new Scene("MainScene"));
        return project;
    }

    /**
     * Exports assets from a project to a directory
     * 
     * @param ignisFile The .ignis file
     * @param outputDir Output directory
     * @throws IOException In case of IO error
     */
    public static void extractAssets(File ignisFile, File outputDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(ignisFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith(ASSETS_DIR) && !entry.isDirectory()) {
                    File outputFile = new File(outputDir, entry.getName());
                    outputFile.getParentFile().mkdirs();

                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
