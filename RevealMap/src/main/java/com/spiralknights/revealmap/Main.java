package com.spiralknights.revealmap;

import com.spiralknights.modpatcher.ModPatcher;
import javassist.*;
import javassist.bytecode.AccessFlag; // Import for AccessFlag

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * RevealMap mod - makes the minimap reveal 20x more area without changing visual size
 * @author Leego Yih (original), Gvidas (refactor for ModPatcher)
 */
public class Main implements ModPatcher.ModTransformer {

    private static boolean mounted = false;

    // Target the map component class for method replacement
    private static final String TARGET_MAP_CLASS = "com.threerings.projectx.map.client.a";
    // Target the scene class for field modification
    private static final String TARGET_SCENE_CLASS = "com.threerings.whirled.a.d";
    // New target: Dungeon client class for method modification
    private static final String TARGET_DUNGEON_CLASS = "com.threerings.projectx.dungeon.client.C";


    // This static field will store the last known scene ID for your mod's logic.
    // It's static because it needs to persist across different instances of map.client.a
    // and across calls to the method.
    public static int lastKnownSceneId = -1;

    // Track when we need to do a delayed big reveal
    public static int pendingBigRevealSceneId = -1;
    public static long pendingBigRevealTime = 0;
    public static int debugCallCount = 0;

    public static void mount(String gameJarPath) {
        if (mounted) {
            return;
        }
        mounted = true;
        ModPatcher.registerTransformer("RevealMapMod", new Main());
        System.out.println("[RevealMapMod] Registered with ModPatcher.");
    }

    public static void mount() {
        mount(null);
    }

    @Override
    public boolean shouldTransform(String className) {
        // We need to transform the map component class, the scene class, and the dungeon client class
        return className.equals(TARGET_MAP_CLASS) ||
                className.equals(TARGET_SCENE_CLASS) ||
                className.equals(TARGET_DUNGEON_CLASS);
    }

    @Override
    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
        System.out.println("[RevealMapMod][transform] Attempting to transform: " + className);

        ClassPool pool = ClassPool.getDefault();
        // Add the current class loader to the ClassPool to ensure all dependencies are found
        pool.appendClassPath(new javassist.LoaderClassPath(this.getClass().getClassLoader()));

        CtClass ctClass = null;
        try {
            // Create a CtClass from the incoming bytecode buffer
            ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

            if (className.equals(TARGET_SCENE_CLASS)) {
                // --- Transformation for com.threerings.whirled.a.d (Scene class) ---
                System.out.println("[RevealMapMod] Applying field modification to " + TARGET_SCENE_CLASS);
                try {
                    // Get the CtField for _sceneId
                    CtField sceneIdField = ctClass.getDeclaredField("_sceneId");

                    // Ensure it's public. We use AccessFlag for direct bytecode manipulation
                    // to set the public flag and keep other flags (like protected/private) if they exist.
                    // This properly handles clearing conflicting access modifiers
                    sceneIdField.setModifiers(Modifier.PUBLIC | (sceneIdField.getModifiers() & ~Modifier.PRIVATE & ~Modifier.PROTECTED));
                    System.out.println("Field _sceneId in " + TARGET_SCENE_CLASS + " is now public.");

                } catch (NotFoundException e) {
                    System.err.println("[RevealMapMod] Field _sceneId not found in " + TARGET_SCENE_CLASS + ": " + e.getMessage());
                    e.printStackTrace();
                }

            } else if (className.equals(TARGET_MAP_CLASS)) {
                // --- Transformation for com.threerings.projectx.map.client.a (Map component class) ---
                System.out.println("[RevealMapMod] Applying method replacement to " + TARGET_MAP_CLASS);

                try {
                    // Target the c(Coord) method that returns visibility for each coordinate
                    CtMethod cMethod = ctClass.getDeclaredMethod("c", new CtClass[]{pool.get("com.threerings.tudey.util.Coord")});

                    if (cMethod != null) {
                        String newMethodBody = "{ " +
                                "float result = 1.0F; " +

                                // Log every 50th call to avoid spam
                                "com.spiralknights.revealmap.Main.debugCallCount++; " +
                                "if (com.spiralknights.revealmap.Main.debugCallCount % 50 == 0) { " +
                                "    System.out.println(\"[RevealMapMod] c(Coord) returning \" + result + \" for (\" + $1.x + \", \" + $1.y + \")\"); " +

                                // Get stack trace to see WHO is calling this method
                                "    try { " +
                                "        StackTraceElement[] stack = Thread.currentThread().getStackTrace(); " +
                                "        if (stack.length > 2) { " +
                                "            System.out.println(\"[RevealMapMod] Called from: \" + stack[2].getClassName() + \".\" + stack[2].getMethodName()); " +
                                "        } " +
                                "    } catch (Exception e) {} " +
                                "} " +

                                "return result; " +
                                "}";

                        cMethod.setBody(newMethodBody);
                        System.out.println("[RevealMapMod] Replaced c(Coord) method with dynamic visibility logic.");
                    }
                } catch (NotFoundException e) {
                    System.err.println("[RevealMapMod] Error finding c(Coord) method in " + TARGET_MAP_CLASS + ": " + e.getMessage());
                }
                System.out.println("[RevealMapMod] Reveal area modifications complete for " + TARGET_MAP_CLASS + "!");
            } else if (className.equals(TARGET_DUNGEON_CLASS)) {
                // --- Transformation for com.threerings.projectx.dungeon.client.C (Dungeon client class) ---
                System.out.println("[RevealMapMod] Applying method modification to " + TARGET_DUNGEON_CLASS);

                try {
                    // Target the c(Coord) method
                    CtMethod cMethod = ctClass.getDeclaredMethod("c", new CtClass[]{pool.get("com.threerings.tudey.util.Coord")});

                    if (cMethod != null) {
                        // The method body to always return super.c((Coord)$1)
                        String newMethodBody = "{ " +
                                "return super.c((com.threerings.tudey.util.Coord)$1); " +
                                "}";

                        cMethod.setBody(newMethodBody);
                        System.out.println("[RevealMapMod] Replaced c(Coord) method in " + TARGET_DUNGEON_CLASS + " to always return super.c((Coord)$1).");
                    }
                } catch (NotFoundException e) {
                    System.err.println("[RevealMapMod] Error finding c(Coord) method in " + TARGET_DUNGEON_CLASS + ": " + e.getMessage());
                }
                System.out.println("[RevealMapMod] Method modification complete for " + TARGET_DUNGEON_CLASS + "!");
            }

            byte[] modifiedBytes = ctClass.toBytecode();
            System.out.println("[RevealMapMod][transform] Successfully transformed " + className);
            return modifiedBytes;

        } catch (CannotCompileException | IOException e) {
            System.err.println("[RevealMapMod] Error transforming " + className + ": " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            if (ctClass != null) {
                ctClass.detach();
            }
        }
    }

    public static void main(String[] args) {
        // Not used
    }
}
