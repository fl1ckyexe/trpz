
package org.example.speed;

public class SpeedControl {

    private volatile long maxBytesPerSec;

    public SpeedControl(long maxBytesPerSec) {
        this.maxBytesPerSec = maxBytesPerSec;
    }

    public long getMaxBytesPerSec() { return maxBytesPerSec; }
    public void setMaxBytesPerSec(long maxBytesPerSec) { this.maxBytesPerSec = maxBytesPerSec; }

    public void throttle(int bytesJustProcessed) {
        long limit = maxBytesPerSec;
        if (limit <= 0) return;

        // naive throttling: delay proportional to bytes chunk
        // delayMillis = bytes / (bytes/sec) * 1000
        double delayMs = (bytesJustProcessed * 1000.0) / limit;
        if (delayMs <= 1.0) return;

        try {
            Thread.sleep((long) delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
