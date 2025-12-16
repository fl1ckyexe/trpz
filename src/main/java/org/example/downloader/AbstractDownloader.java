package org.example.downloader;

import org.example.core.DownloadControl;
import org.example.model.DownloadSegment;
import org.example.model.DownloadTask;
import org.example.segment.Peer;
import org.example.speed.SpeedControl;

import java.util.List;

public abstract class AbstractDownloader {

    public final void download(
            DownloadTask task,
            List<DownloadSegment> segments,
            List<Peer> peers,
            SpeedControl speedControl,
            DownloadControl control,
            DownloadCallbacks callbacks)
    {
        try {
            prepare(task, segments);
            open(task);
            doDownload(task, segments, peers, speedControl, control, callbacks);
            finish(task);
        } catch (Exception e) {
            callbacks.onError(task.getId(), e);
        }
    }

    public long probeContentLength(String url) {
        return -1L;
    }

    protected void prepare(DownloadTask task, List<DownloadSegment> segments) {}
    protected void open(DownloadTask task) {}

    protected abstract void doDownload(
            DownloadTask task,
            List<DownloadSegment> segments,
            List<Peer> peers,
            SpeedControl speedControl,
            DownloadControl control,
            DownloadCallbacks callbacks
    ) throws Exception;

    protected void finish(DownloadTask task) {}

    public interface DownloadCallbacks {
        void onSegmentProgress(long taskId, int segmentIndex, long segmentDownloadedBytes);
        void onLog(String msg);
        void onCompleted(long taskId);
        void onError(long taskId, Exception e);
    }
}
