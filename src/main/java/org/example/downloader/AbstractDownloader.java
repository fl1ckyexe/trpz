
package org.example.downloader;

import org.example.model.DownloadSegment;
import org.example.model.DownloadTask;
import org.example.segment.Peer;
import org.example.speed.SpeedControl;

import java.util.List;


public abstract class AbstractDownloader {

    public final void download(DownloadTask task,
                               List<DownloadSegment> segments,
                               List<Peer> peers,
                               SpeedControl speedControl,
                               DownloadCallbacks callbacks) {
        try {
            prepare(task, segments);
            open(task);
            doDownload(task, segments, peers, speedControl, callbacks);
            finish(task);
        } catch (Exception e) {
            callbacks.onError(task.getId(), e);
        }
    }

    protected void prepare(DownloadTask task, List<DownloadSegment> segments) {
        // validate, create folders, etc.
    }

    protected void open(DownloadTask task) {
        // open files/resources if needed
    }

    protected abstract void doDownload(DownloadTask task,
                                       List<DownloadSegment> segments,
                                       List<Peer> peers,
                                       SpeedControl speedControl,
                                       DownloadCallbacks callbacks) throws Exception;

    protected void finish(DownloadTask task) {
        // close resources
    }

    // callbacks to integrate with manager/observers
    public interface DownloadCallbacks {
        void onSegmentProgress(long taskId, int segmentIndex, long segmentDownloadedBytes);
        void onLog(String msg);
        void onCompleted(long taskId);
        void onError(long taskId, Exception e);
    }
}
