package org.example.model;

public class DownloadSegment {
    private long id;
    private long taskId;
    private int index;
    private long startByte;
    private long endByte; // inclusive
    private long downloadedBytes;
    private SegmentStatus status;

    public DownloadSegment(long id, long taskId, int index, long startByte, long endByte) {
        this.id = id;
        this.taskId = taskId;
        this.index = index;
        this.startByte = startByte;
        this.endByte = endByte;
        this.downloadedBytes = 0;
        this.status = SegmentStatus.CREATED;
    }

    public long getId() { return id; }
    public long getTaskId() { return taskId; }
    public int getIndex() { return index; }
    public long getStartByte() { return startByte; }
    public long getEndByte() { return endByte; }
    public long getDownloadedBytes() { return downloadedBytes; }
    public SegmentStatus getStatus() { return status; }

    public void setDownloadedBytes(long downloadedBytes) { this.downloadedBytes = downloadedBytes; }
    public void setStatus(SegmentStatus status) { this.status = status; }

    public long getLength() {
        return (endByte - startByte + 1);
    }

    public double getProgress01() {
        long len = getLength();
        if (len <= 0) return 0.0;
        return Math.min(1.0, (double) downloadedBytes / (double) len);
    }
}
