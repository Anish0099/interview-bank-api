CREATE TABLE pending_company_reviews (
  id BIGSERIAL PRIMARY KEY,
  raw_post_id BIGINT REFERENCES raw_posts(id) ON DELETE CASCADE,
  extracted_name TEXT NOT NULL,
  best_match_slug TEXT,
  best_match_score REAL,
  reason TEXT,
  status TEXT NOT NULL DEFAULT 'pending',
  resolved_company_id BIGINT REFERENCES companies(id),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_pending_review_status ON pending_company_reviews(status) WHERE status = 'pending';
