package com.anish.ib.extractor;

import com.anish.ib.config.GeminiProperties;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class EmbeddingClient {
    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta";
    private static final int BATCH = 100;

    private final GeminiProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;

    public EmbeddingClient(GeminiProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public boolean configured() { return !props.getApiKey().isBlank(); }

    /**
     * Batch-embed up to 100 texts per call using batchEmbedContents.
     * Returns embeddings in the same order as input; retries with exponential backoff on 429s.
     */
    public List<float[]> embedAll(List<String> texts) throws Exception {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i += BATCH) {
            List<String> chunk = texts.subList(i, Math.min(texts.size(), i + BATCH));
            out.addAll(embedBatch(chunk));
        }
        return out;
    }

    private List<float[]> embedBatch(List<String> texts) throws Exception {
        String url = BASE + "/models/" + props.getEmbeddingModel() + ":batchEmbedContents?key=" + props.getApiKey();
        ObjectNode body = mapper.createObjectNode();
        ArrayNode requests = body.putArray("requests");
        for (String text : texts) {
            ObjectNode req = requests.addObject();
            req.put("model", "models/" + props.getEmbeddingModel());
            ObjectNode content = req.putObject("content");
            ArrayNode parts = content.putArray("parts");
            parts.addObject().put("text", text);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        long backoff = 1000L;
        for (int attempt = 0; attempt < 5; attempt++) {
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 429 || res.statusCode() >= 500) {
                log.warn("Gemini {} — retrying in {}ms", res.statusCode(), backoff);
                Thread.sleep(backoff);
                backoff = Math.min(backoff * 2, 30_000L);
                continue;
            }
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Gemini " + res.statusCode() + ": " + res.body());
            }
            return parseEmbeddings(res.body());
        }
        throw new IllegalStateException("Gemini batchEmbedContents exhausted retries");
    }

    private List<float[]> parseEmbeddings(String body) throws Exception {
        JsonNode root = mapper.readTree(body);
        JsonNode arr = root.path("embeddings");
        List<float[]> out = new ArrayList<>(arr.size());
        for (JsonNode e : arr) {
            JsonNode values = e.path("values");
            float[] v = new float[values.size()];
            for (int i = 0; i < v.length; i++) v[i] = (float) values.get(i).asDouble();
            out.add(v);
        }
        return out;
    }
}
