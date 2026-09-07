package com.anish.ib.extractor;

import com.anish.ib.domain.Company;
import com.anish.ib.domain.PendingCompanyReview;
import com.anish.ib.repository.CompanyRepository;
import com.anish.ib.repository.PendingCompanyReviewRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class CompanyResolver {
    private static final Logger log = LoggerFactory.getLogger(CompanyResolver.class);

    private final CompanyRepository companyRepo;
    private final PendingCompanyReviewRepository pendingRepo;

    @PersistenceContext
    private EntityManager em;

    public CompanyResolver(CompanyRepository companyRepo, PendingCompanyReviewRepository pendingRepo) {
        this.companyRepo = companyRepo;
        this.pendingRepo = pendingRepo;
    }

    public record Resolution(Company company, float score, boolean needsReview) {}

    @Transactional
    public Optional<Resolution> resolve(String extractedName, Long rawPostId) {
        if (extractedName == null || extractedName.isBlank()) return Optional.empty();
        String name = extractedName.trim().toLowerCase(Locale.ROOT);

        // 1. Exact slug or canonical-name match short-circuits fuzzy work.
        Optional<Company> exact = companyRepo.findByCanonicalNameIgnoreCase(name)
            .or(() -> companyRepo.findBySlug(slugify(name)));
        if (exact.isPresent()) {
            return Optional.of(new Resolution(exact.get(), 1.0f, false));
        }

        // 2. pg_trgm similarity against canonical_name and aliases.
        List<Company> candidates = companyRepo.fuzzyMatch(name, 3);
        if (!candidates.isEmpty()) {
            Company top = candidates.get(0);
            float score = bestScore(top, name);
            if (score >= 0.6f) {
                return Optional.of(new Resolution(top, score, false));
            }
            if (score >= 0.4f) {
                queueReview(rawPostId, extractedName, top.getSlug(), score,
                    "Similarity between 0.4 and 0.6");
                return Optional.of(new Resolution(top, score, true));
            }
        }

        // 3. Auto-create with a slugified name.
        Company created = createStub(extractedName);
        log.info("Auto-created company {} (slug={})", created.getCanonicalName(), created.getSlug());
        return Optional.of(new Resolution(created, 0.0f, false));
    }

    private void queueReview(Long rawPostId, String extractedName, String slug, float score, String reason) {
        PendingCompanyReview r = new PendingCompanyReview();
        r.setRawPostId(rawPostId);
        r.setExtractedName(extractedName);
        r.setBestMatchSlug(slug);
        r.setBestMatchScore(score);
        r.setReason(reason);
        pendingRepo.save(r);
    }

    @Transactional
    protected Company createStub(String rawName) {
        String slug = uniqueSlug(slugify(rawName));
        Company c = new Company();
        c.setCanonicalName(rawName);
        c.setSlug(slug);
        c.setAliases(new String[]{rawName.toLowerCase(Locale.ROOT)});
        return companyRepo.save(c);
    }

    private String uniqueSlug(String base) {
        String slug = base;
        int suffix = 1;
        while (companyRepo.findBySlug(slug).isPresent()) {
            suffix++;
            slug = base + "-" + suffix;
        }
        return slug;
    }

    private float bestScore(Company top, String name) {
        // Use a native similarity() query for the top candidate to get an exact score.
        Object score = em.createNativeQuery("""
            SELECT GREATEST(
              similarity(:name, :canonical),
              COALESCE((SELECT MAX(similarity(:name, a)) FROM unnest(:aliases::text[]) a), 0)
            )
            """)
            .setParameter("name", name)
            .setParameter("canonical", top.getCanonicalName().toLowerCase(Locale.ROOT))
            .setParameter("aliases", "{" + String.join(",", top.getAliases()) + "}")
            .getSingleResult();
        return score == null ? 0f : ((Number) score).floatValue();
    }

    static String slugify(String s) {
        String base = s.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return base.isBlank() ? "unknown" : base;
    }
}
