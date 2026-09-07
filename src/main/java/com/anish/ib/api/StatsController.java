package com.anish.ib.api;

import com.anish.ib.dto.StatsDto;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final JdbcTemplate jdbc;

    public StatsController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/stats")
    @Cacheable("stats")
    public StatsDto stats() {
        Long companies = jdbc.queryForObject("SELECT COUNT(*) FROM companies", Long.class);
        Long roles = jdbc.queryForObject("SELECT COUNT(*) FROM roles", Long.class);
        Long questions = jdbc.queryForObject("SELECT COUNT(*) FROM questions", Long.class);
        Long experiences = jdbc.queryForObject("SELECT COUNT(*) FROM experiences", Long.class);
        OffsetDateTime last = jdbc.queryForObject(
            "SELECT COALESCE(MAX(created_at), NOW()) FROM questions",
            OffsetDateTime.class);
        return new StatsDto(
            nn(companies), nn(roles), nn(questions), nn(experiences),
            last == null ? OffsetDateTime.now() : last);
    }

    private static long nn(Long v) { return v == null ? 0L : v; }
}
