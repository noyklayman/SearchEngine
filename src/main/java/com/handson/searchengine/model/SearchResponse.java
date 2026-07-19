package com.handson.searchengine.model;

import java.util.List;

public class SearchResponse {
    private final String query;
    private final long total;
    private final long tookMs;
    private final List<SearchResult> results;

    public SearchResponse(String query, long total, long tookMs, List<SearchResult> results) {
        this.query = query;
        this.total = total;
        this.tookMs = tookMs;
        this.results = results;
    }

    public String getQuery() { return query; }
    public long getTotal() { return total; }
    public long getTookMs() { return tookMs; }
    public List<SearchResult> getResults() { return results; }
}
