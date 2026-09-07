package com.anish.ib.api;

import com.anish.ib.dto.CompanyDto;
import com.anish.ib.dto.ExperienceDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api/companies")
public class CompaniesController {

    private final JdbcTemplate jdbc;

    public CompaniesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Cacheable(value = "companies-list", key = "#limit + ':' + #offset")
    public Map<String, Object> list(@RequestParam(defaultValue = "60") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        int lim = Math.min(Math.max(limit, 1), 200);
        int off = Math.max(offset, 0);
        List<CompanyDto> items = jdbc.query("""
            SELECT c.id, c.slug, c.canonical_name, c.industry, c.logo_url, c.description,
                   COALESCE(cnt.n, 0) AS question_count
            FROM companies c
            LEFT JOIN (
              SELECT e.company_id, COUNT(q.id) AS n
              FROM experiences e JOIN questions q ON q.experience_id = e.id
              GROUP BY e.company_id
            ) cnt ON cnt.company_id = c.id
            ORDER BY question_count DESC, c.canonical_name ASC
            LIMIT ? OFFSET ?
            """, (rs, i) -> CompanyDto.light(
                rs.getLong("id"),
                rs.getString("slug"),
                rs.getString("canonical_name"),
                rs.getString("industry"),
                rs.getString("logo_url"),
                rs.getString("description"),
                rs.getLong("question_count")
            ), lim, off);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM companies", Long.class);
        return Map.of("items", items, "total", total == null ? 0 : total);
    }

    @GetMapping("/{slug}")
    @Cacheable(value = "company-detail", key = "#slug")
    public CompanyDto detail(@PathVariable String slug) {
        var head = jdbc.query("""
            SELECT c.id, c.slug, c.canonical_name, c.industry, c.logo_url, c.description
            FROM companies c WHERE c.slug = ?
            """, (rs, i) -> CompanyDto.light(
                rs.getLong("id"), rs.getString("slug"), rs.getString("canonical_name"),
                rs.getString("industry"), rs.getString("logo_url"), rs.getString("description"), 0L
            ), slug);
        if (head.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "company not found");
        }
        CompanyDto lite = head.get(0);
        long questionCount = jdbc.queryForObject("""
            SELECT COUNT(q.id) FROM questions q
            JOIN experiences e ON e.id = q.experience_id WHERE e.company_id = ?
            """, Long.class, lite.id());
        List<String> topTopics = jdbc.query("""
            SELECT UNNEST(q.topics) AS topic, COUNT(*) AS n
            FROM questions q JOIN experiences e ON e.id = q.experience_id
            WHERE e.company_id = ?
            GROUP BY topic ORDER BY n DESC LIMIT 10
            """, (rs, i) -> rs.getString("topic"), lite.id());
        List<ExperienceDto> recent = jdbc.query("""
            SELECT e.id, e.seniority, e.outcome, e.interview_date, e.summary, e.source_url,
                   r.slug AS role_slug, r.canonical_name AS role_name
            FROM experiences e LEFT JOIN roles r ON r.id = e.role_id
            WHERE e.company_id = ?
            ORDER BY COALESCE(e.interview_date, e.created_at::date) DESC
            LIMIT 8
            """, (rs, i) -> new ExperienceDto(
                rs.getLong("id"), rs.getString("seniority"), rs.getString("outcome"),
                rs.getObject("interview_date", java.time.LocalDate.class),
                rs.getString("summary"), rs.getString("source_url"),
                rs.getString("role_slug"), rs.getString("role_name")
            ), lite.id());
        List<CompanyDto.RoleWithCount> roles = jdbc.query("""
            SELECT r.slug, r.canonical_name, COUNT(q.id) AS n
            FROM roles r
            JOIN experiences e ON e.role_id = r.id
            JOIN questions q ON q.experience_id = e.id
            WHERE e.company_id = ?
            GROUP BY r.slug, r.canonical_name
            ORDER BY n DESC
            """, (rs, i) -> new CompanyDto.RoleWithCount(
                rs.getString("slug"), rs.getString("canonical_name"), rs.getLong("n")
            ), lite.id());
        return new CompanyDto(lite.id(), lite.slug(), lite.canonicalName(),
            lite.industry(), lite.logoUrl(), lite.description(),
            questionCount, topTopics, recent, roles);
    }

    @GetMapping("/{slug}/roles/{roleSlug}")
    public Map<String, Object> byRole(@PathVariable String slug, @PathVariable String roleSlug) {
        Long companyId = firstLong("SELECT id FROM companies WHERE slug = ?", slug);
        Long roleId = firstLong("SELECT id FROM roles WHERE slug = ?", roleSlug);
        if (companyId == null || roleId == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                "company or role not found");
        }
        var questions = jdbc.query("""
            SELECT q.id, q.slug, q.question_text, q.round_type, q.difficulty, q.topics,
                   c.slug AS company_slug, c.canonical_name AS company_name,
                   r.slug AS role_slug, r.canonical_name AS role_name,
                   e.source_url
            FROM questions q
            JOIN experiences e ON e.id = q.experience_id
            JOIN companies c ON c.id = e.company_id
            LEFT JOIN roles r ON r.id = e.role_id
            WHERE e.company_id = ? AND e.role_id = ?
            ORDER BY q.created_at DESC
            LIMIT 100
            """, (rs, i) -> {
                String[] topics = (String[]) (rs.getArray("topics") == null ? null : rs.getArray("topics").getArray());
                return new com.anish.ib.dto.QuestionDto(
                    rs.getLong("id"), rs.getString("slug"), rs.getString("question_text"),
                    rs.getString("round_type"), rs.getString("difficulty"),
                    topics == null ? List.of() : Arrays.asList(topics),
                    new com.anish.ib.dto.QuestionDto.CompanyRef(rs.getString("company_slug"), rs.getString("company_name")),
                    new com.anish.ib.dto.QuestionDto.RoleRef(rs.getString("role_slug"), rs.getString("role_name")),
                    rs.getString("source_url"),
                    null
                );
            }, companyId, roleId);

        Map<String, Integer> diff = new HashMap<>();
        Map<String, Integer> rounds = new HashMap<>();
        for (var q : questions) {
            if (q.difficulty() != null) diff.merge(q.difficulty(), 1, Integer::sum);
            if (q.roundType() != null) rounds.merge(q.roundType(), 1, Integer::sum);
        }
        return Map.of(
            "company", jdbc.queryForMap("SELECT id, slug, canonical_name AS canonicalName, industry FROM companies WHERE id = ?", companyId),
            "role", jdbc.queryForMap("SELECT id, slug, canonical_name AS canonicalName FROM roles WHERE id = ?", roleId),
            "questions", questions,
            "stats", Map.of("difficulty", diff, "rounds", rounds)
        );
    }

    private Long firstLong(String sql, Object... args) {
        var list = jdbc.query(sql, (rs, i) -> rs.getLong(1), args);
        return list.isEmpty() ? null : list.get(0);
    }
}
