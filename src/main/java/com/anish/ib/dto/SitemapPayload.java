package com.anish.ib.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SitemapPayload(
    List<String> companies,
    List<String> roles,
    List<String> questions,
    List<CompanyRole> companyRoles,
    OffsetDateTime lastUpdated
) {
    public record CompanyRole(String company, String role) {}
}
