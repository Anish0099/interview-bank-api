package com.anish.ib.scraper;

import com.anish.ib.domain.RawPost;
import com.anish.ib.repository.RawPostRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes GeeksforGeeks interview experiences.
 * Strategy: fetch the top-level sitemap index, follow child sitemaps that
 * mention /interview-experiences/ and collect matching article URLs.
 */
@Component
public class GfgScraper {
    private static final Logger log = LoggerFactory.getLogger(GfgScraper.class);
    private static final String SITEMAP_INDEX = "https://www.geeksforgeeks.org/sitemap.xml";
    private static final String USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
        + "Chrome/131.0.0.0 Safari/537.36";
    private static final long DELAY_MS = 2000;

    private final RawPostRepository rawPostRepo;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GfgScraper(RawPostRepository rawPostRepo, ObjectMapper mapper) {
        this.rawPostRepo = rawPostRepo;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public ScrapeResult scrape(int maxArticles) throws Exception {
        int seen = 0, inserted = 0, failed = 0;
        List<String> articleUrls;
        try {
            articleUrls = discoverArticleUrls(maxArticles);
        } catch (Exception e) {
            log.warn("GFG discovery failed ({}). GFG is Cloudflare-fronted and blocks many "
                + "data-center IP ranges (GitHub Actions runners included). Skipping GFG for "
                + "this run — try again from a residential IP.", e.getMessage());
            return new ScrapeResult(0, 0, 0);
        }
        log.info("GFG discovery: {} candidate interview-experience URLs", articleUrls.size());
        for (String url : articleUrls) {
            seen++;
            try {
                if (persist(url)) inserted++;
            } catch (Exception e) {
                failed++;
                log.warn("GFG fetch failed for {}: {}", url, e.getMessage());
            }
            Thread.sleep(DELAY_MS);
        }
        log.info("GFG scrape done: seen={} inserted={} failed={}", seen, inserted, failed);
        return new ScrapeResult(seen, inserted, failed);
    }

    private List<String> discoverArticleUrls(int max) throws Exception {
        List<String> out = new ArrayList<>();
        String indexXml = fetchText(SITEMAP_INDEX);
        Document indexDoc = Jsoup.parse(indexXml, "", org.jsoup.parser.Parser.xmlParser());
        for (Element loc : indexDoc.select("sitemap > loc")) {
            String childUrl = loc.text();
            if (!childUrl.toLowerCase().contains("interview")) continue;
            try {
                String childXml = fetchText(childUrl);
                Document childDoc = Jsoup.parse(childXml, "", org.jsoup.parser.Parser.xmlParser());
                for (Element urlLoc : childDoc.select("url > loc")) {
                    String u = urlLoc.text();
                    if (u.contains("/interview-experiences/")) {
                        out.add(u);
                        if (out.size() >= max) return out;
                    }
                }
            } catch (Exception e) {
                log.warn("GFG child sitemap {} failed: {}", childUrl, e.getMessage());
            }
            Thread.sleep(DELAY_MS);
        }
        return out;
    }

    @Transactional
    protected boolean persist(String url) throws Exception {
        String sourceId = sourceIdFrom(url);
        if (rawPostRepo.existsBySourceAndSourceId("gfg", sourceId)) return false;

        String html = fetchText(url);
        Document doc = Jsoup.parse(html, url);
        String title = doc.selectFirst("h1") == null ? doc.title() : doc.selectFirst("h1").text();
        Element article = doc.selectFirst("article") == null ? doc.selectFirst("main") : doc.selectFirst("article");
        String body = article == null ? doc.body().text() : article.text();

        if (body == null || body.length() < 300) return false;

        RawPost rp = new RawPost();
        rp.setSource("gfg");
        rp.setSourceId(sourceId);
        rp.setSourceUrl(url);
        rp.setTitle(title);
        rp.setBody(body);
        ObjectNode meta = mapper.createObjectNode();
        meta.put("source", "geeksforgeeks");
        meta.put("url", url);
        rp.setRawJson(meta);
        rp.setProcessed(false);
        rawPostRepo.save(rp);
        return true;
    }

    private String fetchText(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Sec-Fetch-Dest", "document")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "none")
            .header("Sec-Fetch-User", "?1")
            .header("Upgrade-Insecure-Requests", "1")
            .timeout(Duration.ofSeconds(20))
            .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("GFG HTTP " + res.statusCode() + " for " + url);
        }
        return res.body();
    }

    private static String sourceIdFrom(String url) {
        String u = url.replaceAll("^https?://(www\\.)?geeksforgeeks\\.org/", "");
        return u.replaceAll("/+$", "");
    }

    public record ScrapeResult(int seen, int inserted, int failed) {}
}
