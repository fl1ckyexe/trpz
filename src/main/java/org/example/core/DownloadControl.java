package org.example.core;

import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadControl {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void cancel() {
        cancelled.set(true);
    }

    public void pause() {
        paused.set(true);
    }

    public void resume() {
        paused.set(false);
    }

    public void waitIfPaused() {
        while (paused.get() && !cancelled.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
