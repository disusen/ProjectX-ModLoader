package com.spiralknights.autoadvance;

import com.spiralknights.modpatcher.ModPatcher;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor; // Import Descriptor for utility methods

import java.io.IOException;
import java.lang.reflect.Modifier;

/**
 * AutoAdvance mod rewritten for direct Javassist integration with ModPatcher.
 * @author Leego Yih (original), Gvidas (refactor for ModPatcher)
 */
public class Main implements ModPatcher.ModTransformer {

    private static boolean mounted = false;

    private static final String TARGET_CLASS_Q = "com.threerings.projectx.dungeon.client.Q";
    private static final String TARGET_CLASS_ENDOFLEVELWINDOW = "com.threerings.projectx.dungeon.client.EndOfLevelWindow";

    public static void mount(String gameJarPath) {
        if (mounted) {
            return;
        }
        mounted = true;
        ModPatcher.registerTransformer("AutoAdvanceMod", new Main());
        System.out.println("[AutoAdvanceMod] Registered with ModPatcher.");
    }

    public static void mount() {
        mount(null);
    }

    @Override
    public boolean shouldTransform(String className) {
        // Add logging here to see what classes are being queried
        if (className.equals(TARGET_CLASS_Q) || className.equals(TARGET_CLASS_ENDOFLEVELWINDOW)) {
            System.out.println("[AutoAdvanceMod][shouldTransform] Asked about: " + className + " -> YES");
            return true;
        }
        // Uncomment the line below temporarily to see ALL classes the agent queries
        System.out.println("[AutoAdvanceMod][shouldTransform] Asked about: " + className + " -> NO");
        return false;
    }

    @Override
    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
        System.out.println("[AutoAdvanceMod][transform] Attempting to transform: " + className); // Crucial logging

        ClassPool pool = ClassPool.getDefault();
        // The LoaderClassPath line is good, keep it.
        pool.appendClassPath(new javassist.LoaderClassPath(this.getClass().getClassLoader()));

        CtClass ctClass = null;
        try {
            // Using makeClass from InputStream is correct when you have the raw bytes
            ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));

            if (className.equals(TARGET_CLASS_Q)) {
                System.out.println("[AutoAdvanceMod] Applying transformations to " + TARGET_CLASS_Q);

                // Field addition
                try {
                    CtField newField = new CtField(pool.get("java.lang.Float"), "_variableElapsed", ctClass);
                    newField.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
                    ctClass.addField(newField);
                    System.out.println("[AutoAdvanceMod] Added field _variableElapsed to " + TARGET_CLASS_Q);
                } catch (CannotCompileException e) {
                    // This might happen if the field already exists from a previous run or a different mod
                    System.err.println("[AutoAdvanceMod] Warning: Could not add field _variableElapsed (might already exist): " + e.getMessage());
                }

                // Constructor modification
                CtClass[] paramTypes = {
                        pool.get("com.threerings.projectx.util.A"),
                        pool.get("com.threerings.projectx.client.er"),
                        pool.get("com.threerings.projectx.dungeon.client.EndOfLevelWindow")
                };
                String constructorDescriptor = Descriptor.ofMethod(pool.get("void"), paramTypes);
                System.out.println("[AutoAdvanceMod] Looking for constructor with descriptor: " + constructorDescriptor);
                CtConstructor constructorQ = ctClass.getConstructor(constructorDescriptor);
                System.out.println("[AutoAdvanceMod] Found constructor: " + constructorQ.getSignature());

                constructorQ.insertAfter("" +
                        "this._elapsed = 100F;\n" +
                        ctClass.getName() + ".class.getDeclaredField(\"_variableElapsed\").set(null, Float.valueOf(100F));\n"
                );
                System.out.println("[AutoAdvanceMod] Modified constructor of " + TARGET_CLASS_Q);

                // Method modification
                CtMethod tickMethodQ = ctClass.getMethod("tick", "(F)V");
                tickMethodQ.insertBefore("$1 += 1000.0F;");
                System.out.println("[AutoAdvanceMod] Modified tick method of " + TARGET_CLASS_Q);

            } else if (className.equals(TARGET_CLASS_ENDOFLEVELWINDOW)) {
                System.out.println("[AutoAdvanceMod] Applying transformations to " + TARGET_CLASS_ENDOFLEVELWINDOW);
                CtMethod tickMethodEOL = ctClass.getMethod("tick", "(F)V");
                tickMethodEOL.insertBefore("$1 += 1000.0F;");
                System.out.println("[AutoAdvanceMod] Modified tick method of " + TARGET_CLASS_ENDOFLEVELWINDOW);
            }

            // This is the crucial step: return the modified bytecode!
            byte[] modifiedBytes = ctClass.toBytecode();
            System.out.println("[AutoAdvanceMod][transform] Successfully transformed " + className + ". Returning new bytecode.");
            return modifiedBytes;

        } catch (NotFoundException | CannotCompileException | IOException e) {
            System.err.println("[AutoAdvanceMod] Error transforming " + className + ": " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to indicate transformation failure, ModPatcher might log it.
        } finally {
            if (ctClass != null) {
                // Detach to release memory, but only *after* toBytecode()
                ctClass.detach();
            }
        }
    }

    @lombok.Data
    public static class Autoadv {
        private boolean enabled;
    }

    public static void main(String[] args) {
        // Not used
    }
}