package org.example.core;

import org.example.downloader.AbstractDownloader;
import org.example.model.*;
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
    private final Map<Long, DownloadControl> controls = new HashMap<>();

    private final List<DownloadObserver> observers = new CopyOnWriteArrayList<>();

    private Thread worker;
    private volatile long currentTaskId = -1;

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

    public void addObserver(DownloadObserver o) {
        observers.add(o);
    }
    public void setSpeedLimitBytesPerSec(long bytesPerSec) {
        speedControl.setMaxBytesPerSec(bytesPerSec);
        log("Speed limit set to " + bytesPerSec + " B/s");
    }


    private void notifyTaskChanged(DownloadTask t) {
        observers.forEach(o -> o.onTaskChanged(t));
    }

    private void log(String msg) {
        observers.forEach(o -> o.onLog(msg));
    }

    public DownloadTask addDownload(String url, String fileName) {
        DownloadTask task = storage.createTask(url, fileName);
        tasksCache.put(task.getId(), task);
        notifyTaskChanged(task);
        return task;
    }

    public Optional<DownloadTask> getTask(long id) {
        if (tasksCache.containsKey(id)) return Optional.of(tasksCache.get(id));
        Optional<DownloadTask> t = storage.findTask(id);
        t.ifPresent(x -> tasksCache.put(id, x));
        return t;
    }

    public void start(long taskId) {

        DownloadTask task = getTask(taskId).orElseThrow();
        currentTaskId = taskId;

        DownloadControl control = new DownloadControl();
        controls.put(taskId, control);

        task.setStatus(DownloadStatus.RUNNING);
        storage.updateTask(task);
        notifyTaskChanged(task);

        List<DownloadSegment> segments = storage.loadSegments(taskId);

        if (segments.isEmpty()) {
            long total = downloader.probeContentLength(task.getUrl());

            if (total <= 0) {
                log("No Content-Length, fallback to single stream");
                segments = Collections.emptyList();
            } else {
                task.setTotalBytes(total);
                storage.updateTask(task);

                segments = createSegments(taskId, total, 4);
                storage.saveSegments(taskId, segments);
            }
        }

        final List<DownloadSegment> segmentsFinal = segments;
        segmentManager.setSegments(segmentsFinal);

        worker = new Thread(() -> downloader.download(
                task,
                segmentsFinal,
                peers,
                speedControl,
                control,
                new Callbacks(task)
        ));

        worker.setDaemon(true);
        worker.start();
    }

    public void pause(long taskId) {
        DownloadControl c = controls.get(taskId);
        if (c != null) c.pause();
        updateStatus(taskId, DownloadStatus.PAUSED);
    }

    public void resume(long taskId) {
        DownloadControl c = controls.get(taskId);
        if (c != null) c.resume();
        updateStatus(taskId, DownloadStatus.RUNNING);
    }

    public void stop(long taskId) {
        DownloadControl c = controls.get(taskId);
        if (c != null) c.cancel();
        updateStatus(taskId, DownloadStatus.FAILED);
    }
    public AbstractDownloader getDownloader() {
        return downloader;
    }


    private void updateStatus(long taskId, DownloadStatus st) {
        getTask(taskId).ifPresent(t -> {
            t.setStatus(st);
            storage.updateTask(t);
            notifyTaskChanged(t);
        });
    }

    private List<DownloadSegment> createSegments(long taskId, long total, int count) {
        List<DownloadSegment> list = new ArrayList<>();
        long part = total / count;
        long start = 0;

        for (int i = 0; i < count; i++) {
            long end = (i == count - 1) ? total - 1 : start + part - 1;
            list.add(new DownloadSegment(0, taskId, i, start, end));
            start = end + 1;
        }
        return list;
    }

    private class Callbacks implements AbstractDownloader.DownloadCallbacks {

        private final DownloadTask task;

        Callbacks(DownloadTask task) {
            this.task = task;
        }

        @Override
        public void onSegmentProgress(long taskId, int idx, long downloaded) {

            List<DownloadSegment> segs = storage.loadSegments(taskId);

            for (DownloadSegment s : segs) {
                if (s.getIndex() == idx) {
                    s.setDownloadedBytes(downloaded);
                    s.setStatus(
                            downloaded >= s.getLength()
                                    ? SegmentStatus.COMPLETED
                                    : SegmentStatus.RUNNING
                    );
                    storage.updateSegment(s);
                }
            }

            long sum = segs.stream().mapToLong(DownloadSegment::getDownloadedBytes).sum();
            task.setDownloadedBytes(sum);
            storage.updateTask(task);
            notifyTaskChanged(task);
        }

        @Override public void onLog(String msg) { log(msg); }

        @Override
        public void onCompleted(long taskId) {
            task.setStatus(DownloadStatus.COMPLETED);
            storage.updateTask(task);
            notifyTaskChanged(task);
            log("Task completed: " + taskId);
        }

        @Override
        public void onError(long taskId, Exception e) {
            task.setStatus(DownloadStatus.FAILED);
            storage.updateTask(task);
            notifyTaskChanged(task);
            log("Task failed: " + e.getMessage());
        }
    }

    public void printSegments(long taskId) {
        List<DownloadSegment> segs = storage.loadSegments(taskId);
        segmentManager.setSegments(segs);

        for (DownloadSegment s : segmentManager) {
            log("Segment idx=" + s.getIndex()
                    + " range=" + s.getStartByte() + "-" + s.getEndByte()
                    + " downloaded=" + s.getDownloadedBytes()
                    + " status=" + s.getStatus());
        }
    }

}
