package com.handson.searchengine.crawler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.handson.searchengine.kafka.Producer;
import com.handson.searchengine.model.*;
import com.handson.searchengine.util.ElasticSearch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;



@Service
public class Crawler {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    ObjectMapper om;

    @Autowired
    Producer producer;

    @Autowired
    ElasticSearch elasticSearch;
    protected final Log logger = LogFactory.getLog(getClass());


    public void crawl(String crawlId, CrawlerRequest crawlerRequest) throws InterruptedException, IOException {
        initCrawlInRedis(crawlId);
        producer.send(CrawlerRecord.of(crawlId, crawlerRequest));

    }

    public void crawlOneRecord(String crawlId, CrawlerRecord rec)
            throws IOException, InterruptedException {

        logger.info("crawling url: " + rec.getUrl());

        StopReason stopReason = getStopReason(rec);

        setCrawlStatus(
                crawlId,
                CrawlStatus.of(
                        rec.getDistance(),
                        rec.getStartTime(),
                        0,
                        stopReason
                )
        );

        if (stopReason != null) {
            logger.info("Crawl stopped. Reason: " + stopReason);
            return;
        }

        Document webPageContent = Jsoup.connect(rec.getUrl())
                .userAgent("Mozilla/5.0")
                .timeout(10_000)
                .followRedirects(true)
                .get();

        List<String> innerUrls =
                extractWebPageUrls(rec.getBaseUrl(), webPageContent);

        logger.info("Found " + innerUrls.size() + " internal URLs");

        addUrlsToQueue(
                rec,
                innerUrls,
                rec.getDistance() + 1
        );

        try {
            indexElasticSearch(rec, webPageContent);
        } catch (Exception e) {
            logger.error(
                    "Could not index page in Elasticsearch: " + rec.getUrl(),
                    e
            );
        }
    }

    private StopReason getStopReason(CrawlerRecord rec) {
        if (rec.getDistance() > rec.getMaxDistance()) {
            return StopReason.maxDistance;
        }

        if (getVisitedUrls(rec.getCrawlId()) >= rec.getMaxUrls()) {
            return StopReason.maxUrls;
        }

        if (System.currentTimeMillis() >= rec.getMaxTime()) {
            return StopReason.timeout;
        }

        return null;
    }


    private void addUrlsToQueue(
            CrawlerRecord rec,
            List<String> urls,
            int distance
    ) throws InterruptedException, JsonProcessingException {
        if (distance > rec.getMaxDistance()) {
            logger.info("Maximum depth reached.");

            setCrawlStatus(
                    rec.getCrawlId(),
                    CrawlStatus.of(
                            rec.getDistance(),
                            rec.getStartTime(),
                            0,
                            StopReason.maxDistance
                    )
            );

            return;
        }

        logger.info(
                ">> adding urls to queue: distance->"
                        + distance
                        + " amount->"
                        + urls.size()
        );

        for (String url : urls) {

            int currentCount = getVisitedUrls(rec.getCrawlId());

            if (currentCount >= rec.getMaxUrls()) {
                logger.info(
                        "Maximum URL limit reached. Current count: "
                                + currentCount
                                + ", maximum: "
                                + rec.getMaxUrls()
                );
                break;
            }

            if (!crawlHasVisited(rec, url)) {
                producer.send(
                        CrawlerRecord.of(rec)
                                .withUrl(url)
                                .withIncDistance()
                );
            }
        }
    }

    private List<String> extractWebPageUrls(String baseUrl, Document webPageContent) {
        List<String> links = webPageContent.select("a[href]")
                .eachAttr("abs:href")
                .stream()
                .filter(url -> url != null && !url.isBlank())
                .filter(url -> url.startsWith(baseUrl))
                .map(url -> url.split("#")[0])
                .distinct()
                .collect(Collectors.toList());
        logger.info(">> extracted->" + links.size() + " links");

        return links;
    }

    private void indexElasticSearch(CrawlerRecord rec, Document webPageContent) {
        logger.info(">> adding elastic search for webPage: " + rec.getUrl());

        String title = webPageContent.title();
        String bodyText = webPageContent.body() != null
                ? webPageContent.body().text()
                : "";

        String text = (title + " " + bodyText)
                .replaceAll("\\s+", " ")
                .trim();

        UrlSearchDoc searchDoc = UrlSearchDoc.of(
                rec.getCrawlId(),
                text,
                rec.getUrl(),
                rec.getBaseUrl(),
                rec.getDistance()
        );

        elasticSearch.addData(searchDoc);
    }

    private void initCrawlInRedis(String crawlId) throws JsonProcessingException {
        setCrawlStatus(crawlId, CrawlStatus.of(0, System.currentTimeMillis(),0,  null));
        redisTemplate.opsForValue().set(crawlId + ".urls.count","1");
    }
    private void setCrawlStatus(String crawlId, CrawlStatus crawlStatus) throws JsonProcessingException {
        redisTemplate.opsForValue().set(crawlId + ".status", om.writeValueAsString(crawlStatus));
    }

    private boolean crawlHasVisited(CrawlerRecord rec, String url) {

        String visitedKey = rec.getCrawlId() + ".urls." + url;
        String countKey = rec.getCrawlId() + ".urls.count";

        Boolean wasAdded = redisTemplate
                .opsForValue()
                .setIfAbsent(visitedKey, "1");

        if (!Boolean.TRUE.equals(wasAdded)) {
            return true;
        }

        Long newCount = redisTemplate
                .opsForValue()
                .increment(countKey, 1L);

        if (newCount != null && newCount > rec.getMaxUrls()) {
            redisTemplate.delete(visitedKey);
            redisTemplate
                    .opsForValue()
                    .decrement(countKey, 1L);

            return true;
        }

        return false;
    }

    private int getVisitedUrls(String crawlId) {
        Object curCount = redisTemplate.opsForValue().get(crawlId + ".urls.count");
        if (curCount == null) return 0;
        return Integer.parseInt(curCount.toString());
    }

    public CrawlStatusOut getCrawlInfo(String crawlId) throws JsonProcessingException {
        CrawlStatus cs = om.readValue(redisTemplate.opsForValue().get(crawlId + ".status").toString(),CrawlStatus.class);
        cs.setNumPages(getVisitedUrls(crawlId));
        return CrawlStatusOut.of(cs);
    }
}
