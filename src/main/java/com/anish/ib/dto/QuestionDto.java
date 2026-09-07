package com.anish.ib.dto;

import java.util.List;

public record QuestionDto(
    Long id,
    String slug,
    String questionText,
    String roundType,
    String difficulty,
    List<String> topics,
    CompanyRef company,
    RoleRef role,
    String sourceUrl,
    Double score
) {
    public record CompanyRef(String slug, String canonicalName) {}
    public record RoleRef(String slug, String canonicalName) {}
}
