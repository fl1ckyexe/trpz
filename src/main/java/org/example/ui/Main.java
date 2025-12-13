package org.example.ui;

import org.example.controller.DownloadController;
import org.example.core.DownloadManager;
import org.example.downloader.HttpDownloader;
import org.example.observer.ConsoleDownloadObserver;
import org.example.observer.StatisticObserver;
import org.example.segment.HttpRangePeer;
import org.example.segment.Peer;
import org.example.segment.SegmentManager;
import org.example.speed.SpeedControl;
import org.example.storage.SQLiteStorage;


import java.util.List;


public class Main {

    public static void main(String[] args) {

        // Storage
        var storage = new SQLiteStorage("downloadmanager.db");

        // Core components
        var downloader = new HttpDownloader();
        var speed = new SpeedControl(0); // unlimited by default
        var segmentManager = new SegmentManager();

        var manager = new DownloadManager(storage, downloader, speed, segmentManager);

        // Observers
        manager.addObserver(new ConsoleDownloadObserver());
        manager.addObserver(new StatisticObserver());

        // Peers (HTTP sources)
        List<Peer> peers = List.of(
                new HttpRangePeer("peer1", "https://speed.hetzner.de/100MB.bin"),
                new HttpRangePeer("peer2", "https://speed.hetzner.de/100MB.bin")
        );
        manager.setPeers(peers);

        // Controller + UI
        var controller = new DownloadController(manager);
        var ui = new UserInterface(controller);
        ui.runConsole();
    }
}
