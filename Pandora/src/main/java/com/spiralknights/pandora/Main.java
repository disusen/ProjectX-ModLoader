package com.spiralknights.pandora;

import com.spiralknights.modpatcher.ModPatcher;
import javassist.*;

import java.io.ByteArrayInputStream; // Import ByteArrayInputStream

/**
 * Pandora Mod - Adds preview stuff
 * @author disusen
 */
public class Main {

    private static boolean mounted = false; // Prevents double-mounting

    // This method will be called by ModPatcher when it loads this mod JAR
    public static void mount(String gameJarPath) {
        if (mounted) {
            System.out.println("[Pandora] Already mounted, skipping.");
            return;
        }
        mounted = true;
        System.out.println("[Pandora] Mounting Pandora Mod...");

        // Step 1: Create new classes that don't exist in the original game JAR
        // This should be done before registering transformers for existing classes
        try {
            addPreviewApplyListenerClass();
            System.out.println("[Pandora] Created PreviewApplyListener class.");
        } catch (Exception e) {
            System.err.println("[Pandora] Error creating PreviewApplyListener class: " + e.getMessage());
            e.printStackTrace();
            // Decide how to handle this error:
            // return; // Abort mounting if this class is critical
        }

        ModPatcher.registerTransformer(
                "redefineKnightSpriteToReplacePreviewItems", // Another unique name for this transformer
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // Target the KnightSprite class for modification
                        return className.equals("com.threerings.projectx.client.sprite.KnightSprite");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[redefineKnightSpriteToReplacePreviewItems] Transforming " + className);

                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        ctClass.defrost();

                        // Add the new field
                        try {
                            CtField configPreviewField = new CtField(pool.get("com.threerings.config.ConfigReference"), "_configPreview", ctClass);
                            configPreviewField.setModifiers(Modifier.PUBLIC | Modifier.TRANSIENT | Modifier.VOLATILE);
                            ctClass.addField(configPreviewField);
                            System.out.println("[redefineKnightSpriteToReplacePreviewItems] Added _configPreview field to KnightSprite.");
                        } catch (Exception e) {
                            System.err.println("[redefineKnightSpriteToReplacePreviewItems] Error adding _configPreview field: " + e.getMessage());
                            e.printStackTrace();
                            // Decide if you want to proceed without this field or abort
                        }

                        // Modify the existing method 'c'
                        try {
                            CtMethod cMethod = ctClass.getDeclaredMethod("c", new CtClass[]{pool.get("com.threerings.tudey.data.actor.Actor")});
                            String insertCode =
                                    "if (this._configPreview != null) {\n" +
                                            "    if (!this._configPreview.equals(this._actor.ES())) {\n" +
                                            "        this._configPreview.copy(this._actor.ES());\n" +
                                            "    }\n" +
                                            "}";
                            cMethod.insertBefore(insertCode);
                            System.out.println("[redefineKnightSpriteToReplacePreviewItems] Modified method 'c' in KnightSprite.");
                        } catch (NotFoundException e) {
                            System.err.println("[redefineKnightSpriteToReplacePreviewItems] Method 'c' not found in KnightSprite. Game update? " + e.getMessage());
                            // This might be a critical error, so you might want to re-throw or handle accordingly
                        } catch (Exception e) {
                            System.err.println("[redefineKnightSpriteToReplacePreviewItems] Error modifying method 'c' in KnightSprite: " + e.getMessage());
                            e.printStackTrace();
                        }

                        return ctClass.toBytecode();
                    }
                }
        );

        ModPatcher.registerTransformer(
                "redefineGoodSlotToIgnoreException", // The unique name for this transformer
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // Target the GoodSlot class for modification
                        return className.equals("com.threerings.projectx.shop.client.GoodSlot");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[redefineGoodSlotToIgnoreException] Transforming " + className);

                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        ctClass.defrost(); // Always defrost if you're modifying a loaded class

                        // Find the method named "Jb"
                        try {
                            CtMethod kcMethod = ctClass.getDeclaredMethod("Jb"); // No parameters needed if it has none
                            String newMethodBody =
                                    "{\n" +
                                            "    boolean var1 = false;\n" +
                                            "    boolean var2 = false;\n" +
                                            "    com.threerings.projectx.util.A ctx = $0._ctx;\n" +
                                            "    com.threerings.projectx.shop.config.GoodConfig aQL = $0.aQL;\n" +
                                            "    com.threerings.projectx.shop.util.ItemPreparer aQN = $0.aQN;\n" +
                                            "    if (aQL != null) {\n" +
                                            "        try {\n" +
                                            "            com.threerings.config.ConfigReference var3 = aQN.prepareItem(ctx, aQL);\n" +
                                            "            com.threerings.projectx.item.config.ItemConfig.Original original = com.threerings.projectx.item.data.Item.i(ctx.getConfigManager(), var3);\n" +
                                            "            com.threerings.projectx.item.data.Item var4 = original.b((com.threerings.projectx.item.b.a) ctx, var3);\n" +
                                            "            var1 = var4.isLocked();\n" +
                                            "            var2 = var4 instanceof com.threerings.projectx.item.data.LevelItem;\n" +
                                            "        } catch (Throwable e) {\n" + // Catches any exception (checked or unchecked)
                                            "            // System.err.println(\"[GoodSlot.Jb] Ignored exception: \" + e.getMessage()); // Optional: for debugging\n" +
                                            "        }\n" +
                                            "    }\n" +
                                            "    com.threerings.opengl.gui.p var5 = $0.getComponent(\"bound\");\n" +
                                            "    var5.setEnabled(var1);\n" +
                                            "    var5.setVisible(var1 || var2);\n" +
                                            "}";

                            kcMethod.setBody(newMethodBody);
                            System.out.println("[redefineGoodSlotToIgnoreException] Modified method 'Jb' in " + className + " to ignore exceptions.");

                        } catch (NotFoundException e) {
                            System.err.println("[redefineGoodSlotToIgnoreException] Method 'Jb' not found in " + className + ". Game update? " + e.getMessage());
                            // Depending on criticality, you might want to re-throw this as a RuntimeException
                            // throw new RuntimeException("Failed to find Jb method in GoodSlot", e);
                        } catch (Exception e) {
                            System.err.println("[redefineGoodSlotToIgnoreException] Error modifying method 'Jb' in " + className + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        // Always return the modified bytecode
                        return ctClass.toBytecode();
                    }
                }
        );

        ModPatcher.registerTransformer(
                "redefineVendorListPanelToIgnoreException", // The unique name for this transformer
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // Target the GoodSlot class for modification
                        return className.equals("com.threerings.projectx.shop.client.u");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[redefineVendorListPanelToIgnoreException] Transforming " + className);

                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        ctClass.defrost(); // Always defrost if you're modifying a loaded class

                        // Find the method named "j"
                        try {
                            CtMethod kcMethod = ctClass.getDeclaredMethod("j"); // No parameters needed if it has none
                            String newMethodBody =
                                    "{\n" +
                                            "    java.lang.reflect.Field configMapField = com.threerings.projectx.shop.client.e.class.getDeclaredField(\"aQD\");\n" +
                                            "    configMapField.setAccessible(true);\n" +
                                            "    java.util.Map configMap = configMapField.get((com.threerings.projectx.shop.client.e) $0);\n" +
                                            "    com.threerings.projectx.shop.data.UniqueShopInfo usi = $0.aQC.Jc();\n" +
                                            "    int i = 0;\n" +
                                            "    int size = usi.goodCounts.size();\n" +
                                            "    for (; i < size; ++i) {\n" +
                                            "        com.threerings.projectx.shop.data.UniqueShopInfo.GoodCount goodCount = (com.threerings.projectx.shop.data.UniqueShopInfo.GoodCount) usi.goodCounts.get(i);\n" +
                                            "        try {\n" +
                                            "            com.threerings.config.ConfigReference itemConfigRef = goodCount.good.EE();\n" +
                                            "            com.threerings.projectx.item.config.ItemConfig.Original itemConfigOriginal = com.threerings.projectx.item.data.Item.i($0._ctx.getConfigManager(), itemConfigRef);\n" +
                                            "            com.threerings.projectx.client.d.c groupInfo = (com.threerings.projectx.client.d.c) $0.aFc.get(itemConfigOriginal.Az());\n" +
                                            "            if (groupInfo != null && $0.bW(itemConfigOriginal.a((com.threerings.projectx.util.A) $0._ctx, itemConfigRef))) {\n" +
                                            "                Object goodInfo = configMap.get(goodCount.good);\n" +
                                            "                if (goodInfo == null) {\n" +
                                            "                    goodInfo = new com.threerings.projectx.shop.client.e.a($0,goodCount.good, goodCount.count, i);\n" +
                                            "                    configMap.put(goodCount.good, goodInfo);\n" +
                                            "                }\n" +
                                            "                groupInfo.abX.put(Integer.valueOf(i), goodInfo);\n" +
                                            "            } else {\n" +
                                            "                groupInfo.abX.remove(Integer.valueOf(i));\n" +
                                            "            }\n" +
                                            "        } catch (Exception e) {\n" +
                                            "            System.out.println(\"[VendorListPanel] Failed to add item to pandora \" + goodCount.good.toString() + \"\\n\" + e.getMessage());\n" +
                                            "        }\n" +
                                            "    }\n" +
                                            "}";

                            kcMethod.setBody(newMethodBody);
                            System.out.println("[redefineVendorListPanelToIgnoreException] Modified method 'j' in " + className + " to ignore exceptions.");

                        } catch (NotFoundException e) {
                            System.err.println("[redefineVendorListPanelToIgnoreException] Method 'j' not found in " + className + ". Game update? " + e.getMessage());
                        } catch (Exception e) {
                            System.err.println("[redefineVendorListPanelToIgnoreException] Error modifying method 'j' in " + className + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        // Always return the modified bytecode
                        return ctClass.toBytecode();
                    }
                }
        );

        ModPatcher.registerTransformer(
                "redefineShopDialogToCacheAndRenameTitle", // The unique name for this transformer
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // Target the GoodSlot class for modification
                        return className.equals("com.threerings.projectx.shop.client.l");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[redefineShopDialogToCacheAndRenameTitle] Transforming " + className);

                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        ctClass.defrost(); // Always defrost if you're modifying a loaded class

                        // Find the method named "Jb"
                        try {
                            CtMethod kcMethod = ctClass.getDeclaredMethod("Ao"); // No parameters needed if it has none
                            String newMethodBody =
                                    "{\n" +
                                            "    if (this.aQP.name == null || !this.aQP.name.toLowerCase().contains(\"pandora\")) {\n" +
                                            "        this.aum = new com.threerings.projectx.shop.client.p(this, this._ctx, false);\n" +
                                            "        return;\n" +
                                            "    }\n" +
                                            "    if (this.aum != null) {\n" +
                                            "        System.out.println(\"[Pandora] Use Cached VendorPanel\");\n" +
                                            "        this._ctx.getRoot().addWindow(this.aum, true);\n" +
                                            "        return;\n" +
                                            "    }\n" +
                                            "    System.out.println(\"[Pandora] Create New VendorPanel\");\n" +
                                            "    this.aum = new com.threerings.projectx.shop.client.p(this, this._ctx, false);\n" +
                                            "    com.threerings.projectx.shop.client.e acd = (com.threerings.projectx.shop.client.e) this.aum.sH();\n" +
                                            "    ((com.threerings.opengl.gui.Label) acd.getComponent(\"title\")).setText(this.aQP.title);\n" +
                                            "    com.threerings.opengl.gui.au btnPreview = ((com.threerings.opengl.gui.au) acd.getComponent(\"preview\"));\n" +
                                            "    java.lang.reflect.Method registerComponentMethod = com.threerings.opengl.gui.ax.class.getDeclaredMethod(\"registerComponent\",\n" +
                                            "            new Class[]{String.class, com.threerings.opengl.gui.p.class});\n" +
                                            "    registerComponentMethod.setAccessible(true);\n" +
                                            "    com.threerings.opengl.gui.au btnApply = new com.threerings.opengl.gui.au(this._ctx, \"Apply\");\n" +
                                            "    btnApply.setStyleConfigs(btnPreview.getStyleConfigs());\n" +
                                            "    btnApply.setSize(btnPreview.getWidth(), btnPreview.getHeight());\n" +
                                            "    registerComponentMethod.invoke(acd, new Object[]{\"apply\", btnApply});\n" +
                                            "    com.threerings.opengl.gui.event.a btnApplyListener =\n" +
                                            "            (com.threerings.opengl.gui.event.a) Class.forName(\"com.spiralstudio.mod.pandora.PreviewApplyListener\")\n" +
                                            "                    .getConstructors()[0].newInstance(new Object[]{this._ctx});\n" +
                                            "    btnApply.addListener$2eebd3b8(btnApplyListener);\n" +
                                            "    acd.add(btnApply, (Object) new Integer(1));\n" +
                                            "    System.out.println(\"[Pandora] Add Apply Button\");\n" +
                                            "}";

                            kcMethod.setBody(newMethodBody);
                            System.out.println("[redefineShopDialogToCacheAndRenameTitle] Modified method 'Ao' in " + className + " to ignore exceptions.");

                        } catch (NotFoundException e) {
                            System.err.println("[redefineShopDialogToCacheAndRenameTitle] Method 'Ao' not found in " + className + ". Game update? " + e.getMessage());
                        } catch (Exception e) {
                            System.err.println("[redefineShopDialogToCacheAndRenameTitle] Error modifying method 'Ao' in " + className + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        // Always return the modified bytecode
                        return ctClass.toBytecode();
                    }
                }
        );

        ModPatcher.registerTransformer(
                "redefinePlayerObjectToPreview", // The unique name for this transformer
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // Target the PlayerObject class for modification
                        return className.equals("com.threerings.projectx.data.PlayerObject");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[redefinePlayerObjectToPreview] Transforming " + className);

                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
                        ctClass.defrost(); // Always defrost if you're modifying a loaded class

                        // Add new fields: _equipmentPreview and _previewing
                        try {
                            // Add _equipmentPreview field
                            CtField equipmentPreviewField = new CtField(pool.get("com.threerings.projectx.item.data.LevelItem[]"), "_equipmentPreview", ctClass);
                            equipmentPreviewField.setModifiers(Modifier.PUBLIC | Modifier.TRANSIENT);
                            ctClass.addField(equipmentPreviewField);
                            System.out.println("[redefinePlayerObjectToPreview] Added field '_equipmentPreview'.");

                            // Add _previewing field
                            CtField previewingField = new CtField(CtClass.booleanType, "_previewing", ctClass);
                            previewingField.setModifiers(Modifier.PUBLIC | Modifier.TRANSIENT);
                            ctClass.addField(previewingField);
                            System.out.println("[redefinePlayerObjectToPreview] Added field '_previewing'.");

                        } catch (Exception e) {
                            System.err.println("[redefinePlayerObjectToPreview] Error adding fields to " + className + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        // Find and modify the method named "cl"
                        try {
                            // Ensure you get the correct method signature if it has parameters
                            // The example shows "cl(int)", so we specify the parameter type.
                            CtMethod cgMethod = ctClass.getDeclaredMethod("cl", new CtClass[]{CtClass.intType});

                            String newMethodBody =
                                    "{\n" +
                                            "    if (this._previewing && this._equipmentPreview != null) {\n" +
                                            "        com.threerings.projectx.item.data.LevelItem item = this._equipmentPreview[$1];\n" + // $1 refers to the first parameter (int)
                                            "        return item;\n" +
                                            "    }\n" +
                                            "    long id = this.equipment[$1];\n" +
                                            "    if (id == 0L) {\n" +
                                            "        return null;\n" +
                                            "    }\n" +
                                            "    com.threerings.projectx.item.data.Item item = (com.threerings.projectx.item.data.Item) this.items.f(Long.valueOf(id));\n" +
                                            "    if (item instanceof com.threerings.projectx.item.data.LevelItem) {\n" +
                                            "        return (com.threerings.projectx.item.data.LevelItem) item;\n" +
                                            "    }\n" +
                                            "    return null;\n" +
                                            "}";

                            cgMethod.setBody(newMethodBody);
                            System.out.println("[redefinePlayerObjectToPreview] Modified method 'cl(int)' in " + className + " for preview functionality.");

                        } catch (NotFoundException e) {
                            System.err.println("[redefinePlayerObjectToPreview] Method 'cl(int)' not found in " + className + ". Game update? " + e.getMessage());
                        } catch (Exception e) {
                            System.err.println("[redefinePlayerObjectToPreview] Error modifying method 'cl(int)' in " + className + ": " + e.getMessage());
                            e.printStackTrace();
                        }

                        // Always return the modified bytecode
                        return ctClass.toBytecode();
                    }
                }
        );

        ModPatcher.registerTransformer(
                "PandoraChat",
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        // Target the chat director class
                        return className.equals("com.threerings.crowd.chat.client.a");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[PandoraChat] Transforming " + className);

                        // Create a Javassist ClassPool for this transformation
                        ClassPool pool = ClassPool.getDefault();

                        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
                        // Ensure the class is not frozen if it was loaded earlier
                        ctClass.defrost();

                        try {
                            // Check if the field already exists to prevent errors on re-transformation
                            ctClass.getField("_pandora");
                            System.out.println("[Pandora] Field _pandora already exists in " + className + ". Skipping addition.");
                        } catch (javassist.NotFoundException e) {
                            // Field does not exist, so add it
                            // The type of _pandora should be the shop dialog class it holds
                            CtField pandoraField = new CtField(
                                    pool.get("com.threerings.projectx.shop.client.l"), // The type of your shopDialog
                                    "_pandora",
                                    ctClass
                            );
                            // Make it a private static field
                            pandoraField.setModifiers(java.lang.reflect.Modifier.PRIVATE | java.lang.reflect.Modifier.STATIC);
                            ctClass.addField(pandoraField);
                            System.out.println("[Pandora] Successfully added _pandora field to " + className);
                        }

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
                            System.err.println("[PandoraChat] Method 'a' not found in chat client. Game update? " + e.getMessage());
                            return null; // Return original bytecode
                        }

                        String insertCode =
                                "if ($2 != null && $2.toLowerCase().equals(\"/pandora\")) {" +
                                        "    try {" +
                                        "        com.threerings.projectx.util.A ctxxx = (com.threerings.projectx.util.A) this._ctx;" +
                                        "        if (this._pandora != null) {" +
                                        "            ctxxx.getRoot().addWindow(this._pandora);" +
                                        "        } else {" +
                                        "            com.threerings.projectx.client.aD hud = com.threerings.projectx.client.aD.h(ctxxx);" +
                                        "            com.threerings.projectx.shop.data.ShopDialogInfo sdi = new com.threerings.projectx.shop.data.ShopDialogInfo();" +
                                        "            sdi.level = 0;" +
                                        "            sdi.type = com.threerings.projectx.shop.data.ShopDialogInfo.Type.CROWN;" +
                                        "            sdi.preparer = com.threerings.projectx.shop.util.ItemPreparer.BASIC;" +
                                        "            sdi.seedKnight = true;" +
                                        "            sdi.shopService = null;" +
                                        "            sdi.model = null;" +
                                        "            sdi.animation = null;" +
                                        "            sdi.name = \"boileredit\";" +
                                        "            sdi.title = \"Pandora\";" +
                                        "            com.threerings.projectx.shop.client.l shopDialog = new com.threerings.projectx.shop.client.l(ctxxx, hud.ui(), sdi);" +
                                        "            try {" +
                                        "                java.lang.reflect.Field cfgmgrField = com.threerings.opengl.a.class.getDeclaredField(\"_cfgmgr\");" +
                                        "                cfgmgrField.setAccessible(true);" +
                                        "                com.threerings.config.ConfigManager configManager = (com.threerings.config.ConfigManager) cfgmgrField.get(ctxxx);" +
                                        "" +
                                        "                java.lang.reflect.Field groupsField = com.threerings.config.ConfigManager.class.getDeclaredField(\"_groups\");" +
                                        "                groupsField.setAccessible(true);" +
                                        "                java.util.HashMap groups = (java.util.HashMap) groupsField.get(configManager);" +
                                        "                com.threerings.config.ConfigGroup group = (com.threerings.config.ConfigGroup) groups.get(com.threerings.projectx.item.config.ItemConfig.class);" +
                                        "" +
                                        "                java.lang.reflect.Field configsByNameField = com.threerings.config.ConfigGroup.class.getDeclaredField(\"_configsByName\");" +
                                        "                configsByNameField.setAccessible(true);" +
                                        "                java.util.HashMap configsByName = (java.util.HashMap) configsByNameField.get(group);" +
                                        "                java.lang.Iterable configs = configsByName.values();" +
                                        "                java.util.List goodCounts = new java.util.ArrayList();" +
                                        "                java.util.Iterator iterator = configs.iterator();" +
                                        "                while (iterator.hasNext()) {" +
                                        "                    com.threerings.projectx.item.config.ItemConfig o = (com.threerings.projectx.item.config.ItemConfig) iterator.next();" +
                                        "                    if (o.getName().startsWith(\"Weapon/PvP/\")) {" +
                                        "                        continue;" +
                                        "                    }" +
                                        "                    com.threerings.config.ConfigReference ref = o.getReference();" +
                                        "                    goodCounts.add(new com.threerings.projectx.shop.data.UniqueShopInfo.GoodCount(" +
                                        "                        new com.threerings.projectx.shop.config.GoodConfig.Item(ref)," +
                                        "                        -1));" +
                                        "                }" +
                                        "                com.threerings.projectx.shop.data.UniqueShopInfo usi = new com.threerings.projectx.shop.data.UniqueShopInfo();" +
                                        "                java.lang.reflect.Field goodCountsField = com.threerings.projectx.shop.data.UniqueShopInfo.class.getDeclaredField(\"goodCounts\");" +
                                        "                goodCountsField.setAccessible(true);" +
                                        "                goodCountsField.set(usi, goodCounts);" +
                                        "                java.lang.reflect.Field aQxField = com.threerings.projectx.shop.client.l.class.getDeclaredField(\"aQQ\");" +
                                        "                aQxField.setAccessible(true);" +
                                        "                aQxField.set(shopDialog, usi);" +
                                        "            } catch (Exception e) {" +
                                        "                " +
                                        "            }" +
                                        "            sdi.sourceKey = new com.threerings.tudey.data.EntityKey.Actor(12);" +
                                        "            sdi.sourceTranslation = new com.threerings.math.Vector2f(11.377774F, 11.8713F);" +
                                        "            sdi.sourceRotation = -2.1118479F;" +
                                        "            sdi.sourceTransient = null;" +
                                        "            sdi.sourceCloseAnimation = null;" +
                                        "            this._pandora = shopDialog;" +
                                        "            ctxxx.getRoot().addWindow(shopDialog);" +
                                        "        }" +
                                        "        return \"success\";" +
                                        "    } catch (Exception e) {" +
                                        "        return \"Error: \" + e.getMessage();" +
                                        "    }" +
                                        "}";

                        chatMethod.insertBefore(insertCode);
                        System.out.println("[PandoraChat] Successfully added /pandora command to chat director.");

                        return ctClass.toBytecode(); // Return the modified bytecode
                    }
                }
        );
        System.out.println("[Pandora] Mod mounted successfully.");
    }


    // New helper method to create the PreviewApplyListener class
    private static void addPreviewApplyListenerClass() throws Exception {
        ClassPool pool = ClassPool.getDefault();
        CtClass ctClass = pool.makeClass("com.spiralknights.pandora.PreviewApplyListener");

        // Implement interface
        ctClass.addInterface(pool.get("com.threerings.opengl.gui.event.a"));

        // Add field _ctx
        CtField ctxField = new CtField(pool.get("com.threerings.projectx.util.A"), "_ctx", ctClass);
        ctxField.setModifiers(Modifier.PUBLIC | Modifier.TRANSIENT);
        ctClass.addField(ctxField);

        // Add constructor
        CtConstructor constructor = new CtConstructor(new CtClass[]{pool.get("com.threerings.projectx.util.A")}, ctClass);
        constructor.setModifiers(Modifier.PUBLIC);
        constructor.setBody("{this._ctx=$1; System.out.println(\"PreviewApplyListener New\");}");
        ctClass.addConstructor(constructor);

        // Add actionPerformed method
        CtMethod actionPerformedMethod = CtNewMethod.make(
                "public void actionPerformed(com.threerings.opengl.gui.event.ActionEvent actionEvent) {\n" +
                        "    com.threerings.opengl.gui.au _btn = (com.threerings.opengl.gui.au) actionEvent.getSource();\n" +
                        "    System.out.println(\"[Pandora] Apply\" + Boolean.toString(_btn.isSelected()));\n" +
                        "    com.threerings.projectx.util.A _ctx = this._ctx;\n" +
                        "    if (!_btn.isSelected()) {\n" +
                        "        System.out.println(\"Not Selected\");\n" +
                        "        com.threerings.projectx.data.PlayerObject playerObject = _ctx.ti();\n" +
                        "        com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"_equipmentPreview\").set(playerObject, null);\n" +
                        "        com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"_previewing\").set(playerObject, Boolean.valueOf(false));\n" +
                        "\n" +
                        "        com.threerings.projectx.client.aD _hud = com.threerings.projectx.client.aD.h(_ctx);\n" +
                        "        com.threerings.tudey.a.b.a _actorSprite = _hud.ui().Db();\n" + // Db unknown
                        "        com.threerings.projectx.client.sprite.KnightSprite _knightSprite = (com.threerings.projectx.client.sprite.KnightSprite) _actorSprite.Ne();\n" +
                        "        java.lang.reflect.Field _configPreviewField = com.threerings.projectx.client.sprite.KnightSprite.class.getDeclaredField(\"_configPreview\");\n" +
                        "        _configPreviewField.setAccessible(true);\n" +
                        "        _configPreviewField.set(_knightSprite, null);\n" +
                        "        return;\n" +
                        "    }\n" +
                        "    System.out.println(\"Selected\");\n" +
                        "    com.threerings.projectx.client.dj _whatever = _ctx.wh();\n" +
                        "    if (_whatever == null) {\n" +
                        "        _btn.setSelected(false);\n" +
                        "        return;\n" +
                        "    }\n" +
                        "    com.threerings.projectx.data.PlayerObject playerObject = _ctx.ti();\n" +
                        "    if (playerObject == null) {\n" +
                        "        _btn.setSelected(false);\n" +
                        "        return;\n" +
                        "    }\n" +
                        "    java.lang.reflect.Field _agdField = com.threerings.projectx.client.dj.class.getDeclaredField(\"agu\");\n" +
                        "    _agdField.setAccessible(true);\n" +
                        "    java.lang.Object _agdObject = _agdField.get(_whatever);\n" +
                        "    if (_agdObject == null) {\n" +
                        "        _btn.setSelected(false);\n" +
                        "        return;\n" +
                        "    }\n" +
                        "    com.threerings.projectx.data.PlayerEquipment playerEquipment = (com.threerings.projectx.data.PlayerEquipment) _agdObject;\n" +
                        "    java.lang.reflect.Field _colorField = com.threerings.projectx.data.PlayerEquipment.class.getDeclaredField(\"color\");\n" +
                        "    java.lang.reflect.Field _eyeField = com.threerings.projectx.data.PlayerEquipment.class.getDeclaredField(\"eye\");\n" +
                        "    java.lang.reflect.Field _heightField = com.threerings.projectx.data.PlayerEquipment.class.getDeclaredField(\"height\");\n" +
                        "    java.lang.reflect.Field _itemsField = com.threerings.projectx.data.PlayerEquipment.class.getDeclaredField(\"items\");\n" +
                        "    java.lang.reflect.Field _accessoriesField = com.threerings.projectx.data.PlayerEquipment.class.getDeclaredField(\"accessories\");\n" +
                        "    _colorField.setAccessible(true);\n" +
                        "    _eyeField.setAccessible(true);\n" +
                        "    _heightField.setAccessible(true);\n" +
                        "    _itemsField.setAccessible(true);\n" +
                        "    _accessoriesField.setAccessible(true);\n" +
                        "    java.lang.Object _colorObject = _colorField.get(playerEquipment);\n" +
                        "    java.lang.Object _eyeObject = _eyeField.get(playerEquipment);\n" +
                        "    java.lang.Object _heightObject = _heightField.get(playerEquipment);\n" +
                        "    java.lang.Object _itemsObject = _itemsField.get(playerEquipment);\n" +
                        "    java.lang.Object _accessoriesObject = _accessoriesField.get(playerEquipment);\n" +
                        "\n" +
                        "    java.util.Map itemsMap = (java.util.Map) _itemsObject;\n" +
                        "    java.util.Set accessoriesSet = (java.util.Set) _accessoriesObject;\n" +
                        "    com.threerings.projectx.item.data.LevelItem[] previewItems = new com.threerings.projectx.item.data.LevelItem[16];\n" +
                        "    for (int i = 0; i < previewItems.length; i++) {\n" +
                        "        Object itemObj = itemsMap.get(Integer.valueOf(i));\n" +
                        "        if (itemObj != null) {\n" +
                        "            previewItems[i] = (com.threerings.projectx.item.data.LevelItem) itemObj;\n" +
                        "            System.out.println(\"Item key=\" + Integer.valueOf(i) + \", value=\" + itemObj.toString());\n" +
                        "\n" +
                        "\n" +
                        "        }\n" +
                        "    }\n" +
                        "    int HELM_COSTUME_SLOT = 3;\n" +
                        "    int ARMOR_COSTUME_SLOT = 4;\n" +
                        "    java.util.Iterator accessoriesIterator = accessoriesSet.iterator();\n" +
                        "    while (accessoriesIterator.hasNext()) {\n" +
                        "        com.threerings.config.ConfigReference _accessoryConfigRef = (com.threerings.config.ConfigReference) accessoriesIterator.next();\n" +
                        "        boolean _added = previewItems[HELM_COSTUME_SLOT].j(_ctx.getConfigManager(), _accessoryConfigRef);\n" +
                        "        if (!_added) {\n" +
                        "            previewItems[ARMOR_COSTUME_SLOT].j(_ctx.getConfigManager(), _accessoryConfigRef);\n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "    java.lang.reflect.Field poColorField = com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"color\");\n" +
                        "    java.lang.reflect.Field poEyeField = com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"eye\");\n" +
                        "    java.lang.reflect.Field poHeightField = com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"height\");\n" +
                        "    java.lang.reflect.Field poItemsField = com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"items\");\n" +
                        "    java.lang.reflect.Field poEquipmentField = com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"equipment\");\n" +
                        "    java.lang.reflect.Field poEquipmentPreviewField = com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"_equipmentPreview\");\n" +
                        "    java.lang.reflect.Field poPreviewingField = com.threerings.projectx.data.PlayerObject.class.getDeclaredField(\"_previewing\");\n" +
                        "    poColorField.setAccessible(true);\n" +
                        "    poEyeField.setAccessible(true);\n" +
                        "    poHeightField.setAccessible(true);\n" +
                        "    poItemsField.setAccessible(true);\n" +
                        "    poEquipmentField.setAccessible(true);\n" +
                        "\n" +
                        "    java.lang.reflect.Field aLKField = com.threerings.projectx.item.data.Item.class.getDeclaredField(\"aLK\");\n" +
                        "    java.lang.reflect.Field aLLField = com.threerings.projectx.item.data.Item.class.getDeclaredField(\"aLL\");\n" +
                        "    java.lang.reflect.Field aLMField = com.threerings.projectx.item.data.Item.class.getDeclaredField(\"aLM\");\n" +
                        "    aLKField.setAccessible(true);\n" +
                        "    aLLField.setAccessible(true);\n" +
                        "    aLMField.setAccessible(true);\n" +
                        "\n" +
                        "    long[] equipment = (long[]) poEquipmentField.get(playerObject);\n" +
                        "    com.threerings.presents.dobj.DSet items = (com.threerings.presents.dobj.DSet) poItemsField.get(playerObject);\n" +
                        "    for (int i = 0; i < equipment.length; i++) {\n" +
                        "        if (previewItems[i] != null) {\n" +
                        "            Object item = items.f(Long.valueOf(equipment[i]));\n" +
                        "            if (item != null) {\n" +
                        "                // Get the values first\n" +
                        "                Object aLKValue = aLKField.get(item);\n" +
                        "                Object aLLValue = aLLField.get(item);\n" +
                        "                \n" +
                        "                // Then set them\n" +
                        "                aLKField.set(previewItems[i], aLKValue);\n" +
                        "                aLLField.set(previewItems[i], aLLValue);\n" +
                        "            }\n" +
                        "            previewItems[i].setDirty(true);\n" +
                        "        }\n" +
                        "    }\n" +
                        "    poColorField.set(playerObject, _colorObject);\n" +
                        "    poEyeField.set(playerObject, _eyeObject);\n" +
                        "    poHeightField.set(playerObject, _heightObject);\n" +
                        "    poEquipmentPreviewField.set(playerObject, previewItems);\n" +
                        "    poPreviewingField.set(playerObject, Boolean.valueOf(true));\n" +
                        "\n" +
                        "    java.lang.reflect.Method _createPlayerModelMethod = com.threerings.projectx.data.PlayerObject.class.getDeclaredMethod(\"a\", new java.lang.Class[]{\n" +
                        "            com.threerings.config.ConfigManager.class, boolean.class, boolean.class, int.class, boolean.class, Boolean.class, int.class,\n" +
                        "            com.threerings.projectx.data.ProjectXCodes.EyeType.class, com.threerings.projectx.data.ProjectXCodes.KnightHeight.class});\n" +
                        "    _createPlayerModelMethod.setAccessible(true);\n" +
                        "    com.threerings.config.ConfigReference _modelConfigRef = (com.threerings.config.ConfigReference) _createPlayerModelMethod.invoke(playerObject,\n" +
                        "            new java.lang.Object[]{_ctx.getConfigManager(), Boolean.valueOf(true), Boolean.valueOf(true), Integer.valueOf(-1),\n" +
                        "                    Boolean.valueOf(false), (Boolean) null, _colorObject, _eyeObject, _heightObject});\n" +
                        "    com.threerings.config.ConfigReference _previewConfigRef = com.threerings.projectx.data.PlayerObject.a(\"Character/PC/Default\", _modelConfigRef, new java.lang.Object[0]);\n" +
                        "\n" +
                        "    com.threerings.crowd.client.d _d = _ctx.qC();\n" +
                        "    java.lang.reflect.Field _FAField = com.threerings.crowd.client.d.class.getDeclaredField(\"FA\");\n" +
                        "    _FAField.setAccessible(true);\n" +
                        "    Object _FvObj = _FAField.get(_d);\n" +
                        "    if (_FvObj != null) {\n" +
                        "        com.threerings.crowd.client.t _t = (com.threerings.crowd.client.t) _FvObj;\n" +
                        "        if (_t instanceof com.threerings.projectx.client.eJ) {\n" +
                        "            com.threerings.projectx.client.eJ _el = (com.threerings.projectx.client.eJ) _t;\n" +
                        "            java.lang.reflect.Method _hgMethod = com.threerings.projectx.client.eJ.class.getDeclaredMethod(\"gV\", new Class[0]);\n" +
                        "            _hgMethod.setAccessible(true);\n" +
                        "            _hgMethod.invoke(_el, new Object[0]);\n" +
                        "        } else {\n" +
                        "            com.threerings.projectx.client.aD _hud = com.threerings.projectx.client.aD.h(_ctx);\n" +
                        "            com.threerings.tudey.a.b.a _actorSprite = _hud.ui().Db();\n" +
                        "            com.threerings.projectx.client.sprite.KnightSprite _knightSprite = (com.threerings.projectx.client.sprite.KnightSprite) _actorSprite.Ne();\n" +
                        "            java.lang.reflect.Field _configPreviewField = com.threerings.projectx.client.sprite.KnightSprite.class.getDeclaredField(\"_configPreview\");\n" +
                        "            _configPreviewField.setAccessible(true);\n" +
                        "            _configPreviewField.set(_knightSprite, _previewConfigRef);\n" +
                        "        }\n" +
                        "    }\n" +
                        "}", ctClass);
        //ctClass.addMethod(actionPerformedMethod); // Don't forget to add the method to the class

        // IMPORTANT: Call toClass() to make the class available
        //ctClass.toClass();
    }
}