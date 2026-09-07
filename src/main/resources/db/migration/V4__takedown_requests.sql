CREATE TABLE takedown_requests (
  id BIGSERIAL PRIMARY KEY,
  page_url TEXT NOT NULL,
  requester_email TEXT NOT NULL,
  reason TEXT,
  status TEXT NOT NULL DEFAULT 'pending',
  created_at TIMESTAMPTZ DEFAULT NOW(),
  resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_takedown_status ON takedown_requests(status) WHERE status = 'pending';
