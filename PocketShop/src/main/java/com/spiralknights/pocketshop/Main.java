package com.spiralknights.pocketshop;

import com.spiralknights.modpatcher.ChatDirectorManager;
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

        String command = "/loler";
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

        ChatDirectorManager.registerChatCommand(command, insertCode, "PocketShop");
        System.out.println("[PocketShop] Mod mounted successfully.");
    }

    // This is just a placeholder to satisfy the class structure, won't be called directly by ModPatcher
    public static void main(String[] args) {
        System.out.println("This is the PocketShop mod. Run via ModPatcher.");
    }
}