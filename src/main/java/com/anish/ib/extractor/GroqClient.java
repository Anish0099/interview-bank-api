package com.anish.ib.extractor;

import com.anish.ib.config.GroqProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class GroqClient {
    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final GroqProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public GroqClient(GroqProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean configured() { return !props.getApiKey().isBlank(); }

    /**
     * Returns the raw JSON string emitted by the model (JSON-object mode).
     * Throws on non-2xx.
     */
    public String completeJson(String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("temperature", 0);
        ObjectNode responseFormat = body.putObject("response_format");
        responseFormat.put("type", "json_object");
        ArrayNode messages = body.putArray("messages");
        ObjectNode sys = messages.addObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt);
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", userPrompt);

        HttpRequest req = HttpRequest.newBuilder(URI.create(props.getBaseUrl() + "/chat/completions"))
            .header("Authorization", "Bearer " + props.getApiKey())
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        // Free-tier TPM caps (8000/min for gpt-oss-20b) trigger 429 constantly.
        // Parse the "try again in Xs" hint and honour it; up to 5 attempts.
        for (int attempt = 1; attempt <= 5; attempt++) {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 429) {
                long waitMs = parseRetryAfter(res.body()).orElse(6000L);
                log.warn("Groq 429 (attempt {}/5) — sleeping {}ms", attempt, waitMs);
                Thread.sleep(waitMs);
                continue;
            }
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Groq " + res.statusCode() + ": " + res.body());
            }
            JsonNode json = mapper.readTree(res.body());
            return json.path("choices").path(0).path("message").path("content").asText();
        }
        throw new IllegalStateException("Groq: exhausted 5 retries on 429 rate-limit");
    }

    private static final java.util.regex.Pattern RETRY_AFTER =
        java.util.regex.Pattern.compile("try again in ([0-9.]+)s");

    private static java.util.Optional<Long> parseRetryAfter(String body) {
        var m = RETRY_AFTER.matcher(body);
        if (!m.find()) return java.util.Optional.empty();
        try {
            double secs = Double.parseDouble(m.group(1));
            return java.util.Optional.of((long) Math.ceil(secs * 1000) + 500);
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }
}
