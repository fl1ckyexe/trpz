
package org.example.core;

import org.example.downloader.AbstractDownloader;
import org.example.model.*;
import org.example.model.DownloadTask;
import org.example.observer.DownloadObserver;
import org.example.segment.Peer;
import org.example.segment.SegmentManager;
import org.example.speed.SpeedControl;
import org.example.storage.LocalStorage;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class DownloadManager {

    private final LocalStorage storage;
    private final AbstractDownloader downloader;
    private final SpeedControl speedControl;
    private final SegmentManager segmentManager;

    private final Map<Long, DownloadTask> tasksCache = new HashMap<>();
    private final List<DownloadObserver> observers = new CopyOnWriteArrayList<>();

    private volatile long currentTaskId = -1; // для простоти (1 активне завантаження в каркасі)

    private List<Peer> peers = new ArrayList<>();

    public DownloadManager(LocalStorage storage,
                           AbstractDownloader downloader,
                           SpeedControl speedControl,
                           SegmentManager segmentManager) {
        this.storage = storage;
        this.downloader = downloader;
        this.speedControl = speedControl;
        this.segmentManager = segmentManager;

        storage.init();
    }

    // OBSERVER
    public void addObserver(DownloadObserver observer) { observers.add(observer); }
    public void removeObserver(DownloadObserver observer) { observers.remove(observer); }

    private void notifyTaskChanged(DownloadTask task) {
        for (DownloadObserver o : observers) o.onTaskChanged(task);
    }

    private void log(String msg) {
        for (DownloadObserver o : observers) o.onLog(msg);
    }

    // CONFIG
    public void setPeers(List<Peer> peers) {
        this.peers = new ArrayList<>(peers);
    }

    public void setSpeedLimitBytesPerSec(long bytesPerSec) {
        speedControl.setMaxBytesPerSec(bytesPerSec);
        log("Speed limit set to " + bytesPerSec + " B/s");
    }

    // TASKS
    public DownloadTask addDownload(String url, String fileName) {
        DownloadTask task = storage.createTask(url, fileName);
        tasksCache.put(task.getId(), task);
        notifyTaskChanged(task);
        return task;
    }

    public Optional<DownloadTask> getTask(long taskId) {
        if (tasksCache.containsKey(taskId)) return Optional.of(tasksCache.get(taskId));
        Optional<DownloadTask> db = storage.findTask(taskId);
        db.ifPresent(t -> tasksCache.put(taskId, t));
        return db;
    }

    // LIFECYCLE
    public void start(long taskId) {
        DownloadTask task = getTask(taskId).orElseThrow();
        if (task.getStatus() == DownloadStatus.RUNNING) return;

        currentTaskId = taskId;
        task.setStatus(DownloadStatus.RUNNING);
        storage.updateTask(task);
        notifyTaskChanged(task);

        // load segments or create if empty
        List<DownloadSegment> segments = storage.loadSegments(taskId);
        if (segments.isEmpty()) {
            // very simple segmentation: 4 segments with fake total
            // (real total should be detected by HEAD/GET content-length)
            long fakeTotal = 4 * 1024 * 1024;
            task.setTotalBytes(fakeTotal);
            storage.updateTask(task);

            segments = createSegments(taskId, fakeTotal, 4);
            storage.saveSegments(taskId, segments);
        }

        segmentManager.setSegments(segments);
        log("Start download taskId=" + taskId + " segments=" + segments.size());

        // run sync in this demo; for real app use background threads
        downloader.download(task, segments, peers, speedControl, new AbstractDownloader.DownloadCallbacks() {
            @Override
            public void onSegmentProgress(long tId, int segmentIndex, long segmentDownloadedBytes) {
                if (tId != currentTaskId) return;

                // update local segment state
                List<DownloadSegment> segs = storage.loadSegments(tId);
                for (DownloadSegment s : segs) {
                    if (s.getIndex() == segmentIndex) {
                        s.setDownloadedBytes(segmentDownloadedBytes);
                        s.setStatus(segmentDownloadedBytes >= s.getLength() ? SegmentStatus.COMPLETED : SegmentStatus.RUNNING);
                        storage.updateSegment(s);
                        break;
                    }
                }

                // update task progress (sum segment downloaded)
                long sum = 0;
                for (DownloadSegment s : segs) sum += s.getDownloadedBytes();
                task.setDownloadedBytes(sum);
                storage.updateTask(task);
                notifyTaskChanged(task);
            }

            @Override
            public void onLog(String msg) {
                log(msg);
            }

            @Override
            public void onCompleted(long tId) {
                if (tId != currentTaskId) return;
                task.setStatus(DownloadStatus.COMPLETED);
                storage.updateTask(task);
                notifyTaskChanged(task);
                log("Task completed: " + tId);
            }

            @Override
            public void onError(long tId, Exception e) {
                if (tId != currentTaskId) return;
                task.setStatus(DownloadStatus.FAILED);
                storage.updateTask(task);
                notifyTaskChanged(task);
                log("Task failed: " + tId + " error=" + e.getMessage());
            }
        });
    }

    public void pause(long taskId) {
        DownloadTask task = getTask(taskId).orElseThrow();
        task.setStatus(DownloadStatus.PAUSED);
        storage.updateTask(task);
        notifyTaskChanged(task);
        log("Paused taskId=" + taskId);
        // Реальна реалізація: зупинка потоків / cancellation tokens
    }

    public void resume(long taskId) {
        DownloadTask task = getTask(taskId).orElseThrow();
        if (task.getStatus() != DownloadStatus.PAUSED) return;
        log("Resume taskId=" + taskId);
        start(taskId);
    }

    public void stop(long taskId) {
        DownloadTask task = getTask(taskId).orElseThrow();
        task.setStatus(DownloadStatus.FAILED);
        storage.updateTask(task);
        notifyTaskChanged(task);
        log("Stopped taskId=" + taskId);
        // Реальна реалізація: cancellation
    }

    private List<DownloadSegment> createSegments(long taskId, long totalBytes, int count) {
        List<DownloadSegment> list = new ArrayList<>();
        long part = totalBytes / count;
        long start = 0;
        for (int i = 0; i < count; i++) {
            long end = (i == count - 1) ? totalBytes - 1 : (start + part - 1);
            list.add(new DownloadSegment(0, taskId, i, start, end));
            start = end + 1;
        }
        return list;
    }

    // Iterator usage (over SegmentManager)
    public void printSegments(long taskId) {
        List<DownloadSegment> segs = storage.loadSegments(taskId);
        segmentManager.setSegments(segs);
        for (DownloadSegment s : segmentManager) {
            log("Segment idx=" + s.getIndex() + " range=" + s.getStartByte() + "-" + s.getEndByte());
        }
    }
}
