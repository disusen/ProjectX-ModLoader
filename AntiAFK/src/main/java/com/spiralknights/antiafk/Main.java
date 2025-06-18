package com.spiralknights.antiafk;

import com.spiralknights.modpatcher.ModPatcher; // IMPORTANT: Import your ModPatcher
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import lombok.AllArgsConstructor; // If you still use Lombok
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

/**
 * @author disusen
 */
public class Main {

    private static boolean mounted = false; // Prevents double-mounting

    // This method will be called by ModPatcher when it loads this mod JAR
    public static void mount(String gameJarPath) {
        if (mounted) {
            System.out.println("[AntiAFKMod] Already mounted, skipping.");
            return;
        }
        mounted = true;
        System.out.println("[AntiAFKMod] Mounting AntiAfk Mod...");


        // Register our ModTransformer with ModPatcher
        ModPatcher.registerTransformer(
                "AntiAFKMod",
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // This mod only deletes all content of a method found in this class
                        return className.equals("com.threerings.projectx.client.ProjectXApp$c");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[AntiAFKMod] Transforming " + className);

                        // Create a Javassist ClassPool for this transformation (ModPatcher provides appendClassPath)
                        ClassPool pool = ClassPool.getDefault(); // ModPatcher ensures this pool has access to game JARs

                        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
                        // Ensure the class is not frozen if it was loaded earlier
                        ctClass.defrost();

                        // Find the start method (obfuscated QK)
                        // The obfuscated methods/classes should be looked up dynamically if possible,
                        // or kept track of as part of your mod's knowledge.
                        CtMethod getStart = null;
                        try {
                            getStart = ctClass.getDeclaredMethod("start"); // getMachineIdentifier
                        } catch (NotFoundException e) {
                            System.err.println("[AntiAFK] Method 'start' not found in 'com.threerings.projectx.client.ProjectXApp$c'. Game update? " + e.getMessage());
                            return null; // Return original bytecode
                        }

                        // The new method body - updated with correct obfuscated method names
                        String newBody = "{ System.out.println(\"never idle\"); }";

                        getStart.setBody(newBody);
                        System.out.println("[AntiAFK] Successfully set new body for 'start' in 'com.threerings.projectx.client.ProjectXApp$c'.");

                        return ctClass.toBytecode(); // Return the modified bytecode
                    }
                }
        );
        System.out.println("[AntiAFK] Mod mounted successfully.");
    }

    // This is just a placeholder to satisfy the class structure, won't be called directly by ModPatcher
    public static void main(String[] args) {
        System.out.println("This is the AntiAFK mod. Run via ModPatcher.");
    }
}