
package org.example.observer;

import  org.example.model.DownloadTask;

public interface DownloadObserver {
    void onTaskChanged(DownloadTask task);
    default void onLog(String message) {}
}
