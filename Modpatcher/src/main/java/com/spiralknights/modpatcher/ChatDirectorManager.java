package com.spiralknights.modpatcher;

import javassist.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.spiralknights.modpatcher.ModPatcher.registerTransformer;

public class ChatDirectorManager {
    private static final Map<String, String> chatCommands = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> chatFields = new ConcurrentHashMap<>();
    private static boolean chatDirectorTransformed = false;

    public static void registerChatClientField(String fieldName, String fieldTypeClassName) {
        if (fieldName == null || fieldName.trim().isEmpty()) {
            System.err.println("[ModPatcher] Attempted to register a field with a null or empty name.");
            return;
        }
        if (fieldTypeClassName == null || fieldTypeClassName.trim().isEmpty()) {
            System.err.println("[ModPatcher] Attempted to register field '" + fieldName + "' with a null or empty type class name.");
            return;
        }
        chatFields.put(fieldName, fieldTypeClassName);
        System.out.println("[ModPatcher] Registered dynamic field: " + fieldName + " of type " + fieldTypeClassName);
    }

    /**
     * Method for mods to register their chat commands
     * @param command The chat command (e.g., "/pandora", "/loler")
     * @param injectionCode The complete if-block code to inject
     * @param modName Name of the mod registering this command
     */
    public static void registerChatCommand(String command, String injectionCode, String modName) {
        String commandKey = command.toLowerCase();
        if (chatCommands.containsKey(commandKey)) {
            System.out.println("[ModPatcher] Warning: Command '" + commandKey +
                    "' is already registered. Overwriting with " + modName);
        }
        chatCommands.put(commandKey, injectionCode);
        System.out.println("[ModPatcher] Registered chat command '" + commandKey +
                "' for mod: " + modName);
    }

    /**
     * Call this method after all mods have registered their commands
     * This will create and register the chat director transformer
     */
    public static void finalizeChatDirectorTransformation() {
        if (chatCommands.isEmpty()) {
            System.out.println("[ModPatcher] No chat commands registered, skipping chat director transformation.");
            return;
        }

        if (chatDirectorTransformed) {
            System.out.println("[ModPatcher] Chat director already transformed, skipping.");
            return;
        }

        // Register the chat director transformer
        registerTransformer(
                "ChatDirector_Combined",
                new ModPatcher.ModTransformer() {
                    @Override
                    public boolean shouldTransform(String className) {
                        return className.equals("com.threerings.crowd.chat.client.a");
                    }

                    @Override
                    public byte[] transform(String className, byte[] classfileBuffer) throws Exception {
                        System.out.println("[ModPatcher] Transforming chat director with " +
                                chatCommands.size() + " registered commands");

                        ClassPool pool = ClassPool.getDefault();
                        CtClass ctClass = pool.makeClass(new java.io.ByteArrayInputStream(classfileBuffer));
                        ctClass.defrost();

                        for (ConcurrentHashMap.Entry<String, String> entry : chatFields.entrySet()) {
                            String fieldName = entry.getKey();
                            String fieldTypeClassName = entry.getValue();

                            try {
                                ctClass.getField(fieldName);
                                System.out.println("[ModPatcher] Field " + fieldName + " already exists in " + className + ". Skipping addition.");
                            } catch (NotFoundException e) {
                                // Field does not exist, so add it
                                CtField newField = new CtField(
                                        pool.get(fieldTypeClassName), // Dynamic type
                                        fieldName,                    // Dynamic name
                                        ctClass
                                );
                                // Make it a private static field
                                newField.setModifiers(Modifier.PRIVATE | Modifier.STATIC);
                                ctClass.addField(newField);
                                System.out.println("[ModPatcher] Added chatDirector field: " + fieldName + " of type " + fieldTypeClassName + " to " + className);
                            }
                        }

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

                        }

                        // Find the chat director method
                        CtMethod chatMethod = null;
                        try {
                            chatMethod = ctClass.getDeclaredMethod("a", new CtClass[]{
                                    pool.get("com.threerings.crowd.chat.client.m"),
                                    pool.get("java.lang.String"),
                                    CtClass.booleanType
                            });
                        } catch (NotFoundException e) {
                            System.err.println("[ModPatcher] Method 'a' not found in chat client. Game update? " + e.getMessage());
                            return null;
                        }

                        // Build the combined injection code by concatenating all registered commands
                        String combinedCode = buildCombinedInjectionCode();

                        chatMethod.insertBefore(combinedCode);
                        System.out.println("[ModPatcher] Successfully added " + chatCommands.size() +
                                " chat commands to chat director.");

                        return ctClass.toBytecode();
                    }
                }
        );

        chatDirectorTransformed = true;
        System.out.println("[ModPatcher] Chat director transformer registered and ready.");
    }

    /**
     * Simple string concatenation approach - just like your current method
     */
    private static String buildCombinedInjectionCode() {
        String insertCode = "";

        // Loop through all registered commands and concatenate them
        for (String injectionCode : chatCommands.values()) {
            insertCode = insertCode + injectionCode;
        }

        return insertCode;
    }
}
