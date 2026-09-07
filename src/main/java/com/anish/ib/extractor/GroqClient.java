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
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 429) {
            log.warn("Groq 429 — sleeping 5s and retrying once");
            Thread.sleep(5000);
            res = http.send(req, HttpResponse.BodyHandlers.ofString());
        }
        if (res.statusCode() / 100 != 2) {
            throw new IllegalStateException("Groq " + res.statusCode() + ": " + res.body());
        }
        JsonNode json = mapper.readTree(res.body());
        return json.path("choices").path(0).path("message").path("content").asText();
    }
}
