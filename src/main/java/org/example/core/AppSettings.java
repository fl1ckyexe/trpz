package org.example.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

public class AppSettings {

    private static final String KEY_DOWNLOAD_DIR = "downloadDir";
    private static final String KEY_INCOMPLETE_DIR = "incompleteDir";

    private final Preferences prefs =
            Preferences.userNodeForPackage(AppSettings.class);

    private Path downloadDir;
    private Path incompleteDir;

    public AppSettings() {
        Path home = Paths.get(System.getProperty("user.home"));
        this.downloadDir = home.resolve("Downloads");
        this.incompleteDir = home.resolve("Downloads").resolve("incomplete");
        ensureDirs();
    }

    public static AppSettings load() {
        AppSettings s = new AppSettings();

        String d = s.prefs.get(KEY_DOWNLOAD_DIR, null);
        if (d != null && !d.isBlank()) {
            s.downloadDir = Paths.get(d);
        }

        String inc = s.prefs.get(KEY_INCOMPLETE_DIR, null);
        if (inc != null && !inc.isBlank()) {
            s.incompleteDir = Paths.get(inc);
        }

        s.ensureDirs();
        return s;
    }

    public void save() {
        prefs.put(KEY_DOWNLOAD_DIR, downloadDir.toString());
        prefs.put(KEY_INCOMPLETE_DIR, incompleteDir.toString());
    }

    private void ensureDirs() {
        try {
            Files.createDirectories(downloadDir);
            Files.createDirectories(incompleteDir);
        } catch (Exception ignored) {}
    }

    public Path getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(Path downloadDir) {
        this.downloadDir = downloadDir;
        ensureDirs();
    }

    public Path getIncompleteDir() {
        return incompleteDir;
    }

    public void setIncompleteDir(Path incompleteDir) {
        this.incompleteDir = incompleteDir;
        ensureDirs();
    }
}
