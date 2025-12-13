package org.example.command;

import org.example.core.DownloadManager;

public class PauseDownloadCommand implements DownloadCommand {

    private final DownloadManager manager;
    private final long taskId;

    public PauseDownloadCommand(DownloadManager manager, long taskId) {
        this.manager = manager;
        this.taskId = taskId;
    }

    @Override
    public void execute() {
        manager.pause(taskId);
    }
}
