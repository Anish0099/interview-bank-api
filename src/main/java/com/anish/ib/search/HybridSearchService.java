package com.anish.ib.search;

import com.anish.ib.domain.SearchLog;
import com.anish.ib.dto.QuestionDto;
import com.anish.ib.dto.SearchResponse;
import com.anish.ib.extractor.EmbeddingClient;
import com.anish.ib.repository.SearchLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
public class HybridSearchService {
    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

    private static final String SQL_HYBRID = """
        WITH fts AS (
          SELECT id, ts_rank(to_tsvector('english', question_text),
                             plainto_tsquery('english', ?)) AS score
          FROM questions
          WHERE to_tsvector('english', question_text) @@ plainto_tsquery('english', ?)
          ORDER BY score DESC LIMIT 50
        ),
        semantic AS (
          SELECT id, 1 - (embedding <=> CAST(? AS vector)) AS score
          FROM questions
          WHERE embedding IS NOT NULL
          ORDER BY embedding <=> CAST(? AS vector) LIMIT 50
        )
        SELECT q.id, q.slug, q.question_text, q.round_type, q.difficulty, q.topics,
               c.slug AS company_slug, c.canonical_name AS company_name,
               r.slug AS role_slug, r.canonical_name AS role_name,
               e.source_url,
               COALESCE(fts.score, 0) * 0.4 + COALESCE(semantic.score, 0) * 0.6 AS final_score
        FROM questions q
        LEFT JOIN fts ON fts.id = q.id
        LEFT JOIN semantic ON semantic.id = q.id
        JOIN experiences e ON e.id = q.experience_id
        JOIN companies c ON c.id = e.company_id
        LEFT JOIN roles r ON r.id = e.role_id
        WHERE fts.id IS NOT NULL OR semantic.id IS NOT NULL
        ORDER BY final_score DESC
        LIMIT 20
        """;

    private static final String SQL_FTS_ONLY = """
        SELECT q.id, q.slug, q.question_text, q.round_type, q.difficulty, q.topics,
               c.slug AS company_slug, c.canonical_name AS company_name,
               r.slug AS role_slug, r.canonical_name AS role_name,
               e.source_url,
               ts_rank(to_tsvector('english', q.question_text),
                       plainto_tsquery('english', ?)) AS final_score
        FROM questions q
        JOIN experiences e ON e.id = q.experience_id
        JOIN companies c ON c.id = e.company_id
        LEFT JOIN roles r ON r.id = e.role_id
        WHERE to_tsvector('english', q.question_text) @@ plainto_tsquery('english', ?)
        ORDER BY final_score DESC
        LIMIT 20
        """;

    private final JdbcTemplate jdbc;
    private final EmbeddingClient embeddings;
    private final SearchLogRepository searchLogRepo;

    public HybridSearchService(JdbcTemplate jdbc, EmbeddingClient embeddings, SearchLogRepository searchLogRepo) {
        this.jdbc = jdbc;
        this.embeddings = embeddings;
        this.searchLogRepo = searchLogRepo;
    }

    public SearchResponse search(String query, String type) {
        if (query == null || query.isBlank()) {
            return new SearchResponse("", 0, List.of());
        }
        String q = query.trim();
        List<QuestionDto> results = "fts".equalsIgnoreCase(type) ? ftsOnly(q) : hybrid(q);
        try {
            SearchLog logEntry = new SearchLog();
            logEntry.setQuery(q);
            logEntry.setResultsCount(results.size());
            searchLogRepo.save(logEntry);
        } catch (Exception ignored) {
            // logging must never break search
        }
        return new SearchResponse(q, results.size(), results);
    }

    private List<QuestionDto> hybrid(String q) {
        if (!embeddings.configured()) {
            log.debug("Embeddings not configured — degrading to FTS-only search.");
            return ftsOnly(q);
        }
        String vecLiteral;
        try {
            var vec = embeddings.embedAll(List.of(q)).get(0);
            vecLiteral = toVectorLiteral(vec);
        } catch (Exception e) {
            log.warn("Query embedding failed, falling back to FTS: {}", e.getMessage());
            return ftsOnly(q);
        }
        return jdbc.query(SQL_HYBRID,
            (rs, i) -> mapRow(rs),
            q, q, vecLiteral, vecLiteral);
    }

    private List<QuestionDto> ftsOnly(String q) {
        return jdbc.query(SQL_FTS_ONLY, (rs, i) -> mapRow(rs), q, q);
    }

    private static QuestionDto mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        String[] topics = (String[]) (rs.getArray("topics") == null ? null : rs.getArray("topics").getArray());
        List<String> topicList = topics == null ? Collections.emptyList() : Arrays.asList(topics);
        return new QuestionDto(
            rs.getLong("id"),
            rs.getString("slug"),
            rs.getString("question_text"),
            rs.getString("round_type"),
            rs.getString("difficulty"),
            topicList,
            new QuestionDto.CompanyRef(rs.getString("company_slug"), rs.getString("company_name")),
            new QuestionDto.RoleRef(rs.getString("role_slug"), rs.getString("role_name")),
            rs.getString("source_url"),
            rs.getObject("final_score") == null ? null : rs.getDouble("final_score")
        );
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
