package com.handson.searchengine.controller;


import com.handson.searchengine.crawler.Crawler;
import com.handson.searchengine.kafka.Producer;
import com.handson.searchengine.model.CrawlStatus;
import com.handson.searchengine.model.CrawlStatusOut;
import com.handson.searchengine.model.CrawlerRequest;
import com.handson.searchengine.model.SearchResponse;
import com.handson.searchengine.util.ElasticSearch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Random;

@RestController
@RequestMapping("/api")
public class AppController {

    private static final int ID_LENGTH = 6;
    private Random random = new Random();
    @Autowired
    Crawler crawler;

    @Autowired
    Producer producer;

    @Autowired
    ElasticSearch elasticSearch;
    @RequestMapping(value = "/crawl", method = RequestMethod.POST)
    public String crawl(@RequestBody CrawlerRequest request) throws IOException, InterruptedException {
        String crawlId = generateCrawlId();
        if (!request.getUrl().startsWith("http")) {
            request.setUrl("https://" + request.getUrl());
        }
        new Thread(()-> {
            try {
                crawler.crawl(crawlId, request);
            }catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        return crawlId;
    }

    @RequestMapping(value = "/crawl/{crawlId}", method = RequestMethod.GET)
    public CrawlStatusOut getCrawl(@PathVariable String crawlId) throws IOException, InterruptedException {
        return crawler.getCrawlInfo(crawlId);
    }

    private String generateCrawlId() {
        String charPool = "ABCDEFHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < ID_LENGTH; i++) {
            res.append(charPool.charAt(random.nextInt(charPool.length())));
        }
        return res.toString();
    }


    @GetMapping("/search")
    public SearchResponse search(@RequestParam String query,
                                 @RequestParam(defaultValue = "20") int size) throws IOException {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.isEmpty()) {
            return new SearchResponse("", 0, 0, java.util.Collections.emptyList());
        }
        int safeSize = Math.max(1, Math.min(size, 50));
        return elasticSearch.search(cleanQuery, safeSize);
    }

    @RequestMapping(value = "/sendKafka", method = RequestMethod.POST)
    public String sendKafka(@RequestBody CrawlerRequest request) throws IOException, InterruptedException {
        producer.send(request);
        return "OK";
    }
}
