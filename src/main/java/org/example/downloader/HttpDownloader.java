package org.example.downloader;

import org.example.core.DownloadControl;
import org.example.model.DownloadSegment;
import org.example.model.DownloadTask;
import org.example.model.SegmentStatus;
import org.example.segment.Peer;
import org.example.speed.SpeedControl;

import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class HttpDownloader extends AbstractDownloader {

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public long probeContentLength(String url) {
        try {
            HttpRequest head = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> resp =
                    client.send(head, HttpResponse.BodyHandlers.discarding());

            return resp.headers()
                    .firstValue("Content-Length")
                    .map(Long::parseLong)
                    .orElse(-1L);
        } catch (Exception e) {
            return -1L;
        }
    }
    public String detectExtensionByHead(String url) {
        try {
            HttpRequest head = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> resp =
                    client.send(head, HttpResponse.BodyHandlers.discarding());

            // ===== 1. Content-Disposition =====
            String cd = resp.headers()
                    .firstValue("Content-Disposition")
                    .orElse("");

            if (!cd.isBlank()) {
                int idx = cd.toLowerCase().lastIndexOf("filename=");
                if (idx >= 0) {
                    String name = cd.substring(idx + 9)
                            .replace("\"", "")
                            .trim();
                    int dot = name.lastIndexOf('.');
                    if (dot > 0 && dot < name.length() - 1) {
                        return name.substring(dot + 1).toLowerCase();
                    }
                }
            }

            // ===== 2. Content-Type =====
            String ct = resp.headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .toLowerCase();

            if (ct.contains("image/jpeg") || ct.contains("image/jpg")) return "jpg";
            if (ct.contains("image/png")) return "png";
            if (ct.contains("image/gif")) return "gif";

            if (ct.contains("video/mp4")) return "mp4";
            if (ct.contains("video/webm")) return "webm";
            if (ct.contains("video/quicktime")) return "mov";

            if (ct.contains("application/pdf")) return "pdf";
            if (ct.contains("application/zip")) return "zip";


            int q = url.indexOf('?');
            String clean = q > 0 ? url.substring(0, q) : url;

            int dot = clean.lastIndexOf('.');
            if (dot > 0 && dot < clean.length() - 1) {
                return clean.substring(dot + 1).toLowerCase();
            }

        } catch (Exception ignored) {}

        return "bin";
    }



    @Override
    protected void doDownload(
            DownloadTask task,
            List<DownloadSegment> segments,
            List<Peer> peers,
            SpeedControl speed,
            DownloadControl control,
            DownloadCallbacks cb
    ) throws Exception {



        for (DownloadSegment seg : segments) {

            if (control.isCancelled()) return;
            if (seg.getStatus() == SegmentStatus.COMPLETED) continue;

            long from = seg.getStartByte() + seg.getDownloadedBytes();
            long to   = seg.getEndByte();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(task.getUrl()))
                    .header("Range", "bytes=" + from + "-" + to)
                    .GET()
                    .build();

            HttpResponse<InputStream> resp =
                    client.send(req, HttpResponse.BodyHandlers.ofInputStream());

            if (resp.statusCode() != 206 && resp.statusCode() != 200) {
                throw new RuntimeException("HTTP " + resp.statusCode());
            }

            Path tmpFile = Path.of(task.getFileName() + ".bin");

            try (InputStream in = resp.body();
                 RandomAccessFile raf = new RandomAccessFile(tmpFile.toFile(), "rw")) {

                raf.seek(from);

                byte[] buf = new byte[8192];
                int read;

                while ((read = in.read(buf)) != -1) {

                    if (control.isPaused()) {
                        control.awaitResume();
                    }
                    if (control.isCancelled()) return;

                    raf.write(buf, 0, read);
                    speed.throttle(read);

                    seg.setDownloadedBytes(
                            seg.getDownloadedBytes() + read
                    );

                    cb.onSegmentProgress(
                            task.getId(),
                            seg.getIndex(),
                            seg.getDownloadedBytes()
                    );
                }
            }

            seg.setStatus(SegmentStatus.COMPLETED);
        }

        cb.onCompleted(task.getId());
    }


    private void singleStreamDownload(DownloadTask task,
                                      Path outPath,
                                      SpeedControl speedControl,
                                      DownloadControl control,
                                      DownloadCallbacks callbacks) throws Exception {

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(task.getUrl()))
                .GET()
                .build();

        HttpResponse<InputStream> resp =
                client.send(req, HttpResponse.BodyHandlers.ofInputStream());

        try (InputStream in = resp.body();
             RandomAccessFile raf = new RandomAccessFile(outPath.toFile(), "rw")) {

            byte[] buf = new byte[8192];
            int read;
            long downloaded = 0;

            while ((read = in.read(buf)) != -1) {

                if (control.isCancelled()) return;
                if (control.isPaused()) {
                    control.awaitResume();
                }


                raf.write(buf, 0, read);
                downloaded += read;

                speedControl.throttle(read);

                callbacks.onSegmentProgress(task.getId(), 0, downloaded);
            }
        }
    }
}
