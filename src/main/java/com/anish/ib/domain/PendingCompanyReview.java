package com.anish.ib.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pending_company_reviews")
public class PendingCompanyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_post_id")
    private Long rawPostId;

    @Column(name = "extracted_name", nullable = false)
    private String extractedName;

    @Column(name = "best_match_slug")
    private String bestMatchSlug;

    @Column(name = "best_match_score")
    private Float bestMatchScore;

    private String reason;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "resolved_company_id")
    private Long resolvedCompanyId;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getRawPostId() { return rawPostId; }
    public void setRawPostId(Long rawPostId) { this.rawPostId = rawPostId; }
    public String getExtractedName() { return extractedName; }
    public void setExtractedName(String extractedName) { this.extractedName = extractedName; }
    public String getBestMatchSlug() { return bestMatchSlug; }
    public void setBestMatchSlug(String bestMatchSlug) { this.bestMatchSlug = bestMatchSlug; }
    public Float getBestMatchScore() { return bestMatchScore; }
    public void setBestMatchScore(Float bestMatchScore) { this.bestMatchScore = bestMatchScore; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getResolvedCompanyId() { return resolvedCompanyId; }
    public void setResolvedCompanyId(Long resolvedCompanyId) { this.resolvedCompanyId = resolvedCompanyId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(OffsetDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
