package com.anish.ib.dto;

import java.util.List;

public record CompanyDto(
    Long id,
    String slug,
    String canonicalName,
    String industry,
    String logoUrl,
    String description,
    Long questionCount,
    List<String> topTopics,
    List<ExperienceDto> recentExperiences,
    List<RoleWithCount> roles
) {
    public record RoleWithCount(String slug, String canonicalName, long questionCount) {}

    public static CompanyDto light(Long id, String slug, String canonicalName, String industry,
                                   String logoUrl, String description, Long questionCount) {
        return new CompanyDto(id, slug, canonicalName, industry, logoUrl, description,
            questionCount, List.of(), List.of(), List.of());
    }
}
