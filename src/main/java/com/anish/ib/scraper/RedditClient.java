package com.anish.ib.scraper;

import com.anish.ib.config.RedditProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Minimal Reddit API client.
 * Auth: script/app credentials → OAuth token via client_credentials.
 * Read: oauth.reddit.com/r/{sub}/search?q=...&sort=new&limit=100&t=month
 */
@Component
public class RedditClient {
    private static final Logger log = LoggerFactory.getLogger(RedditClient.class);
    private static final String TOKEN_URL = "https://www.reddit.com/api/v1/access_token";
    private static final String OAUTH_BASE = "https://oauth.reddit.com";

    private final RedditProperties props;
    private final HttpClient http;
    private final ObjectMapper mapper;

    private String accessToken;
    private Instant tokenExpiresAt = Instant.EPOCH;

    public RedditClient(RedditProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public boolean configured() {
        return !props.getClientId().isBlank() && !props.getClientSecret().isBlank();
    }

    private synchronized String token() throws Exception {
        if (accessToken != null && Instant.now().isBefore(tokenExpiresAt.minusSeconds(30))) {
            return accessToken;
        }
        if (!configured()) {
            throw new IllegalStateException("Reddit credentials not configured (REDDIT_CLIENT_ID/SECRET).");
        }
        String basic = Base64.getEncoder()
            .encodeToString((props.getClientId() + ":" + props.getClientSecret()).getBytes());
        HttpRequest req = HttpRequest.newBuilder(URI.create(TOKEN_URL))
            .header("Authorization", "Basic " + basic)
            .header("User-Agent", props.getUserAgent())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
            .timeout(Duration.ofSeconds(15))
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Reddit token failed: " + res.statusCode() + " " + res.body());
        }
        JsonNode json = mapper.readTree(res.body());
        accessToken = json.get("access_token").asText();
        long expiresIn = json.has("expires_in") ? json.get("expires_in").asLong() : 3600;
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn);
        return accessToken;
    }

    public JsonNode search(String subreddit, String query, String time, String after) throws Exception {
        StringBuilder url = new StringBuilder(OAUTH_BASE)
            .append("/r/").append(subreddit)
            .append("/search?q=").append(URLEncode(query))
            .append("&restrict_sr=1")
            .append("&sort=new")
            .append("&limit=100")
            .append("&t=").append(time);
        if (after != null && !after.isBlank()) {
            url.append("&after=").append(after);
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url.toString()))
            .header("Authorization", "Bearer " + token())
            .header("User-Agent", props.getUserAgent())
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) {
            log.warn("Reddit rate-limit hit on r/{}; sleeping 10s", subreddit);
            Thread.sleep(10_000);
            return search(subreddit, query, time, after);
        }
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Reddit search failed: " + res.statusCode() + " " + res.body());
        }
        return mapper.readTree(res.body());
    }

    public Optional<JsonNode> fetchPost(String permalink) throws Exception {
        String url = OAUTH_BASE + permalink + ".json?raw_json=1";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer " + token())
            .header("User-Agent", props.getUserAgent())
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return Optional.empty();
        return Optional.of(mapper.readTree(res.body()));
    }

    private static String URLEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
