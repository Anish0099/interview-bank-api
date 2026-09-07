package com.anish.ib;

import com.anish.ib.domain.RawPost;
import com.anish.ib.extractor.ExtractionPipeline;
import com.anish.ib.repository.RawPostRepository;
import com.anish.ib.scraper.DevToScraper;
import com.anish.ib.scraper.GfgScraper;
import com.anish.ib.scraper.RedditScraper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootApplication
@EnableCaching
public class ScraperCli {

    public static void main(String[] args) {
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("ib.cli.enabled", "true");
        SpringApplication.run(ScraperCli.class, args);
    }

    @Component
    @Profile("!test")
    @ConditionalOnProperty(name = "ib.cli.enabled", havingValue = "true")
    static class Runner implements CommandLineRunner {
        private static final Logger log = LoggerFactory.getLogger(Runner.class);

        private final RedditScraper reddit;
        private final GfgScraper gfg;
        private final DevToScraper devto;
        private final ExtractionPipeline extractor;
        private final RawPostRepository rawPostRepo;
        private final ObjectMapper mapper;

        Runner(RedditScraper reddit,
               GfgScraper gfg,
               DevToScraper devto,
               ExtractionPipeline extractor,
               RawPostRepository rawPostRepo,
               ObjectMapper mapper) {
            this.reddit = reddit;
            this.gfg = gfg;
            this.devto = devto;
            this.extractor = extractor;
            this.rawPostRepo = rawPostRepo;
            this.mapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        }

        @Override
        public void run(String... args) throws Exception {
            String source = argValue(args, "--source", "reddit");
            String mode = argValue(args, "--mode", "recent");
            log.info("scraper-cli source={} mode={}", source, mode);

            switch (mode) {
                case "recent", "backfill" -> runScrape(source, mode);
                case "extract" -> {
                    int batch = Integer.parseInt(argValue(args, "--batch", "50"));
                    var s = extractor.runOnce(batch);
                    log.info("extract summary: processed={} skipped={} questions={} errors={}",
                        s.processed(), s.skipped(), s.questionsInserted(), s.errors());
                }
                case "export-seed" -> exportSeed(argValue(args, "--out", "seed/reddit-samples.json"),
                                                Integer.parseInt(argValue(args, "--count", "30")));
                default -> log.warn("Unknown mode: {}", mode);
            }
        }

        private void runScrape(String source, String mode) throws Exception {
            switch (source) {
                case "reddit" -> {
                    RedditScraper.ScrapeResult r = reddit.scrapeAll(mode);
                    log.info("reddit scrape summary: seen={} inserted={} filtered={}", r.seen(), r.inserted(), r.filtered());
                }
                case "gfg" -> {
                    int max = "backfill".equals(mode) ? 500 : 100;
                    GfgScraper.ScrapeResult r = gfg.scrape(max);
                    log.info("gfg scrape summary: seen={} inserted={} failed={}", r.seen(), r.inserted(), r.failed());
                }
                case "devto" -> {
                    DevToScraper.ScrapeResult r = devto.scrape(mode);
                    log.info("devto scrape summary: seen={} inserted={} filtered={} failed={}",
                        r.seen(), r.inserted(), r.filtered(), r.failed());
                }
                default -> log.warn("Unknown source: {}", source);
            }
        }

        private void exportSeed(String outPath, int count) throws Exception {
            List<RawPost> posts = rawPostRepo.findAll().stream().limit(count).toList();
            if (posts.isEmpty()) {
                log.warn("No raw_posts to export. Run --mode=recent first.");
                return;
            }
            var payload = posts.stream().map(p -> {
                var node = mapper.createObjectNode();
                node.put("source", p.getSource());
                node.put("source_id", p.getSourceId());
                node.put("source_url", p.getSourceUrl());
                node.put("title", p.getTitle());
                node.put("body", p.getBody());
                return node;
            }).toList();
            Path out = Path.of(outPath);
            if (out.getParent() != null) Files.createDirectories(out.getParent());
            Files.writeString(out, mapper.writeValueAsString(payload));
            log.info("Exported {} raw posts to {}", posts.size(), out.toAbsolutePath());
        }
    }

    private static String argValue(String[] args, String key, String fallback) {
        for (String a : args) {
            if (a.startsWith(key + "=")) return a.substring(key.length() + 1);
        }
        return fallback;
    }
}
