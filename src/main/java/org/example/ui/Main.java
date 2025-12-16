package org.example.ui;

import org.example.core.AppSettings;
import org.example.core.DownloadManager;
import org.example.downloader.HttpDownloader;
import org.example.segment.SegmentManager;
import org.example.speed.SpeedControl;
import org.example.storage.LocalStorage;
import org.example.storage.SQLiteStorage;

public class Main {

    public static void main(String[] args) {


        LocalStorage storage = new SQLiteStorage("src/main/java/org/example/data/downloadmanager.db");
        storage.init();


        var downloader = new HttpDownloader();
        var speedControl = new SpeedControl(0);
        var segmentManager = new SegmentManager();
        var settings = AppSettings.load();

        var manager = new DownloadManager(
                storage,
                downloader,
                speedControl,
                segmentManager,
                settings
        );

        var task = manager.addDownload(
                "https://speed.hetzner.de/100MB.bin",
                "test.bin"
        );

        System.out.println("Created task id = " + task.getId());

        manager.start(task.getId());

        sleep(3000);
        manager.pause(task.getId());

        sleep(2000);
        manager.resume(task.getId());

        sleep(3000);
        manager.stop(task.getId());


        System.out.println("Test finished");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
