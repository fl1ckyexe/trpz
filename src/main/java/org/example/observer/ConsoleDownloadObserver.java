package org.example.observer;


import org.example.model.DownloadTask;

public class ConsoleDownloadObserver implements DownloadObserver {

    @Override
    public void onTaskChanged(DownloadTask task) {
        System.out.printf(
                "[ConsoleObserver] task=%d status=%s progress=%.2f%%%n",
                task.getId(),
                task.getStatus(),
                task.getProgress01() * 100.0
        );
    }

    @Override
    public void onLog(String message) {
        System.out.println("[ConsoleObserver] " + message);
    }
}

