package org.example.segment;


import org.example.model.DownloadSegment;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

// Segment manager that can provide Iterator
public class SegmentManager implements Iterable<DownloadSegment> {

    private final List<DownloadSegment> segments = new ArrayList<>();

    public void setSegments(List<DownloadSegment> newSegments) {
        segments.clear();
        segments.addAll(newSegments);
    }

    public List<DownloadSegment> getSegments() {
        return new ArrayList<>(segments);
    }

    @Override
    public Iterator<DownloadSegment> iterator() {
        return new SegmentIterator(segments);
    }
}
