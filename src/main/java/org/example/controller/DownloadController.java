
package org.example.controller;

import org.example.command.*;
import org.example.core.DownloadManager;
import org.example.model.DownloadTask;

public class DownloadController {

    private final DownloadManager manager;

    public DownloadController(DownloadManager manager) {
        this.manager = manager;
    }

    public DownloadTask addDownload(String url, String fileName) {
        return manager.addDownload(url, fileName);
    }

    public void start(long taskId) {
        DownloadCommand cmd = new StartDownloadCommand(manager, taskId);
        cmd.execute();
    }

    public void pause(long taskId) {
        DownloadCommand cmd = new PauseDownloadCommand(manager, taskId);
        cmd.execute();
    }

    public void resume(long taskId) {
        DownloadCommand cmd = new ResumeDownloadCommand(manager, taskId);
        cmd.execute();
    }

    public void stop(long taskId) {
        DownloadCommand cmd = new StopDownloadCommand(manager, taskId);
        cmd.execute();
    }

    public void printSegments(long taskId) {
        manager.printSegments(taskId);
    }

    public void setSpeedLimit(long bytesPerSec) {
        manager.setSpeedLimitBytesPerSec(bytesPerSec);
    }
}
