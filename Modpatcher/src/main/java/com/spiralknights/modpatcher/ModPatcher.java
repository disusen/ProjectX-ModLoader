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
import java.util.jar.JarFile;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import javassist.ClassPool;
import javassist.CtClass;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Standalone JAR mod patcher for Spiral Knights
 * Extracts and simplifies the mod loading functionality from Knight Launcher
 */
public class ModPatcher implements ClassFileTransformer {



    public static final String CURRENT_DIR = System.getProperty("user.dir");
    private static final String LOGS_DIR = CURRENT_DIR + File.separator + "logs"; // New constant
    private static final String MODS_DIR = CURRENT_DIR + File.separator + "mods";
    private static final String CODE_DIR = CURRENT_DIR + File.separator + "code";
    private static final String TARGET_JAR = CODE_DIR + File.separator + "projectx-pcode.jar";

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";
    private static final String MAIN_CLASS_KEY = "Main-Class:";
    private static final String NAME_KEY = "Name:";

    private static Instrumentation instrumentation;
    private static ModPatcher instance;
    private static Map<String, ModTransformer> modTransformers = new LinkedHashMap<>();

    // Interface that mods should implement for transformation
    public interface ModTransformer {
        boolean shouldTransform(String className);
        byte[] transform(String className, byte[] classfileBuffer) throws Exception;
    }

