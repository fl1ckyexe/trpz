package org.example.segment;


import  org.example.model.DownloadSegment;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

// Iterator pattern
public class SegmentIterator implements Iterator<DownloadSegment> {

    private final List<DownloadSegment> segments;
    private int index = 0;

    public SegmentIterator(List<DownloadSegment> segments) {
        this.segments = segments;
    }

    @Override
    public boolean hasNext() {
        return index < segments.size();
    }

    @Override
    public DownloadSegment next() {
        if (!hasNext()) throw new NoSuchElementException();
        return segments.get(index++);
    }
}
