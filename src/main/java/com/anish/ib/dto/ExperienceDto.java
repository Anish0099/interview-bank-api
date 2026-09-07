package com.anish.ib.dto;

import java.time.LocalDate;

public record ExperienceDto(
    Long id,
    String seniority,
    String outcome,
    LocalDate interviewDate,
    String summary,
    String sourceUrl,
    String roleSlug,
    String roleName
) {}
