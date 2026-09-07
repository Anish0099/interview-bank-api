package com.anish.ib.api;

import com.anish.ib.dto.SitemapPayload;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/sitemap-data")
public class SitemapDataController {

    private final JdbcTemplate jdbc;

    public SitemapDataController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Cacheable("sitemap-data")
    public SitemapPayload payload() {
        List<String> companies = jdbc.query("SELECT slug FROM companies ORDER BY slug",
            (rs, i) -> rs.getString(1));
        List<String> roles = jdbc.query("SELECT slug FROM roles ORDER BY slug",
            (rs, i) -> rs.getString(1));
        List<String> questions = jdbc.query("SELECT slug FROM questions ORDER BY created_at DESC LIMIT 50000",
            (rs, i) -> rs.getString(1));
        List<SitemapPayload.CompanyRole> combos = jdbc.query("""
            SELECT DISTINCT c.slug AS company_slug, r.slug AS role_slug
            FROM experiences e
            JOIN companies c ON c.id = e.company_id
            JOIN roles r ON r.id = e.role_id
            ORDER BY c.slug, r.slug
            """, (rs, i) -> new SitemapPayload.CompanyRole(rs.getString("company_slug"), rs.getString("role_slug")));
        OffsetDateTime last = jdbc.queryForObject(
            "SELECT COALESCE(MAX(created_at), NOW()) FROM questions",
            OffsetDateTime.class);
        return new SitemapPayload(companies, roles, questions, combos,
            last == null ? OffsetDateTime.now() : last);
    }
}
