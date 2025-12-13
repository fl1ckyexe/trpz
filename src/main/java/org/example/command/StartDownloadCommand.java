package org.example.command;

import org.example.core.DownloadManager;

public class StartDownloadCommand implements DownloadCommand {

    private final DownloadManager manager;
    private final long taskId;

    public StartDownloadCommand(DownloadManager manager, long taskId) {
        this.manager = manager;
        this.taskId = taskId;
    }

    @Override
    public void execute() {
        manager.start(taskId);
    }
}
