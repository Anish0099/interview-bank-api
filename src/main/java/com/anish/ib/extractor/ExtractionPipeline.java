package com.anish.ib.extractor;

import com.anish.ib.domain.Company;
import com.anish.ib.domain.Experience;
import com.anish.ib.domain.RawPost;
import com.anish.ib.domain.Role;
import com.anish.ib.extractor.dto.ExtractedExperience;
import com.anish.ib.repository.ExperienceRepository;
import com.anish.ib.repository.QuestionRepository;
import com.anish.ib.repository.RawPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

@Component
public class ExtractionPipeline {
    private static final Logger log = LoggerFactory.getLogger(ExtractionPipeline.class);
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    private final RawPostRepository rawPostRepo;
    private final ExperienceRepository experienceRepo;
    private final QuestionRepository questionRepo;
    private final LlmExtractor llm;
    private final CompanyResolver companyResolver;
    private final RoleResolver roleResolver;
    private final EmbeddingClient embeddings;
    private final JdbcTemplate jdbc;

    public ExtractionPipeline(RawPostRepository rawPostRepo,
                              ExperienceRepository experienceRepo,
                              QuestionRepository questionRepo,
                              LlmExtractor llm,
                              CompanyResolver companyResolver,
                              RoleResolver roleResolver,
                              EmbeddingClient embeddings,
                              JdbcTemplate jdbc) {
        this.rawPostRepo = rawPostRepo;
        this.experienceRepo = experienceRepo;
        this.questionRepo = questionRepo;
        this.llm = llm;
        this.companyResolver = companyResolver;
        this.roleResolver = roleResolver;
        this.embeddings = embeddings;
        this.jdbc = jdbc;
    }

    public Summary runOnce(int batchSize) throws Exception {
        List<RawPost> pending = rawPostRepo.findFirst100ByProcessedFalseOrderByScrapedAtAsc();
        if (pending.isEmpty()) {
            log.info("No pending raw posts.");
            return new Summary(0, 0, 0, 0);
        }
        int processed = 0, skipped = 0, questionsInserted = 0, errors = 0;
        List<Long> newQuestionIds = new ArrayList<>();
        List<String> textsToEmbed = new ArrayList<>();

        for (RawPost p : pending.subList(0, Math.min(batchSize, pending.size()))) {
            try {
                var extracted = llm.extract(p.getTitle(), p.getBody());
                if (extracted.isEmpty()) {
                    markProcessed(p, "not an interview experience");
                    skipped++;
                    continue;
                }
                var e = extracted.get();
                var company = companyResolver.resolve(e.company(), p.getId());
                if (company.isEmpty() || company.get().needsReview()) {
                    markProcessed(p, company.map(r -> "queued-for-review:" + r.company().getSlug()).orElse("no company"));
                    skipped++;
                    continue;
                }
                Role role = roleResolver.resolve(e.role(), e.seniority());
                Experience exp = persistExperience(p, e, company.get().company(), role);

                int qCount = persistQuestions(exp, e, newQuestionIds, textsToEmbed);
                questionsInserted += qCount;
                markProcessed(p, null);
                processed++;
            } catch (Exception ex) {
                log.warn("Extraction failed for post {}: {}", p.getSourceId(), ex.getMessage());
                markProcessed(p, "error: " + truncate(ex.getMessage(), 500));
                errors++;
            }
        }

        // Embed new questions in batches of 100.
        if (embeddings.configured() && !textsToEmbed.isEmpty()) {
            try {
                List<float[]> vectors = embeddings.embedAll(textsToEmbed);
                writeEmbeddings(newQuestionIds, vectors);
            } catch (Exception embedErr) {
                log.warn("Embedding step failed (rows inserted without vectors): {}", embedErr.getMessage());
            }
        }
        return new Summary(processed, skipped, questionsInserted, errors);
    }

    @Transactional
    protected Experience persistExperience(RawPost p, ExtractedExperience e, Company company, Role role) {
        Experience exp = new Experience();
        exp.setRawPostId(p.getId());
        exp.setCompanyId(company.getId());
        exp.setRoleId(role == null ? null : role.getId());
        exp.setSeniority(e.seniority());
        exp.setOutcome(e.outcome());
        exp.setInterviewDate(parseYearMonth(e.interview_date()));
        exp.setSummary(e.summary());
        exp.setSourceUrl(p.getSourceUrl());
        return experienceRepo.save(exp);
    }

    @Transactional
    protected int persistQuestions(Experience exp, ExtractedExperience e,
                                   List<Long> collectedIds, List<String> collectedTexts) {
        if (e.rounds() == null) return 0;
        int inserted = 0;
        Random rng = new Random(exp.getId() ^ System.nanoTime());
        for (var round : e.rounds()) {
            if (round == null || round.questions() == null) continue;
            for (var q : round.questions()) {
                if (q == null || q.question_text() == null || q.question_text().isBlank()) continue;
                var qEntity = new com.anish.ib.domain.Question();
                qEntity.setExperienceId(exp.getId());
                qEntity.setSlug(uniqueSlug(q.question_text(), rng));
                qEntity.setQuestionText(q.question_text().trim());
                qEntity.setRoundType(round.round_type());
                qEntity.setDifficulty(q.difficulty());
                qEntity.setTopics(q.topics() == null ? new String[0] : q.topics().toArray(new String[0]));
                var saved = questionRepo.save(qEntity);
                collectedIds.add(saved.getId());
                collectedTexts.add(qEntity.getQuestionText());
                inserted++;
            }
        }
        return inserted;
    }

    private void writeEmbeddings(List<Long> ids, List<float[]> vectors) {
        int n = Math.min(ids.size(), vectors.size());
        for (int i = 0; i < n; i++) {
            String vec = toVectorLiteral(vectors.get(i));
            jdbc.update("UPDATE questions SET embedding = CAST(? AS vector) WHERE id = ?", vec, ids.get(i));
        }
    }

    private static String toVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 8).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    @Transactional
    protected void markProcessed(RawPost p, String error) {
        p.setProcessed(true);
        p.setProcessingError(error);
        rawPostRepo.save(p);
    }

    private String uniqueSlug(String text, Random rng) {
        String base = CompanyResolver.slugify(text);
        if (base.length() > 80) base = base.substring(0, 80);
        String candidate = base + "-" + Long.toHexString(rng.nextInt() & 0xffffL);
        while (questionRepo.existsBySlug(candidate)) {
            candidate = base + "-" + Long.toHexString(rng.nextInt() & 0xffffL);
        }
        return candidate;
    }

    private LocalDate parseYearMonth(String ym) {
        if (ym == null || ym.isBlank() || "null".equalsIgnoreCase(ym)) return null;
        try {
            return LocalDate.parse(ym.trim() + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            try {
                return LocalDate.from(YM.parse(ym.trim())).withDayOfMonth(1);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    public record Summary(int processed, int skipped, int questionsInserted, int errors) {}
}
