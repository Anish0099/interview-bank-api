package com.anish.ib.scraper;

import com.anish.ib.domain.RawPost;
import com.anish.ib.repository.RawPostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Scrapes interview-experience articles from Dev.to.
 * Public REST API, no auth: https://developers.forem.com/api
 *
 *   GET /api/articles?tag=<tag>&per_page=100&page=<n>   → summary list
 *   GET /api/articles/{id}                              → full body_markdown
 *
 * Rate limit: ~30 requests / 30 seconds ("reasonable use"). We pace at 1 req/sec.
 */
@Component
public class DevToScraper {
    private static final Logger log = LoggerFactory.getLogger(DevToScraper.class);
    private static final String BASE = "https://dev.to/api";
    private static final String USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) "
        + "Chrome/131.0.0.0 Safari/537.36 interview-bank/1.0";
    private static final long DELAY_MS = 1100;
    private static final List<String> TAGS = List.of("interview", "interviewexperience", "interviewquestions");

    private static final String[] SIGNAL_TERMS = {
        "interview", "asked", "round", "question", "recruiter", "onsite", "screen"
    };

    private final RawPostRepository rawPostRepo;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public DevToScraper(RawPostRepository rawPostRepo, ObjectMapper mapper) {
        this.rawPostRepo = rawPostRepo;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public ScrapeResult scrape(String mode) throws Exception {
        int perTagPages = "backfill".equalsIgnoreCase(mode) ? 5 : 1;
        int seen = 0, inserted = 0, filtered = 0, failed = 0;
        for (String tag : TAGS) {
            for (int page = 1; page <= perTagPages; page++) {
                JsonNode arr;
                try {
                    arr = fetchList(tag, page);
                } catch (Exception e) {
                    log.warn("Dev.to list fetch failed (tag={} page={}): {}", tag, page, e.getMessage());
                    break;
                }
                if (!arr.isArray() || arr.isEmpty()) break;
                for (JsonNode summary : arr) {
                    seen++;
                    try {
                        if (persistOne(summary)) inserted++;
                        else filtered++;
                    } catch (Exception e) {
                        failed++;
                        log.warn("Dev.to persist failed for id={}: {}",
                            summary.path("id").asText(), e.getMessage());
                    }
                    Thread.sleep(DELAY_MS);
                }
            }
        }
        log.info("Dev.to scrape done: seen={} inserted={} filtered={} failed={}",
            seen, inserted, filtered, failed);
        return new ScrapeResult(seen, inserted, filtered, failed);
    }

    private JsonNode fetchList(String tag, int page) throws Exception {
        String url = BASE + "/articles?tag=" + tag + "&per_page=100&page=" + page;
        return fetchJson(url);
    }

    private JsonNode fetchArticle(long id) throws Exception {
        return fetchJson(BASE + "/articles/" + id);
    }

    private JsonNode fetchJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(20))
            .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) {
            log.warn("Dev.to 429 — sleeping 30s");
            Thread.sleep(30_000);
            return fetchJson(url);
        }
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Dev.to HTTP " + res.statusCode() + " for " + url);
        }
        return mapper.readTree(res.body());
    }

    @Transactional
    protected boolean persistOne(JsonNode summary) throws Exception {
        long id = summary.path("id").asLong();
        String sourceId = "devto-" + id;
        if (rawPostRepo.existsBySourceAndSourceId("devto", sourceId)) return false;

        String title = summary.path("title").asText("");
        String desc = summary.path("description").asText("");
        String url = summary.path("url").asText("");

        // Fetch full article for body_markdown — worth the extra call.
        JsonNode full = fetchArticle(id);
        String body = full.path("body_markdown").asText("");
        if (body.isBlank()) body = desc;
        String combined = (title + "\n\n" + body).toLowerCase(Locale.ROOT);

        int signals = 0;
        for (String s : SIGNAL_TERMS) if (combined.contains(s)) signals++;
        if (signals < 2 || body.length() < 300) {
            return false;
        }

        RawPost rp = new RawPost();
        rp.setSource("devto");
        rp.setSourceId(sourceId);
        rp.setSourceUrl(url);
        rp.setTitle(title);
        rp.setBody(body);
        rp.setRawJson(full);
        rp.setProcessed(false);
        rawPostRepo.save(rp);
        return true;
    }

    public record ScrapeResult(int seen, int inserted, int filtered, int failed) {}
}
