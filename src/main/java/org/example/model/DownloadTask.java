package org.example.model;


public class DownloadTask {
    private long id;
    private String url;
    private String fileName;
    private DownloadStatus status;
    private long totalBytes;
    private long downloadedBytes;

    public DownloadTask(long id, String url, String fileName) {
        this.id = id;
        this.url = url;
        this.fileName = fileName;
        this.status = DownloadStatus.CREATED;
        this.totalBytes = -1;
        this.downloadedBytes = 0;
    }

    public long getId() { return id; }
    public String getUrl() { return url; }
    public String getFileName() { return fileName; }
    public DownloadStatus getStatus() { return status; }
    public long getTotalBytes() { return totalBytes; }
    public long getDownloadedBytes() { return downloadedBytes; }

    public void setStatus(DownloadStatus status) { this.status = status; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public void setDownloadedBytes(long downloadedBytes) { this.downloadedBytes = downloadedBytes; }

    public double getProgress01() {
        if (totalBytes <= 0) return 0.0;
        return Math.min(1.0, (double) downloadedBytes / (double) totalBytes);
    }
}
