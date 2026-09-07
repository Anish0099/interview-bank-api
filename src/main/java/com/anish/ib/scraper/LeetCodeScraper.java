package com.anish.ib.scraper;

import org.springframework.stereotype.Component;

/**
 * LeetCode Discuss scraper — deferred (Phase 9 stub).
 *
 * TODO(phase-9): call https://leetcode.com/graphql with the categoryTopicList query
 *   (categorySlug: "interview-question") and paginate. Persist results into
 *   raw_posts with source="leetcode". Follow LeetCode's rate limits and
 *   robots.txt; consider requiring a signed-in session cookie for full replies.
 *
 * Example GraphQL query:
 *   query categoryTopicList($orderBy: TopicSortingOption, $query: String, $skip: Int, $first: Int, $tags: [String!]) {
 *     categoryTopicList(orderBy: $orderBy, query: $query, skip: $skip, first: $first, tags: $tags) {
 *       edges { node { id title post { content author { username } } } }
 *     }
 *   }
 */
@Component
public class LeetCodeScraper {

    public record ScrapeResult(int seen, int inserted, int failed) {}

    public ScrapeResult scrape(int max) {
        // Intentional no-op until Phase 9 lands.
        return new ScrapeResult(0, 0, 0);
    }
}
