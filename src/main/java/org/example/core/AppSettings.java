package org.example.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.prefs.Preferences;

public class AppSettings {

    private static final String KEY_DOWNLOAD_DIR = "downloadDir";

    private final Preferences prefs = Preferences.userNodeForPackage(AppSettings.class);

    private Path downloadDir;

    public AppSettings() {

        Path home = Paths.get(System.getProperty("user.home"));
        Path def = home.resolve("Downloads");
        this.downloadDir = def;
    }

    public static AppSettings load() {
        AppSettings s = new AppSettings();
        String saved = s.prefs.get(KEY_DOWNLOAD_DIR, null);
        if (saved != null && !saved.isBlank()) {
            s.downloadDir = Paths.get(saved);
        }
        return s;
    }

    public void save() {
        if (downloadDir != null) {
            prefs.put(KEY_DOWNLOAD_DIR, downloadDir.toString());
        }
    }

    public Path getDownloadDir() {
        return downloadDir;
    }

    public void setDownloadDir(Path downloadDir) {
        this.downloadDir = downloadDir;
    }
}
