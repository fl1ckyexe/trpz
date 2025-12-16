package org.example.storage;

import org.example.model.DownloadSegment;
import org.example.model.DownloadTask;

import java.util.List;
import java.util.Optional;

public interface LocalStorage {

    void init();

    DownloadTask createTask(String url, String fileName);

    Optional<DownloadTask> findTask(long taskId);

    void updateTask(DownloadTask task);



    void saveSegments(long taskId, List<DownloadSegment> segments);

    List<DownloadSegment> loadSegments(long taskId);
    List<DownloadTask> loadAllTasks();
    void updateSegment(DownloadSegment segment);
}
