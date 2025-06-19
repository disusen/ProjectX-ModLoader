package com.spiralknights.pocketshop;

import com.spiralknights.modpatcher.ModPatcher;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;

/**
 * PocketShop Mod - Adds auction command to chat director
 * @author disusen
 */
public class Main {

    private static boolean mounted = false; // Prevents double-mounting

    // This method will be called by ModPatcher when it loads this mod JAR
    public static void mount(String gameJarPath) {
        if (mounted) {
            System.out.println("[PocketShop] Already mounted, skipping.");
            return;
        }
        mounted = true;
        System.out.println("[PocketShop] Mounting PocketShop Mod...");

        // Register our ModTransformer with ModPatcher
        ModPatcher.registerTransformer(
                "PocketShop",
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // Target the chat director class
                        return className.equals("com.threerings.crowd.chat.client.a");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[PocketShop] Transforming " + className);

                        // Create a Javassist ClassPool for this transformation
                        ClassPool pool = ClassPool.getDefault();

                        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
                        // Ensure the class is not frozen if it was loaded earlier
                        ctClass.defrost();

                        // Find the chat director method
                        CtMethod chatMethod = null;
                        try {
                            // Get the method with the exact signature
                            chatMethod = ctClass.getDeclaredMethod("a", new CtClass[]{
                                    pool.get("com.threerings.crowd.chat.client.m"),
                                    pool.get("java.lang.String"),
                                    CtClass.booleanType
                            });
                        } catch (NotFoundException e) {
                            System.err.println("[PocketShop] Method 'a' not found in chat client. Game update? " + e.getMessage());
                            return null; // Return original bytecode
                        }

                        // Insert our auction command logic at the beginning of the method
                        String insertCode =
                                "if ($2 != null && $2.toLowerCase().equals(\"/loler\")) {" +
                                        "    try {" +
                                        "        System.out.println(\"[PocketShop] Loler command detected!\");" +
                                        "        com.threerings.projectx.auction.data.AuctionDialogInfo di = new com.threerings.projectx.auction.data.AuctionDialogInfo();" +
                                        "        com.threerings.projectx.util.A ctx__ = (com.threerings.projectx.util.A) this._ctx;" +
                                        "        com.threerings.projectx.client.aD hud__ = com.threerings.projectx.client.aD.h(ctx__);" +
                                        "        ctx__.getRoot().addWindow(di.a(ctx__, hud__.ui()));" +
                                        "        return \"success\";" +
                                        "    } catch (Exception e) {" +
                                        "        System.err.println(\"[PocketShop] Error opening auction dialog: \" + e.getMessage());" +
                                        "        e.printStackTrace();" +
                                        "        return \"Error opening auction dialog\";" +
                                        "    }" +
                                        "}";

                        chatMethod.insertBefore(insertCode);
                        System.out.println("[PocketShop] Successfully added auction command to chat director.");

                        return ctClass.toBytecode(); // Return the modified bytecode
                    }
                }
        );
        System.out.println("[PocketShop] Mod mounted successfully.");
    }

    // This is just a placeholder to satisfy the class structure, won't be called directly by ModPatcher
    public static void main(String[] args) {
        System.out.println("This is the PocketShop mod. Run via ModPatcher.");
    }
}