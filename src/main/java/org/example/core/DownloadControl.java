package org.example.core;

public class DownloadControl {

    private boolean paused = false;
    private boolean cancelled = false;

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
        notifyAll();
    }

    public synchronized void cancel() {
        cancelled = true;
        notifyAll();
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    public synchronized boolean isCancelled() {
        return cancelled;
    }

    public synchronized void awaitResume() throws InterruptedException {
        while (paused && !cancelled) {
            wait();
        }
    }
}
