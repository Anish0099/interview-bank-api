package com.anish.ib.dto;

import java.time.OffsetDateTime;

public record StatsDto(
    long companies,
    long roles,
    long questions,
    long experiences,
    OffsetDateTime lastUpdated
) {}
