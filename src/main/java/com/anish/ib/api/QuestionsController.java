package com.anish.ib.api;

import com.anish.ib.dto.QuestionDto;
import com.anish.ib.extractor.EmbeddingClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionsController {

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddings;

    public QuestionsController(JdbcTemplate jdbc, EmbeddingClient embeddings) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
    }

    @GetMapping("/{slug}")
    @Cacheable(value = "question-detail", key = "#slug")
    public Map<String, Object> question(@PathVariable String slug) {
        var rows = jdbc.query("""
            SELECT q.id, q.slug, q.question_text, q.round_type, q.difficulty, q.topics,
                   c.slug AS company_slug, c.canonical_name AS company_name,
                   r.slug AS role_slug, r.canonical_name AS role_name,
                   e.source_url
            FROM questions q
            JOIN experiences e ON e.id = q.experience_id
            JOIN companies c ON c.id = e.company_id
            LEFT JOIN roles r ON r.id = e.role_id
            WHERE q.slug = ?
            """, (rs, i) -> mapRow(rs), slug);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "question not found");
        }
        QuestionDto q = rows.get(0);
        List<QuestionDto> related = findRelated(q.id());
        return Map.of("question", q, "related", related);
    }

    private List<QuestionDto> findRelated(long id) {
        // semantic similarity if the row has an embedding; else same-topic fallback.
        Boolean hasVec = jdbc.query("SELECT embedding IS NOT NULL AS has FROM questions WHERE id = ?",
            (rs, i) -> rs.getBoolean("has"), id).stream().findFirst().orElse(false);
        if (Boolean.TRUE.equals(hasVec)) {
            return jdbc.query("""
                SELECT q.id, q.slug, q.question_text, q.round_type, q.difficulty, q.topics,
                       c.slug AS company_slug, c.canonical_name AS company_name,
                       r.slug AS role_slug, r.canonical_name AS role_name,
                       e.source_url
                FROM questions q
                JOIN experiences e ON e.id = q.experience_id
                JOIN companies c ON c.id = e.company_id
                LEFT JOIN roles r ON r.id = e.role_id
                WHERE q.id <> ?
                  AND q.embedding IS NOT NULL
                ORDER BY q.embedding <=> (SELECT embedding FROM questions WHERE id = ?)
                LIMIT 3
                """, (rs, i) -> mapRow(rs), id, id);
        }
        return jdbc.query("""
            SELECT q.id, q.slug, q.question_text, q.round_type, q.difficulty, q.topics,
                   c.slug AS company_slug, c.canonical_name AS company_name,
                   r.slug AS role_slug, r.canonical_name AS role_name,
                   e.source_url
            FROM questions q
            JOIN experiences e ON e.id = q.experience_id
            JOIN companies c ON c.id = e.company_id
            LEFT JOIN roles r ON r.id = e.role_id
            WHERE q.id <> ?
              AND q.topics && (SELECT topics FROM questions WHERE id = ?)
            ORDER BY q.created_at DESC
            LIMIT 3
            """, (rs, i) -> mapRow(rs), id, id);
    }

    private static QuestionDto mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String[] topics = (String[]) (rs.getArray("topics") == null ? null : rs.getArray("topics").getArray());
        return new QuestionDto(
            rs.getLong("id"), rs.getString("slug"), rs.getString("question_text"),
            rs.getString("round_type"), rs.getString("difficulty"),
            topics == null ? List.of() : Arrays.asList(topics),
            new QuestionDto.CompanyRef(rs.getString("company_slug"), rs.getString("company_name")),
            new QuestionDto.RoleRef(rs.getString("role_slug"), rs.getString("role_name")),
            rs.getString("source_url"),
            null
        );
    }
}
