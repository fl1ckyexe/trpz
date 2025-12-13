package org.example.segment;

public class HttpRangePeer implements Peer {

    private final String id;
    private final String baseUrl;

    public HttpRangePeer(String id, String baseUrl) {
        this.id = id;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getId() { return id; }

    @Override
    public String getBaseUrl() { return baseUrl; }

    @Override
    public String toString() {
        return "HttpRangePeer{id='" + id + "', baseUrl='" + baseUrl + "'}";
    }
}
