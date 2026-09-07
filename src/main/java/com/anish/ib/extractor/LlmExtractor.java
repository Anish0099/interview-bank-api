package com.anish.ib.extractor;

import com.anish.ib.extractor.dto.ExtractedExperience;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LlmExtractor {
    private static final Logger log = LoggerFactory.getLogger(LlmExtractor.class);

    private static final String SYSTEM_PROMPT =
        "You extract structured JSON. Never include prose.";

    private static final String USER_TEMPLATE = """
        You are extracting structured data from an interview experience post.
        Return ONLY valid JSON matching this schema:
        {
          "company": "canonical company name or null",
          "role": "role title as stated in the post",
          "seniority": "entry|mid|senior|lead|unknown",
          "outcome": "offer|rejected|ghosted|unknown",
          "interview_date": "YYYY-MM or null",
          "summary": "2-sentence summary of the interview experience",
          "rounds": [
            {
              "round_type": "screening|dsa|system_design|behavioral|hr|domain",
              "questions": [
                {
                  "question_text": "the question, cleaned up but technically accurate",
                  "difficulty": "easy|medium|hard|unknown",
                  "topics": ["lowercase", "hyphenated-topics"]
                }
              ]
            }
          ]
        }
        Rules:
        - If the post is not actually an interview experience, return {"company": null}
        - Do not invent details not present in the post
        - Preserve technical accuracy of questions verbatim where possible
        - Topics must be lowercase, hyphenated (e.g. "dynamic-programming", "system-design", "oop")
        - Round types must be exactly one of the enum values above

        Post title: %s
        Post body: %s
        """;

    private final GroqClient groq;
    private final ObjectMapper mapper;

    public LlmExtractor(GroqClient groq, ObjectMapper mapper) {
        this.groq = groq;
        this.mapper = mapper;
    }

    public Optional<ExtractedExperience> extract(String title, String body) {
        String userPrompt = USER_TEMPLATE.formatted(safe(title), safe(body));
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                String raw = groq.completeJson(SYSTEM_PROMPT, userPrompt);
                ExtractedExperience parsed = mapper.readValue(raw, ExtractedExperience.class);
                if (parsed.company() == null || parsed.company().isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(parsed);
            } catch (Exception e) {
                log.warn("LLM extract attempt {} failed: {}", attempt, e.getMessage());
                if (attempt == 2) throw new RuntimeException("LLM extraction failed after retry", e);
            }
        }
        return Optional.empty();
    }

    private static String safe(String s) {
        if (s == null) return "";
        // Trim runaway posts. 4000 chars keeps each request under ~1200 input tokens
        // so we stay inside Groq free tier's 8000 TPM cap without punishing retries.
        return s.length() > 4000 ? s.substring(0, 4000) : s;
    }
}
