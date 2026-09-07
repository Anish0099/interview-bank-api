package com.anish.ib.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/takedown")
public class TakedownController {

    private final JdbcTemplate jdbc;
    private final String contactEmail;

    public TakedownController(JdbcTemplate jdbc, @Value("${ib.takedown.contact-email:}") String contactEmail) {
        this.jdbc = jdbc;
        this.contactEmail = contactEmail;
    }

    public record TakedownRequest(
        @NotBlank @Size(max = 500) String pageUrl,
        @NotBlank @Email @Size(max = 200) String requesterEmail,
        @Size(max = 2000) String reason
    ) {}

    @PostMapping
    public Map<String, Object> submit(@Valid @RequestBody TakedownRequest body) {
        jdbc.update(
            "INSERT INTO takedown_requests(page_url, requester_email, reason) VALUES (?, ?, ?)",
            body.pageUrl(), body.requesterEmail(), body.reason());
        return Map.of(
            "status", "received",
            "contact", contactEmail == null ? "" : contactEmail,
            "sla_hours", 48
        );
    }
}
