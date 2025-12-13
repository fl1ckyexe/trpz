package org.example.observer;

import org.example.model.DownloadStatus;
import org.example.model.DownloadTask;

public class StatisticObserver implements DownloadObserver {

    private int completedCount = 0;
    private int failedCount = 0;

    @Override
    public void onTaskChanged(DownloadTask task) {
        if (task.getStatus() == DownloadStatus.COMPLETED) {
            completedCount++;
        } else if (task.getStatus() == DownloadStatus.FAILED) {
            failedCount++;
        }
    }

    @Override
    public void onLog(String message) {
        // можна логувати в файл/БД
    }

    public int getCompletedCount() { return completedCount; }
    public int getFailedCount() { return failedCount; }
}
