package com.handson.searchengine.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.model.SearchResponse;
import com.handson.searchengine.model.SearchResult;
import com.handson.searchengine.model.UrlSearchDoc;
import okhttp3.*;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class ElasticSearch {
    private final OkHttpClient client = new OkHttpClient();

    @Value("${elasticsearch.base.url}")
    private String elasticSearchUrl;

    @Value("${elasticsearch.key}")
    private String apiKey;

    @Value("${elasticsearch.index}")
    private String index;

    @Autowired
    private ObjectMapper objectMapper;

    public void addData(UrlSearchDoc doc) {
        try {
            String content = objectMapper.writeValueAsString(doc);
            RequestBody body = RequestBody.create(MediaType.parse("application/json"), content);
            Request request = requestBuilder(elasticSearchUrl + "/" + index + "/doc")
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("Elasticsearch indexing failed: HTTP " + response.code());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to index page in Elasticsearch", e);
        }
    }

    public SearchResponse search(String query, int size) throws IOException {
        com.fasterxml.jackson.databind.node.ObjectNode multiMatch = objectMapper.createObjectNode();
        multiMatch.put("query", query);
        multiMatch.putArray("fields")
                .add("content^3")
                .add("url^2")
                .add("baseUrl");

        com.fasterxml.jackson.databind.node.ObjectNode queryNode = objectMapper.createObjectNode();
        queryNode.set("multi_match", multiMatch);

        com.fasterxml.jackson.databind.node.ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.set("query", queryNode);
        String json = requestNode.toString();

        RequestBody body = RequestBody.create(MediaType.parse("application/json"), json);
        Request request = requestBuilder(elasticSearchUrl + "/" + index + "/_search?size=" + size)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Elasticsearch search failed: HTTP " + response.code());
            }

            JsonNode root = objectMapper.readTree(response.body().string());
            long took = root.path("took").asLong(0);
            JsonNode totalNode = root.path("hits").path("total");
            long total = totalNode.isObject() ? totalNode.path("value").asLong(0) : totalNode.asLong(0);
            List<SearchResult> results = new ArrayList<>();

            for (JsonNode hit : root.path("hits").path("hits")) {
                JsonNode source = hit.path("_source");
                String url = source.path("url").asText("");
                String content = source.path("content").asText("");
                String baseUrl = source.path("baseUrl").asText("");
                String crawlId = source.path("crawlId").asText("");
                int level = source.path("level").asInt(0);
                double score = hit.path("_score").asDouble(0);

                results.add(new SearchResult(
                        buildTitle(url),
                        url,
                        baseUrl,
                        buildSnippet(content, query),
                        level,
                        crawlId,
                        score
                ));
            }

            return new SearchResponse(query, total, took, results);
        }
    }

    private Request.Builder requestBuilder(String url) {
        String auth = new String(Base64.encodeBase64(apiKey.getBytes()));
        return new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader(HttpHeaders.AUTHORIZATION, "Basic " + auth);
    }

    private String buildTitle(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? url : uri.getHost().replaceFirst("^www\\.", "");
            String path = uri.getPath();
            if (path == null || path.equals("/") || path.isBlank()) {
                return host;
            }
            String lastPart = path.substring(path.lastIndexOf('/') + 1)
                    .replace('-', ' ')
                    .replace('_', ' ')
                    .trim();
            return lastPart.isEmpty() ? host : capitalize(lastPart);
        } catch (Exception ignored) {
            return url;
        }
    }

    private String buildSnippet(String content, String query) {
        if (content == null || content.isBlank()) {
            return "Indexed page from the crawler.";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        int index = normalized.toLowerCase().indexOf(query.toLowerCase());
        int start = index < 0 ? 0 : Math.max(0, index - 90);
        int end = Math.min(normalized.length(), start + 240);
        String snippet = normalized.substring(start, end);
        return (start > 0 ? "…" : "") + snippet + (end < normalized.length() ? "…" : "");
    }

    private String capitalize(String value) {
        if (value.isEmpty()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
