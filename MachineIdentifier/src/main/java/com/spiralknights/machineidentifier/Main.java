package com.spiralknights.machineidentifier;

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
            System.out.println("[MachineIdentifierMod] Already mounted, skipping.");
            return;
        }
        mounted = true;
        System.out.println("[MachineIdentifierMod] Mounting Machine Identifier Mod...");

        // Get machine identifier value from config
        String identifierValue = getMachineIdentifier();

        // Register our ModTransformer with ModPatcher
        ModPatcher.registerTransformer(
                "MachineIdentifierMod",
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // This mod only wants to transform the obfuscated IdentUtil class
                        return className.equals("com.threerings.util.G"); // com.threerings.util.IdentUtil
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[MachineIdentifierMod] Transforming " + className);

                        // Create a Javassist ClassPool for this transformation (ModPatcher provides appendClassPath)
                        ClassPool pool = ClassPool.getDefault(); // ModPatcher ensures this pool has access to game JARs

                        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
                        // Ensure the class is not frozen if it was loaded earlier
                        ctClass.defrost();

                        // Find the getMachineIdentifier method (obfuscated QK)
                        // The obfuscated methods/classes should be looked up dynamically if possible,
                        // or kept track of as part of your mod's knowledge.
                        CtMethod getMachineIdentifierMethod = null;
                        try {
                            getMachineIdentifierMethod = ctClass.getDeclaredMethod("OY"); // getMachineIdentifier
                        } catch (NotFoundException e) {
                            System.err.println("[MachineIdentifierMod] Method 'OY' not found in 'com.threerings.util.G'. Game update? " + e.getMessage());
                            return null; // Return original bytecode
                        }

                        // The new method body - updated with correct obfuscated method names
                        String newBody = "{\n" +
                                "    String appendIdent = \"" + identifierValue + "\";\n" +
                                "    try {\n" +
                                "        String[] macArray = com.samskivert.b.c.fa();\n" + // Updated: c.fj() -> c.fa()
                                "        String macStr = com.samskivert.util.ar.a(macArray) + \",\" + appendIdent;\n" + // Updated: aq.a -> ar.a
                                "        String ident = com.samskivert.util.ar.ai(macStr);\n" + // Updated: aq.af -> ar.ai
                                "        StringBuilder encoded = new StringBuilder();\n" +
                                "        byte[] identArray = com.threerings.util.G.dy(ident);\n" + // Updated: dD -> dy
                                "        byte[] checksum = com.threerings.util.G.j(identArray);\n" + // Original code's usage
                                "        for (int ii = 0; ii < identArray.length; ++ii) {\n" +
                                "            byte value = (byte) ((identArray[ii] << 2) + checksum[ii]);\n" +
                                "            value = (byte) (value * 29 % 64);\n" +
                                "            encoded.append((char) com.threerings.util.G.bdT[value]);\n" + // Updated: bek -> bdT
                                "        }\n" +
                                "        return encoded.toString();\n" +
                                "    } catch (Exception var5) {\n" +
                                "        com.threerings.b.log.f(\"Failed creating ident.\", new java.lang.Object[]{var5});\n" +
                                "        return \"ZHNmYWtsd2VxZmprbGpvZGxrc2pmb2lq\";\n" +
                                "    }\n" +
                                "}";

                        getMachineIdentifierMethod.setBody(newBody);
                        System.out.println("[MachineIdentifierMod] Successfully set new body for 'OY' in 'com.threerings.util.G'.");

                        return ctClass.toBytecode(); // Return the modified bytecode
                    }
                }
        );
        System.out.println("[MachineIdentifierMod] Mod mounted successfully.");
    }

    // Helper to read identifier from a properties file
    static String getMachineIdentifier() {
        String identifier = "4aFDE453789123EEFF"; // Default value
        File configFile = new File(ModPatcher.CURRENT_DIR + "mods/" + "machineidentifier.properties"); // Config in main SK dir

        if (configFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                identifier = props.getProperty("identifier", identifier);
                System.out.println("[MachineIdentifierMod] Loaded identifier from config: " + identifier);
            } catch (IOException e) {
                System.err.println("[MachineIdentifierMod] Failed to read config file: " + e.getMessage());
            }
        } else {
            System.out.println("[MachineIdentifierMod] No machineidentifier.properties found, using default: " + identifier);
        }
        return identifier;
    }

    // This is just a placeholder to satisfy the class structure, won't be called directly by ModPatcher
    public static void main(String[] args) {
        System.out.println("This is the MachineIdentifier mod. Run via ModPatcher.");
    }
}