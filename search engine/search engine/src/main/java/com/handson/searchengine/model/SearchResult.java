package com.handson.searchengine.model;

public class SearchResult {
    private String title;
    private String url;
    private String baseUrl;
    private String snippet;
    private int level;
    private String crawlId;
    private double score;

    public SearchResult() {
    }

    public SearchResult(String title, String url, String baseUrl, String snippet,
                        int level, String crawlId, double score) {
        this.title = title;
        this.url = url;
        this.baseUrl = baseUrl;
        this.snippet = snippet;
        this.level = level;
        this.crawlId = crawlId;
        this.score = score;
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getBaseUrl() { return baseUrl; }
    public String getSnippet() { return snippet; }
    public int getLevel() { return level; }
    public String getCrawlId() { return crawlId; }
    public double getScore() { return score; }
}
