package com.anish.ib.api;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
public class RolesController {

    private final JdbcTemplate jdbc;

    public RolesController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Cacheable("roles-list")
    public Map<String, Object> list() {
        List<Map<String, Object>> items = jdbc.query("""
            SELECT r.id, r.slug, r.canonical_name AS canonicalName, r.category,
                   COALESCE(cnt.n, 0) AS questionCount
            FROM roles r
            LEFT JOIN (
              SELECT e.role_id, COUNT(q.id) AS n
              FROM experiences e JOIN questions q ON q.experience_id = e.id
              GROUP BY e.role_id
            ) cnt ON cnt.role_id = r.id
            ORDER BY questionCount DESC, r.canonical_name ASC
            """, (rs, i) -> Map.of(
                "id", rs.getLong("id"),
                "slug", rs.getString("slug"),
                "canonicalName", rs.getString("canonicalName"),
                "category", rs.getString("category") == null ? "" : rs.getString("category"),
                "questionCount", rs.getLong("questionCount")
            ));
        return Map.of("items", items);
    }
}
