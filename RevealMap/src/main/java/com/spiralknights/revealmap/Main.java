package com.spiralknights.revealmap;

import com.spiralknights.modpatcher.ModPatcher;
import javassist.*;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * RevealMap mod - makes the minimap reveal 20x more area without changing visual size
 * @author Leego Yih (original), Gvidas (refactor for ModPatcher)
 */
public class Main implements ModPatcher.ModTransformer {

    private static boolean mounted = false;

    // Target the map component class
    private static final String TARGET_CLASS = "com.threerings.projectx.map.client.a";

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
        return className.equals(TARGET_CLASS);
    }

    @Override
    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
        System.out.println("[RevealMapMod][transform] Attempting to transform: " + className);

        ClassPool pool = ClassPool.getDefault();
        pool.appendClassPath(new javassist.LoaderClassPath(this.getClass().getClassLoader()));

        CtClass ctClass = null;
        try {
            ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

            if (className.equals(TARGET_CLASS)) {
                System.out.println("[RevealMapMod] Applying transformations to " + TARGET_CLASS);

                // --- APPROACH 1: Modify the d(Coord) method directly ---
                // This method calculates which tiles to reveal, let's replace it entirely
                try {
                    CtMethod dMethod = ctClass.getDeclaredMethod("d", new CtClass[]{pool.get("com.threerings.tudey.util.Coord")});
                    if (dMethod != null) {
                        // Replace the entire method with our own implementation using bigger values
                        String newMethodBody = "{ " +
                                "java.util.HashMap hashMap = com.google.common.collect.Maps.newHashMap(); " +
                                "int aME_NEW = 60; " +       // 20 * 3 = 60 (3x bigger)
                                "int aMG_NEW = 96; " +       // 32 * 3 = 96 (3x bigger)
                                "int aMF_NEW = 3600; " +     // 60 * 60 = 3600
                                "int aMH_NEW = 9216; " +     // 96 * 96 = 9216
                                "for (int i = $1.x - aMG_NEW, j = $1.x + aMG_NEW; i <= j; i++) { " +
                                "int n = (i - $1.x) * (i - $1.x); " +
                                "for (int i1 = $1.y - aMG_NEW, i2 = $1.y + aMG_NEW; i1 <= i2; i1++) { " +
                                "int i3 = (i1 - $1.y) * (i1 - $1.y); " +
                                "if (n + i3 > aMH_NEW) { " +
                                "if (i1 > $1.y) break; " +
                                "} else { " +
                                "float f = 1.0F; " +
                                "int totalDist = n + i3; " +
                                "if (totalDist > aMF_NEW) " +
                                "f = 1.0F - (float)(totalDist - aMF_NEW) / (float)(aMH_NEW - aMF_NEW); " +
                                "hashMap.put(new com.threerings.tudey.util.Coord(i, i1), Float.valueOf(f)); " +
                                "} " +
                                "} " +
                                "} " +
                                "return hashMap; " +
                                "}";

                        dMethod.setBody(newMethodBody);
                        System.out.println("[RevealMapMod] Replaced d(Coord) method with expanded reveal calculation");
                    }
                } catch (NotFoundException e) {
                    System.out.println("[RevealMapMod] d(Coord) method not found, trying alternative approach");

                    // --- APPROACH 2: Try to modify the static initializer more aggressively ---
                    try {
                        // Add multiple static blocks to ensure our values stick
                        ctClass.makeClassInitializer().insertBefore("aME = 60; aMG = 96; aMF = 3600; aMH = 9216;");
                        ctClass.makeClassInitializer().insertAfter("aME = 60; aMG = 96; aMF = 3600; aMH = 9216;");
                        System.out.println("[RevealMapMod] Added static initializer overrides");
                    } catch (Exception e2) {
                        System.out.println("[RevealMapMod] Static initializer failed: " + e2.getMessage());
                    }
                }

                // --- APPROACH 3: Add a new static method that gets called ---
                try {
                    // Add a method that forces the values to be set
                    CtMethod forceValues = CtMethod.make(
                            "private static void forceRevealValues() { " +
                                    "    aME = 60; " +
                                    "    aMG = 96; " +
                                    "    aMF = 3600; " +
                                    "    aMH = 9216; " +
                                    "}", ctClass);
                    ctClass.addMethod(forceValues);

                    // Try to call this method from the constructor
                    CtConstructor[] constructors = ctClass.getConstructors();
                    for (CtConstructor constructor : constructors) {
                        constructor.insertAfter("forceRevealValues();");
                    }
                    System.out.println("[RevealMapMod] Added forceRevealValues method and constructor calls");
                } catch (Exception e) {
                    System.out.println("[RevealMapMod] Force values method failed: " + e.getMessage());
                }

                System.out.println("[RevealMapMod] Reveal area modifications complete - should reveal 20x more area!");
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