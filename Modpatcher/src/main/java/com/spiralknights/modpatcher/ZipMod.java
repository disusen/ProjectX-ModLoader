package com.spiralknights.modpatcher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.spiralknights.modpatcher.ModPatcher.CURRENT_DIR;
import static com.spiralknights.modpatcher.ModPatcher.MODS_DIR;

public class ZipMod {
    private static final String RSRC_DIR = CURRENT_DIR + File.separator + "rsrc";

    private static final Map<String, String> fileChecksums = new ConcurrentHashMap<>();

    static void loadZipMods() {
        System.out.println("Scanning for ZIP file replacement mods...");
        File modsDir = new File(MODS_DIR);
        File[] files = modsDir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("No files found in mods directory for ZIP processing");
            return;
        }
        List<File> zipFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".zip"))
                zipFiles.add(file);
        }
        if (zipFiles.isEmpty()) {
            System.out.println("No ZIP mods found in mods directory");
            return;
        }
        System.out.println("Found " + zipFiles.size() + " ZIP mod(s):");
        for (File zip : zipFiles)
            System.out.println("  - " + zip.getName());
        System.out.println();
        File rsrcDir = new File(RSRC_DIR);
        if (!rsrcDir.exists()) {
            System.err.println("Game rsrc directory not found: " + RSRC_DIR);
            System.err.println("Make sure you're running from the correct game directory");
            return;
        }
        for (File zipFile : zipFiles)
            processZipMod(zipFile);
    }

    private static void processZipMod(File zipFile) {
        System.out.println("Processing ZIP mod: " + zipFile.getName());
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            int filesExtracted = 0;
            int filesSkipped = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory())
                    continue;
                String entryPath = entry.getName();
                entryPath = entryPath.replace('\\', File.separatorChar);
                entryPath = entryPath.replace('/', File.separatorChar);
                if (entryPath.startsWith(File.separator))
                    entryPath = entryPath.substring(1);
                File targetFile = new File(CURRENT_DIR, entryPath);
                String canonicalGameDir = (new File(CURRENT_DIR)).getCanonicalPath();
                String canonicalTargetPath = targetFile.getCanonicalPath();
                if (!canonicalTargetPath.startsWith(canonicalGameDir)) {
                    System.err.println("  Skipping file outside game directory: " + entryPath);
                    continue;
                }
                if (shouldExtractFile(zip, entry, targetFile)) {
                    if (extractFileFromZip(zip, entry, targetFile)) {
                        filesExtracted++;
                        System.out.println("  Extracted: " + entryPath);
                        continue;
                    }
                    System.err.println("  Failed to extract: " + entryPath);
                    continue;
                }
                filesSkipped++;
                System.out.println("  Skipped (unchanged): " + entryPath);
            }
            System.out.println("ZIP mod processing complete: " + zipFile.getName());
            System.out.println("  Files extracted: " + filesExtracted);
            System.out.println("  Files skipped: " + filesSkipped);
            System.out.println();
        } catch (Exception e) {
            System.err.println("Failed to process ZIP mod: " + zipFile.getName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean shouldExtractFile(ZipFile zip, ZipEntry entry, File targetFile) {
        if (!targetFile.exists())
            return true;
        if (targetFile.length() != entry.getSize())
            return true;
        if (targetFile.lastModified() < entry.getTime())
            return true;
        try {
            String zipEntryChecksum = calculateZipEntryChecksum(zip, entry);
            String targetFileChecksum = calculateFileChecksum(targetFile);
            if (!zipEntryChecksum.equals(targetFileChecksum))
                return true;
        } catch (Exception e) {
            System.err.println("  Warning: Checksum comparison failed for " + targetFile.getName() + ", extracting anyway");
            return true;
        }
        return false;
    }

    private static boolean extractFileFromZip(ZipFile zip, ZipEntry entry, File targetFile) {
        try {
            File parentDir = targetFile.getParentFile();
            if (parentDir != null && !parentDir.exists() &&
                    !parentDir.mkdirs()) {
                System.err.println("Failed to create directory: " + parentDir.getAbsolutePath());
                return false;
            }
            try(InputStream in = zip.getInputStream(entry);
                FileOutputStream out = new FileOutputStream(targetFile)) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = in.read(buffer)) != -1)
                    out.write(buffer, 0, length);
            }
            targetFile.setLastModified(entry.getTime());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to extract file: " + targetFile.getName() + " - " + e.getMessage());
            return false;
        }
    }

    private static String calculateZipEntryChecksum(ZipFile zip, ZipEntry entry) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1)
                md.update(buffer, 0, length);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", new Object[] { Byte.valueOf(b) }));
        }
        return sb.toString();
    }

    private static String calculateFileChecksum(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fis.read(buffer)) != -1)
                md.update(buffer, 0, length);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", new Object[] { Byte.valueOf(b) }));
        }
        return sb.toString();
    }
}
