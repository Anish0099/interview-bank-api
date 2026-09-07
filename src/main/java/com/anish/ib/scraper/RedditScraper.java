package com.anish.ib.scraper;

import com.anish.ib.config.RedditProperties;
import com.anish.ib.domain.RawPost;
import com.anish.ib.repository.RawPostRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Component
public class RedditScraper {
    private static final Logger log = LoggerFactory.getLogger(RedditScraper.class);
    private static final String SEARCH_QUERY = "interview experience";
    private static final String[] MUST_MATCH_ONE = {
        "interview", "rounds"
    };
    private static final String[] SIGNAL_TERMS = {
        "experience", "questions asked", "rounds", "was asked", "asked me"
    };

    private final RedditClient client;
    private final RawPostRepository rawPostRepo;
    private final RedditProperties props;

    public RedditScraper(RedditClient client, RawPostRepository rawPostRepo, RedditProperties props) {
        this.client = client;
        this.rawPostRepo = rawPostRepo;
        this.props = props;
    }

    /**
     * mode: "recent" → t=month, single page per subreddit.
     *       "backfill" → t=year, paginate up to 5 pages.
     */
    public ScrapeResult scrapeAll(String mode) throws Exception {
        if (!client.configured()) {
            log.warn("Reddit credentials missing; skipping scrape.");
            return new ScrapeResult(0, 0, 0);
        }
        String time = "backfill".equalsIgnoreCase(mode) ? "year" : "month";
        int pages = "backfill".equalsIgnoreCase(mode) ? 5 : 1;

        int seen = 0, inserted = 0, filtered = 0;
        for (String subreddit : props.getSubreddits().split(",")) {
            String sub = subreddit.trim();
            if (sub.isEmpty()) continue;
            String after = null;
            for (int page = 0; page < pages; page++) {
                JsonNode payload = client.search(sub, SEARCH_QUERY, time, after);
                JsonNode data = payload.path("data");
                JsonNode children = data.path("children");
                if (!children.isArray() || children.isEmpty()) break;
                for (JsonNode child : children) {
                    seen++;
                    JsonNode post = child.path("data");
                    if (!isLikelyInterviewExperience(post)) {
                        filtered++;
                        continue;
                    }
                    if (persist(post)) inserted++;
                }
                after = data.path("after").isTextual() ? data.get("after").asText() : null;
                if (after == null || after.isBlank()) break;
                // polite delay between paginated calls
                Thread.sleep(1500);
            }
        }
        log.info("Reddit scrape done: seen={} inserted={} filtered_out={}", seen, inserted, filtered);
        return new ScrapeResult(seen, inserted, filtered);
    }

    private boolean isLikelyInterviewExperience(JsonNode post) {
        String title = safe(post.path("title").asText("")).toLowerCase(Locale.ROOT);
        String body = safe(post.path("selftext").asText("")).toLowerCase(Locale.ROOT);
        String combined = title + "\n" + body;
        boolean mustMatch = false;
        for (String t : MUST_MATCH_ONE) {
            if (combined.contains(t)) { mustMatch = true; break; }
        }
        if (!mustMatch) return false;
        int signals = 0;
        for (String s : SIGNAL_TERMS) {
            if (combined.contains(s)) signals++;
        }
        return signals >= 1 && body.length() > 200;
    }

    @Transactional
    protected boolean persist(JsonNode post) {
        String id = post.path("id").asText();
        if (id.isBlank()) return false;
        if (rawPostRepo.existsBySourceAndSourceId("reddit", id)) return false;
        RawPost rp = new RawPost();
        rp.setSource("reddit");
        rp.setSourceId(id);
        String permalink = post.path("permalink").asText("");
        rp.setSourceUrl(permalink.isBlank() ? post.path("url").asText("") : "https://reddit.com" + permalink);
        rp.setTitle(safe(post.path("title").asText("")));
        rp.setBody(safe(post.path("selftext").asText("")));
        rp.setRawJson(post);
        rp.setProcessed(false);
        rawPostRepo.save(rp);
        return true;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public record ScrapeResult(int seen, int inserted, int filtered) {}
}
