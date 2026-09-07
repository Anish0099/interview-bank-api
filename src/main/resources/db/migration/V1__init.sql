CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE companies (
  id BIGSERIAL PRIMARY KEY,
  canonical_name TEXT NOT NULL UNIQUE,
  slug TEXT NOT NULL UNIQUE,
  aliases TEXT[] DEFAULT '{}',
  industry TEXT,
  logo_url TEXT,
  description TEXT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_companies_aliases ON companies USING gin(aliases);
CREATE INDEX idx_companies_name_trgm ON companies USING gin(canonical_name gin_trgm_ops);

CREATE TABLE roles (
  id BIGSERIAL PRIMARY KEY,
  canonical_name TEXT NOT NULL UNIQUE,
  slug TEXT NOT NULL UNIQUE,
  aliases TEXT[] DEFAULT '{}',
  category TEXT
);
CREATE INDEX idx_roles_aliases ON roles USING gin(aliases);

CREATE TABLE raw_posts (
  id BIGSERIAL PRIMARY KEY,
  source TEXT NOT NULL,
  source_id TEXT NOT NULL,
  source_url TEXT NOT NULL,
  title TEXT,
  body TEXT,
  raw_json JSONB,
  scraped_at TIMESTAMPTZ DEFAULT NOW(),
  processed BOOLEAN DEFAULT FALSE,
  processing_error TEXT,
  UNIQUE(source, source_id)
);
CREATE INDEX idx_raw_posts_processed ON raw_posts(processed) WHERE processed = FALSE;

CREATE TABLE experiences (
  id BIGSERIAL PRIMARY KEY,
  raw_post_id BIGINT REFERENCES raw_posts(id) ON DELETE CASCADE,
  company_id BIGINT REFERENCES companies(id),
  role_id BIGINT REFERENCES roles(id),
  seniority TEXT,
  outcome TEXT,
  interview_date DATE,
  summary TEXT,
  source_url TEXT NOT NULL,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_experiences_company_role ON experiences(company_id, role_id);
CREATE INDEX idx_experiences_date ON experiences(interview_date DESC);

CREATE TABLE questions (
  id BIGSERIAL PRIMARY KEY,
  experience_id BIGINT REFERENCES experiences(id) ON DELETE CASCADE,
  slug TEXT UNIQUE NOT NULL,
  question_text TEXT NOT NULL,
  round_type TEXT,
  difficulty TEXT,
  topics TEXT[] DEFAULT '{}',
  embedding vector(768),
  created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_questions_experience ON questions(experience_id);
CREATE INDEX idx_questions_topics ON questions USING gin(topics);
CREATE INDEX idx_questions_fts ON questions USING gin(to_tsvector('english', question_text));
CREATE INDEX idx_questions_embedding ON questions USING hnsw (embedding vector_cosine_ops);

CREATE TABLE search_logs (
  id BIGSERIAL PRIMARY KEY,
  query TEXT NOT NULL,
  results_count INT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_search_logs_created ON search_logs(created_at DESC);
