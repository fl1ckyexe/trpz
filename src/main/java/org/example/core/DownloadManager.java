package org.example.core;

import org.example.downloader.AbstractDownloader;
import org.example.model.*;
import org.example.observer.DownloadObserver;
import org.example.segment.Peer;
import org.example.segment.SegmentManager;
import org.example.speed.SpeedControl;
import org.example.storage.LocalStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class DownloadManager {

    private final LocalStorage storage;
    private final AbstractDownloader downloader;
    private final SpeedControl speedControl;
    private final SegmentManager segmentManager;
    private final AppSettings settings;

    private final Map<Long, DownloadTask> tasksCache = new HashMap<>();
    private final Map<Long, DownloadControl> controls = new HashMap<>();

    private final List<DownloadObserver> observers = new CopyOnWriteArrayList<>();

    private Thread worker;
    private volatile long currentTaskId = -1;

    private List<Peer> peers = new ArrayList<>();

    public DownloadManager(LocalStorage storage,
                           AbstractDownloader downloader,
                           SpeedControl speedControl,
                           SegmentManager segmentManager,
                           AppSettings settings) {
        this.storage = storage;
        this.downloader = downloader;
        this.speedControl = speedControl;
        this.segmentManager = segmentManager;
        this.settings = settings;
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

    public DownloadTask addDownload(String url, String finalPath) {

        DownloadTask task = storage.createTask(url, finalPath);
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

        // 1️⃣ Загружаем сегменты
        List<DownloadSegment> segmentsTmp = storage.loadSegments(taskId);

        if (segmentsTmp.isEmpty()) {
            long total = downloader.probeContentLength(task.getUrl());

            if (total > 0) {
                task.setTotalBytes(total);
                storage.updateTask(task);

                segmentsTmp = createSegments(taskId, total, 4);
                storage.saveSegments(taskId, segmentsTmp);
            } else {
                segmentsTmp = Collections.emptyList();
            }
        }

        // 2️⃣ ФИКС: создаём final-переменную
        final List<DownloadSegment> segments = segmentsTmp;

        segmentManager.setSegments(segments);


        Path tmpFile = settings.getIncompleteDir()
                .resolve(taskId + ".bin");



        worker = new Thread(() -> downloader.download(
                task,
                segments,
                peers,
                speedControl,
                control,
                new Callbacks(task, tmpFile)
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

        // 1. Если поток жив — просто resume
        DownloadControl c = controls.get(taskId);
        if (c != null) {
            c.resume();
            updateStatus(taskId, DownloadStatus.RUNNING);
            return;
        }

        // 2. Если потока нет — это resume после перезапуска
        start(taskId);
    }



    public void stop(long taskId) {
        DownloadControl c = controls.get(taskId);
        if (c != null) c.cancel();
        updateStatus(taskId, DownloadStatus.FAILED);
    }

    public AbstractDownloader getDownloader() {
        return downloader;
    }

    public List<DownloadTask> getUnfinishedTasks() {
        return storage.loadAllTasks().stream()
                .filter(t ->
                        t.getStatus() == DownloadStatus.PAUSED ||
                                t.getStatus() == DownloadStatus.RUNNING
                )
                .toList();
    }


    private void updateStatus(long taskId, DownloadStatus st) {
        getTask(taskId).ifPresent(t -> {
            t.setStatus(st);
            storage.updateTask(t);
            notifyTaskChanged(t);
        });
    }
    public void printSegments(long taskId) {
        List<DownloadSegment> segs = storage.loadSegments(taskId);
        for (DownloadSegment s : segs) {
            log("Segment " + s.getIndex() +
                    " [" + s.getStartByte() + "-" + s.getEndByte() + "] " +
                    s.getDownloadedBytes() + "/" + s.getLength() +
                    " status=" + s.getStatus());
        }
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
        private final Path tmpFile;

        Callbacks(DownloadTask task, Path tmpFile) {
            this.task = task;
            this.tmpFile = tmpFile;
        }

        @Override
        public void onSegmentProgress(long taskId, int idx, long downloaded) {

            List<DownloadSegment> segs = storage.loadSegments(taskId);

            for (DownloadSegment s : segs) {
                if (s.getIndex() == idx) {
                    s.setDownloadedBytes(downloaded);
                    storage.updateSegment(s);
                }
            }

            long sum = segs.stream()
                    .mapToLong(DownloadSegment::getDownloadedBytes)
                    .sum();

            task.setDownloadedBytes(sum);
            storage.updateTask(task);
            notifyTaskChanged(task);
        }


        @Override public void onLog(String msg) { log(msg); }

        @Override
        public void onCompleted(long taskId) {
            try {
                Files.move(
                        tmpFile,
                        Path.of(task.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (Exception e) {
                log("Move failed: " + e.getMessage());
            }

            task.setStatus(DownloadStatus.COMPLETED);
            storage.updateTask(task);
            notifyTaskChanged(task);
        }


        @Override
        public void onError(long taskId, Exception e) {
            task.setStatus(DownloadStatus.FAILED);
            storage.updateTask(task);
            notifyTaskChanged(task);
            log("Task failed: " + e.getMessage());
        }
    }
}
