package org.example.downloader;



import org.example.model.DownloadSegment;
import org.example.model.DownloadTask;
import org.example.segment.Peer;
import org.example.speed.SpeedControl;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class HttpDownloader extends AbstractDownloader {

    private final HttpClient client = HttpClient.newBuilder().build();

    @Override
    protected void doDownload(DownloadTask task,
                              List<DownloadSegment> segments,
                              List<Peer> peers,
                              SpeedControl speedControl,
                              DownloadCallbacks callbacks) throws Exception {
        if (peers == null || peers.isEmpty()) {
            throw new IllegalStateException("No peers configured. Add HttpRangePeer sources first.");
        }

        // Demo: sequential per segment (real app: multithread)
        int peerIndex = 0;
        for (DownloadSegment seg : segments) {
            Peer peer = peers.get(peerIndex % peers.size());
            peerIndex++;

            callbacks.onLog("Downloading segment idx=" + seg.getIndex() + " from peer=" + peer.getId());

            String range = "bytes=" + (seg.getStartByte() + seg.getDownloadedBytes()) + "-" + seg.getEndByte();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(peer.getBaseUrl()))
                    .header("Range", range)
                    .GET()
                    .build();

            HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 206 && resp.statusCode() != 200) {
                throw new RuntimeException("HTTP error " + resp.statusCode());
            }

            long downloaded = seg.getDownloadedBytes();
            try (InputStream in = resp.body()) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    downloaded += r;
                    speedControl.throttle(r);
                    callbacks.onSegmentProgress(task.getId(), seg.getIndex(), downloaded);

                    // Demo stop conditions:
                    // in real app should check pause/stop flags
                    if (downloaded >= seg.getLength()) break;
                }
            }
        }

        callbacks.onCompleted(task.getId());
    }
}
