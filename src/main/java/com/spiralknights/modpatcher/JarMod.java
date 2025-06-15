package com.spiralknights.modpatcher;

/**
 * Simplified JAR mod data structure
 */
public class JarMod {
    private String fileName;
    private String displayName;
    private String description;
    private String author;
    private String version;
    private int minJDKVersion;
    private int maxJDKVersion;
    private boolean enabled;
    private String absolutePath;

    public JarMod() {
        this.minJDKVersion = 8;
        this.maxJDKVersion = 8;
        this.enabled = true;
    }

    public JarMod(String fileName) {
        this();
        this.fileName = fileName;
        this.displayName = fileName;
    }

    // Getters and setters
    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getMinJDKVersion() {
        return minJDKVersion;
    }

    public void setMinJDKVersion(int minJDKVersion) {
        this.minJDKVersion = minJDKVersion;
    }

    public int getMaxJDKVersion() {
        return maxJDKVersion;
    }

    public void setMaxJDKVersion(int maxJDKVersion) {
        this.maxJDKVersion = maxJDKVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public void setAbsolutePath(String absolutePath) {
        this.absolutePath = absolutePath;
    }

    /**
     * Check if this mod is compatible with the current JVM version
     */
    public boolean isJDKCompatible() {
        int currentJVM = getCurrentJVMVersion();
        return currentJVM >= minJDKVersion && currentJVM <= maxJDKVersion;
    }

    private static int getCurrentJVMVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf(".");
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    @Override
    public String toString() {
        return "JarMod{" +
                "fileName='" + fileName + '\'' +
                ", displayName='" + displayName + '\'' +
                ", author='" + author + '\'' +
                ", version='" + version + '\'' +
                ", minJDKVersion=" + minJDKVersion +
                ", maxJDKVersion=" + maxJDKVersion +
                ", enabled=" + enabled +
                '}';
    }
}