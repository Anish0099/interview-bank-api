package com.anish.ib.extractor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedExperience(
    String company,
    String role,
    String seniority,
    String outcome,
    String interview_date,
    String summary,
    List<Round> rounds
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Round(String round_type, List<Question> questions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Question(String question_text, String difficulty, List<String> topics) {}
}
