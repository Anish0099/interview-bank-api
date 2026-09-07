# Legal — InterviewBank API

## Data sourcing

The scrapers in this repo fetch publicly accessible content only:

- **Reddit**: authenticated via OAuth `client_credentials`; only public
  subreddits configured in `IB_REDDIT_SUBREDDITS` are read. We do not read
  private, quarantined, or logged-in-only content.
- **GeeksforGeeks**: fetched via the site's own `sitemap.xml` and article
  pages, respecting `robots.txt` and with a 2-second delay between requests.
- **LeetCode Discuss** (phase 9, stubbed): will use the public GraphQL API
  the site itself uses to render `/discuss`.

## What we store

- Full raw post JSON is kept in `raw_posts.raw_json` for reproducibility of
  the extraction step. We never store Reddit usernames, user IDs, or any
  author-linkable field beyond what is required to link back to the post's
  public URL.
- Structured extractions live in `experiences` and `questions`. Each row
  carries the original `source_url` so every derived question links back to
  the public post.

## Fair-use rationale

Interview questions are short factual utterances typically not eligible for
copyright protection in the jurisdictions we operate in. Where a source post
combines a question with commentary or narrative, we index the extracted
question and a two-sentence AI-generated summary — not the original prose.
No page reproduces a source post in full.

## Takedown process

`POST /api/takedown` accepts:

```json
{
  "pageUrl": "https://interviews.yourdomain.com/question/...",
  "requesterEmail": "you@example.com",
  "reason": "I authored the source post and want it removed."
}
```

Requests are inserted into `takedown_requests` and mirrored to the address in
`TAKEDOWN_CONTACT_EMAIL`. We remove the row and invalidate caches within 48
hours of receipt.

## Trademarks

Company names (Cognizant, TCS, Amazon, Razorpay, etc.) are the property of
their respective owners. Their appearance is descriptive and does not imply
affiliation or endorsement.
