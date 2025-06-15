package com.spiralknights.modpatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Standalone JAR mod patcher for Spiral Knights
 * Extracts and simplifies the mod loading functionality from Knight Launcher
 */
public class ModPatcher {

    private static final String CURRENT_DIR = System.getProperty("user.dir");
    private static final String MODS_DIR = CURRENT_DIR + File.separator + "mods";
    private static final String CODE_DIR = CURRENT_DIR + File.separator + "code";
    private static final String TARGET_JAR = CODE_DIR + File.separator + "projectx-pcode.jar";
    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final String MAIN_CLASS_KEY = "Main-Class:";
    private static final String NAME_KEY = "Name:";

    public static void main(String[] args) {
        System.out.println("=== Spiral Knights JAR Mod Patcher ===");
        System.out.println("Working directory: " + CURRENT_DIR);
        System.out.println("Mods directory: " + MODS_DIR);
        System.out.println("Target JAR: " + TARGET_JAR);
        System.out.println();

        try {
            // Validate environment
            if (!validateEnvironment()) {
                System.err.println("Environment validation failed. Exiting.");
                System.exit(1);
            }

            // Load and apply mods
            loadJarMods();

            System.out.println("Mod patching completed successfully!");

        } catch (Exception e) {
            System.err.println("Error during mod patching: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean validateEnvironment() {
        System.out.println("Validating environment...");

        // Check if mods directory exists
        File modsDir = new File(MODS_DIR);
        if (!modsDir.exists()) {
            System.out.println("Mods directory doesn't exist, creating: " + MODS_DIR);
            if (!modsDir.mkdirs()) {
                System.err.println("Failed to create mods directory");
                return false;
            }
        }

        // Check if code directory exists
        File codeDir = new File(CODE_DIR);
        if (!codeDir.exists()) {
            System.err.println("Code directory not found: " + CODE_DIR);
            System.err.println("Make sure you're running this from the Spiral Knights root directory");
            return false;
        }

        // Check if target JAR exists
        File targetJar = new File(TARGET_JAR);
        if (!targetJar.exists()) {
            System.err.println("Target JAR not found: " + TARGET_JAR);
            System.err.println("Make sure Spiral Knights is properly installed");
            return false;
        }

        System.out.println("Environment validation passed!");
        return true;
    }

    private static void loadJarMods() {
        System.out.println("Scanning for JAR mods...");

        File modsDir = new File(MODS_DIR);
        File[] files = modsDir.listFiles();

        if (files == null || files.length == 0) {
            System.out.println("No files found in mods directory");
            return;
        }

        // Find all JAR files
        List<File> jarFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".jar")) {
                jarFiles.add(file);
            }
        }

        if (jarFiles.isEmpty()) {
            System.out.println("No JAR mods found in mods directory");
            return;
        }

        System.out.println("Found " + jarFiles.size() + " JAR mod(s):");
        for (File jar : jarFiles) {
            System.out.println("  - " + jar.getName());
        }
        System.out.println();

        // Load JARs into classpath
        loadJarsIntoClasspath(jarFiles);

        // Load and mount mod classes
        loadAndMountModClasses(jarFiles);
    }

    private static void loadJarsIntoClasspath(List<File> jarFiles) {
        System.out.println("Loading JARs into classpath...");

        try {
            Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            boolean wasAccessible = addUrlMethod.isAccessible();
            addUrlMethod.setAccessible(true);

            for (File jar : jarFiles) {
                try {
                    addUrlMethod.invoke(classLoader, jar.toURI().toURL());
                    System.out.println("  ✓ Loaded: " + jar.getName());
                } catch (Exception e) {
                    System.err.println("  ✗ Failed to load: " + jar.getName() + " - " + e.getMessage());
                }
            }

            addUrlMethod.setAccessible(wasAccessible);

        } catch (Exception e) {
            System.err.println("Failed to setup JAR loading: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void loadAndMountModClasses(List<File> jarFiles) {
        System.out.println("Loading and mounting mod classes...");

        Map<String, Class<?>> modClasses = new LinkedHashMap<>();

        for (File jar : jarFiles) {
            // Read manifest to find main class
            String manifest = readZipEntry(jar, MANIFEST_PATH);
            if (manifest == null || manifest.isEmpty()) {
                System.err.println("  ✗ No manifest found in: " + jar.getName());
                continue;
            }

            String mainClassName = null;
            String modName = null;

            for (String line : manifest.split("\n")) {
                line = line.trim();
                if (line.startsWith(MAIN_CLASS_KEY)) {
                    mainClassName = line.replace(MAIN_CLASS_KEY, "").trim();
                } else if (line.startsWith(NAME_KEY)) {
                    modName = line.replace(NAME_KEY, "").trim();
                }
            }

            if (mainClassName == null || mainClassName.isEmpty()) {
                System.err.println("  ✗ No Main-Class found in manifest: " + jar.getName());
                continue;
            }

            if (modName == null) {
                modName = jar.getName();
            }

            // Load the class
            try {
                Class<?> modClass = Class.forName(mainClassName);
                modClasses.put(modName, modClass);
                System.out.println("  ✓ Loaded class: " + mainClassName + " from " + jar.getName());
            } catch (Exception e) {
                System.err.println("  ✗ Failed to load class: " + mainClassName + " from " + jar.getName() + " - " + e.getMessage());
            }
        }

        if (!modClasses.isEmpty()) {
            mountMods(modClasses);
        }
    }

    private static void mountMods(Map<String, Class<?>> modClasses) {
        System.out.println("Mounting mods...");

        for (Map.Entry<String, Class<?>> entry : modClasses.entrySet()) {
            String modName = entry.getKey();
            Class<?> modClass = entry.getValue();

            try {
                System.out.println("  Mounting: " + modName);
                Method mountMethod = modClass.getDeclaredMethod("mount");
                mountMethod.setAccessible(true);
                mountMethod.invoke(null);
                System.out.println("  ✓ Successfully mounted: " + modName);
            } catch (NoSuchMethodException e) {
                System.err.println("  ✗ No 'mount' method found in: " + modName);
            } catch (Exception e) {
                System.err.println("  ✗ Failed to mount: " + modName + " - " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static String readZipEntry(File zipFile, String entryName) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            java.util.zip.ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }

            StringBuilder content = new StringBuilder();
            try (InputStream is = zip.getInputStream(entry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            return content.toString();

        } catch (Exception e) {
            System.err.println("Failed to read " + entryName + " from " + zipFile.getName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Get current JVM version for compatibility checking
     */
    private static int getJVMVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }
}