    public static void premain(String agentArgs, Instrumentation inst) {

        ModPatcher.instrumentation = inst;
        ModPatcher.instance = new ModPatcher();

        System.out.println("=== Spiral Knights JAR Mod Patcher (Agent Mode) ===");
        System.out.println("Working directory: " + CURRENT_DIR);
        System.out.println("Mods directory: " + MODS_DIR);
        System.out.println("Target JAR: " + TARGET_JAR);
        System.out.println();

        try {
            if (!validateEnvironment()) {
                System.err.println("Environment validation failed. Mod Patcher Agent will not proceed.");
                return;
            }

            // Register the transformer BEFORE loading mods
            inst.addTransformer(instance, true);
            System.out.println("Registered ClassFileTransformer");

            loadJarMods(); // This loads mod JARs and registers individual transformers

            // --- ADD THIS SECTION FOR RETRANSFORMATION ---
            System.out.println("[ModPatcher] Attempting to retransform already loaded classes...");
            Set<String> classesToRetransform = new HashSet<>();
            // Add all classes that your mods target
            classesToRetransform.add("com.threerings.projectx.dungeon.client.Q");
            classesToRetransform.add("com.threerings.projectx.dungeon.client.EndOfLevelWindow");
            // Add any other classes any future mod might target

            List<Class<?>> classesToApply = new ArrayList<>();
            for (Class<?> clazz : inst.getAllLoadedClasses()) {
                String className = clazz.getName();
                if (classesToRetransform.contains(className)) {
                    // Ensure the class is modifiable and retransformable
                    if (inst.isModifiableClass(clazz) && inst.isRetransformClassesSupported()) {
                        classesToApply.add(clazz);
                        System.out.println("[ModPatcher] Found already loaded class for retransformation: " + className);
                    } else {
                        System.out.println("[ModPatcher] Skipping retransformation for " + className + " (not modifiable or retransformable).");
                    }
                }
            }

            if (!classesToApply.isEmpty()) {
                try {
                    inst.retransformClasses(classesToApply.toArray(new Class[0]));
                    System.out.println("[ModPatcher] Successfully retransformed " + classesToApply.size() + " classes.");
                } catch (Exception e) {
                    System.err.println("[ModPatcher] Error during retransformation: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                System.out.println("[ModPatcher] No target classes found already loaded for retransformation.");
            }
            // --- END ADDED SECTION ---

            System.out.println("Mod patching completed successfully!");

        } catch (Exception e) {
            System.err.println("Error during mod patching in Agent Mode: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        // Convert internal class name format (com/example/Class) to normal format (com.example.Class)
        String normalClassName = className.replace('/', '.');

        // Start with the original bytecode
        byte[] currentBytecode = classfileBuffer;
        boolean wasTransformed = false;

        // Apply transformations sequentially - each transformer gets the output of the previous one
        for (Map.Entry<String, ModTransformer> entry : modTransformers.entrySet()) {
            String modName = entry.getKey();
            ModTransformer transformer = entry.getValue();

            try {
                if (transformer.shouldTransform(normalClassName)) {
                    System.out.println("[ModPatcher] Transforming class: " + normalClassName + " with mod: " + modName);

                    // Clear the specific class from Javassist's cache before transformation
                    // This ensures each transformer gets a fresh CtClass to work with
                    try {
                        ClassPool defaultPool = ClassPool.getDefault();
                        CtClass cachedClass = defaultPool.getOrNull(normalClassName);
                        if (cachedClass != null) {
                            cachedClass.detach();
                            System.out.println("[ModPatcher] Detached cached CtClass for: " + normalClassName);
                        }
                    } catch (Exception cacheException) {
                        System.err.println("[ModPatcher] Warning: Could not clear cache for " + normalClassName + ": " + cacheException.getMessage());
                        // Continue anyway - this is just optimization
                    }

                    byte[] transformedBytecode = transformer.transform(normalClassName, currentBytecode);

                    if (transformedBytecode != null) {
                        System.out.println("[ModPatcher] Successfully transformed: " + normalClassName + " with mod: " + modName);
                        currentBytecode = transformedBytecode; // Use the transformed bytecode for the next transformer
                        wasTransformed = true;
                    } else {
                        System.out.println("[ModPatcher] Mod " + modName + " returned null for: " + normalClassName + " (no changes)");
                    }
                }
            } catch (Exception e) {
                System.err.println("[ModPatcher] Error transforming " + normalClassName + " with mod " + modName + ": " + e.getMessage());
                e.printStackTrace();
                // Continue with other transformers even if one fails
            }
        }

        // Return the final bytecode if any transformations were applied, otherwise return null
        return wasTransformed ? currentBytecode : null;
    }

    // Method for mods to register their transformers
    public static void registerTransformer(String modName, ModTransformer transformer) {
        modTransformers.put(modName, transformer);
        System.out.println("[ModPatcher] Registered transformer for mod: " + modName);
    }

    public static void main(String[] args) {
        System.out.println("Running ModPatcher in Standalone Test Mode...");
        premain(null, null);
    }

    private static boolean validateEnvironment() {
        System.out.println("Validating environment...");

        File modsDir = new File(MODS_DIR);
        if (!modsDir.exists()) {
            System.out.println("Mods directory doesn't exist, creating: " + MODS_DIR);
            if (!modsDir.mkdirs()) {
                System.err.println("Failed to create mods directory");
                return false;
            }
        }

        File codeDir = new File(CODE_DIR);
        if (!codeDir.exists()) {
            System.err.println("Code directory not found: " + CODE_DIR);
            System.err.println("Make sure you're running this from the Spiral Knights root directory");
            return false;
        }

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

        loadJarsIntoClasspaths(jarFiles);
        loadAndMountModClasses(jarFiles);
    }

    private static void loadJarsIntoClasspaths(List<File> modJarFiles) {
        System.out.println("Loading JARs into classpaths...");

        if (instrumentation == null) {
            System.err.println("Error: Instrumentation instance is not available. Cannot dynamically load JARs.");
            throw new IllegalStateException("Instrumentation not available. ModPatcher must be run as a Java Agent.");
        }

        try {
            ClassPool pool = ClassPool.getDefault();

            System.out.println("Scanning game code directory for base JARs to add to JVM classpath and Javassist ClassPool...");
            File codeDir = new File(CODE_DIR);
            File[] gameJars = codeDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));

            if (gameJars != null) {
                for (File gameJar : gameJars) {
                    try {
                        pool.appendClassPath(gameJar.getAbsolutePath());
                        System.out.println("  ✓ Added game JAR to Javassist ClassPool: " + gameJar.getName());

                        instrumentation.appendToSystemClassLoaderSearch(new JarFile(gameJar));
                        System.out.println("  ✓ Added game JAR to System ClassLoader (via Instrumentation): " + gameJar.getName());

                    } catch (Exception e) {
                        System.err.println("  ✗ Failed to add game JAR: " + gameJar.getName() + " - " + e.getMessage());
                    }
                }
            } else {
                System.err.println("  ✗ No game JARs found in: " + CODE_DIR);
            }

            for (File jar : modJarFiles) {
                try {
                    instrumentation.appendToSystemClassLoaderSearch(new JarFile(jar));
                    System.out.println("  ✓ Loaded mod JAR: " + jar.getName());

                    pool.appendClassPath(jar.getAbsolutePath());
                    System.out.println("  ✓ Added mod JAR to Javassist ClassPool: " + jar.getName());

                } catch (Exception e) {
                    System.err.println("  ✗ Failed to load mod JAR: " + jar.getName() + " - " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to setup JAR loading: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static void loadAndMountModClasses(List<File> jarFiles) {
        System.out.println("Loading and mounting mod classes...");

        Map<String, Class<?>> modClasses = new LinkedHashMap<>();

        for (File jar : jarFiles) {
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

            try {
                ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
                System.out.println("  [DEBUG] Inspecting System ClassLoader for " + mainClassName + "...");
                if (systemClassLoader instanceof java.net.URLClassLoader) {
                    java.net.URL[] urls = ((java.net.URLClassLoader) systemClassLoader).getURLs();
                    System.out.println("  [DEBUG] System ClassLoader URLs (before Class.forName):");
                    for (java.net.URL url : urls) {
                        System.out.println("    - " + url.toString());
                    }
                } else {
                    System.out.println("  [DEBUG] System ClassLoader is not a URLClassLoader, cannot inspect URLs.");
                }
                System.out.println("  [DEBUG] Attempting Class.forName(" + mainClassName + ") for JAR: " + jar.getName());
                Class<?> modClass = Class.forName(mainClassName);
                System.out.println("  ✓ Loaded class: " + mainClassName + " from " + jar.getName());

                Method mountMethod = modClass.getDeclaredMethod("mount", String.class); // Or `mount()` if your method signature changed
                mountMethod.setAccessible(true);
                mountMethod.invoke(null, jar.getParentFile().getAbsolutePath()); // Or `invoke(null)`
                System.out.println("  ✓ Successfully mounted: " + modName);

            } catch (NoSuchMethodException e) {
                System.err.println("  ✗ No 'mount' method found in " + modName + " with the expected signature.");
                System.err.println("    Error: " + e.getMessage());
                e.printStackTrace(); // <-- Make sure this is here!
            } catch (java.lang.reflect.InvocationTargetException e) {
                System.err.println("  ✗ Mod '" + modName + "' threw an exception during mount: " + e.getTargetException().getMessage());
                e.getTargetException().printStackTrace(); // <-- Make sure this is here!
            } catch (Exception e) { // This is likely the catch block hitting the error you're seeing
                System.err.println("  ✗ Failed to load/mount: " + modName + " from " + jar.getName() + " - " + e.getMessage());
                e.printStackTrace(); // <-- YOU NEED THIS FULL STACK TRACE!
            }
        }

        if (!modClasses.isEmpty()) {
            mountMods(modClasses);
        }
    }

    private static void mountMods(Map<String, Class<?>> modClasses) {
        System.out.println("Mounting mods...");

        File targetGameJarFile = new File(TARGET_JAR);
        String targetGameJarPath = targetGameJarFile.getAbsolutePath();

        for (Map.Entry<String, Class<?>> entry : modClasses.entrySet()) {
            String modName = entry.getKey();
            Class<?> modClass = entry.getValue();

            try {
                System.out.println("  Mounting: " + modName);
                Method mountMethod = modClass.getDeclaredMethod("mount", String.class);
                mountMethod.setAccessible(true);
                mountMethod.invoke(null, targetGameJarPath);
                System.out.println("  ✓ Successfully mounted: " + modName);
            } catch (NoSuchMethodException e) {
                System.err.println("  ✗ No 'mount' method found in " + modName + " with a single String parameter. Ensure 'public static void mount(String gameJarPath)' exists.");
            } catch (java.lang.reflect.InvocationTargetException e) {
                System.err.println("  ✗ Mod '" + modName + "' threw an exception during mount: " + e.getTargetException().getMessage());
                e.getTargetException().printStackTrace();
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