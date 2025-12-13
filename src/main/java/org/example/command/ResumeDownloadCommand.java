package org.example.command;

import org.example.core.DownloadManager;

public class ResumeDownloadCommand implements DownloadCommand {

    private final DownloadManager manager;
    private final long taskId;

    public ResumeDownloadCommand(DownloadManager manager, long taskId) {
        this.manager = manager;
        this.taskId = taskId;
    }

    @Override
    public void execute() {
        manager.resume(taskId);
    }
}
