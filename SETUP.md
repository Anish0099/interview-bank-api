# InterviewBank — Setup Guide

Zero-cost setup: Neon Postgres + Google Cloud Run (backend) + Vercel (frontend) + GitHub Actions (cron) + Groq (LLM) + Gemini (embeddings). Render is kept as a fallback if you'd rather not attach a billing account to Google Cloud.

## 1. Prerequisites

- A GitHub account
- Java 21 and Maven locally (Java 21 comes with Temurin: https://adoptium.net)
- Node.js 20+ and npm
- The two repo directories in this folder pushed to their own GitHub repos:
  - `interview-bank-api/` → e.g. `github.com/you/interview-bank-api`
  - `interview-bank-web/` → e.g. `github.com/you/interview-bank-web`

## 2. Create a Neon Postgres database

1. Sign up at https://neon.tech (free tier).
2. Create a new project. Region: pick the one closest to your Render region (Singapore is a good default for Indian users).
3. Once created, open the connection details panel. Copy the pooled connection string — it looks like `postgres://user:pass@ep-xxx-pooler.eu-central-1.aws.neon.tech/interview_bank?sslmode=require`.
4. Convert it to a JDBC URL: replace the `postgres://user:pass@` prefix with `jdbc:postgresql://` and move the credentials to separate env vars. Example:
   - `DB_URL=jdbc:postgresql://ep-xxx-pooler.eu-central-1.aws.neon.tech/interview_bank?sslmode=require`
   - `DB_USER=user`
   - `DB_PASSWORD=pass`
5. In the Neon SQL editor, run once:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   CREATE EXTENSION IF NOT EXISTS pg_trgm;
   ```
   (Flyway also creates them, but running once explicitly ensures your project has the right permissions.)

## 3. Create a Reddit "script" app

1. Log in at https://www.reddit.com/prefs/apps.
2. Click **create app** at the bottom → choose **script** → name it `interview-bank`.
3. Set the redirect URI to `http://localhost` (unused by client-credentials flow but required by Reddit).
4. Copy the **client id** (the string under the app name) and the **secret**.
5. Pick a user-agent string that includes your Reddit username, e.g. `interview-bank/1.0 by /u/your_username`. Reddit blocks requests without one.

## 4. Get a Groq API key

1. Sign up at https://console.groq.com.
2. Create an API key. Free tier gives generous throughput on `llama-3.3-70b-versatile`.

## 5. Get a Google AI Studio (Gemini) key

1. Go to https://aistudio.google.com/app/apikey.
2. Create an API key. Free tier includes 1500 requests/day on `text-embedding-004`.

## 6. Deploy the backend to Google Cloud Run

Cloud Run beats Render on cold start (~1–3s JVM warm-up vs 30–60s on Render) and the free tier is permanent, not credit-based. Requires a Google Cloud account with a payment method on file — you won't be charged unless you exceed the always-free quota (2M requests / 360k GB-seconds / 180k vCPU-seconds per month), which a small app will not.

1. Install the gcloud CLI: https://cloud.google.com/sdk/docs/install (on Debian/Ubuntu: `sudo apt install google-cloud-cli`).
2. Sign in and create a project:
   ```bash
   gcloud auth login
   gcloud projects create interview-bank-<your-suffix> --name="InterviewBank"
   gcloud config set project interview-bank-<your-suffix>
   ```
3. Attach a billing account to that project (Cloud Run free tier still requires billing to be linked — nothing gets charged until you exceed free quota): https://console.cloud.google.com/billing/linkedaccount
4. Fill in the values in your local `interview-bank-api/.env`:
   - `GCP_PROJECT` — the project id from step 2
   - `DB_URL`, `DB_USER`, `DB_PASSWORD` — Neon values
   - `CORS_ALLOWED_ORIGINS` — your future Vercel URL, e.g. `https://interview-bank-web.vercel.app`
   - `REDDIT_CLIENT_ID`, `REDDIT_CLIENT_SECRET`, `REDDIT_USER_AGENT`
   - `GROQ_API_KEY`, `GEMINI_API_KEY`
   - `TAKEDOWN_CONTACT_EMAIL`
5. Deploy from source (Cloud Build builds the Docker image for you — no local Docker required):
   ```bash
   cd interview-bank-api/deploy/cloudrun
   ./deploy.sh
   ```
   First deploy takes ~4–5 minutes. It ends by printing the live URL.
6. Verify: `curl https://<your-cloud-run-host>/api/health` — should return `{"status":"ok"}`.

The service auto-scales to zero when idle and wakes on the first request, so **no UptimeRobot ping is needed**.

### Alternative: Render (fallback)

The repo still ships a `render.yaml`. If you prefer Render, push to GitHub, import as a **Web Service** with environment = Docker, plan = Free, region = Singapore, and set the same env vars in the service's **Environment** tab. Add an UptimeRobot HTTPS monitor pointing at `/api/health` at 5-minute intervals to soften the 15-minute cold-sleep.

## 7. Deploy the frontend to Vercel

1. Push `interview-bank-web/` to GitHub.
2. On https://vercel.com/new, import the repo.
3. Framework preset = Next.js. Root directory = repo root.
4. Add these environment variables (both Preview and Production):
   - `NEXT_PUBLIC_API_URL` = `https://<your-cloud-run-host>` (no trailing slash)
   - `NEXT_PUBLIC_SITE_URL` = `https://<your-vercel-domain>` (or your custom domain when connected)
5. Deploy.

## 8. Connect a custom domain (optional)

- Vercel: **Domains → Add** → follow DNS instructions with your registrar. Update `NEXT_PUBLIC_SITE_URL` after DNS propagates.
- Cloud Run: **Cloud Run console → your service → Custom Domains → Add Mapping** (or use Cloud Load Balancer for HTTPS on an apex domain). Update `CORS_ALLOWED_ORIGINS` to include the new domain and redeploy.

## 9. Wire up the GitHub Actions cron scraper

1. In the `interview-bank-api` repo's GitHub settings, go to **Settings → Secrets → Actions**.
2. Add every secret you set on Cloud Run — same names, same values.
3. Trigger the workflow once manually from the **Actions** tab (`Scrape and extract` → **Run workflow** → mode `recent`).
4. Confirm the run finishes green. The cron will now trigger every 6 hours automatically.

## 10. Capture 30 real seed posts locally

Before the frontend has real content, capture a small seed corpus locally so you can develop against real data.

```bash
cd interview-bank-api
cp .env.example .env
# fill in DB_URL / DB_USER / DB_PASSWORD / REDDIT_*
export $(grep -v '^#' .env | xargs)

mvn -B -DskipTests package
java -jar target/ib-api-scraper.jar --source=reddit --mode=recent
java -jar target/ib-api-scraper.jar --mode=export-seed --out=seed/reddit-samples.json --count=30

git add seed/reddit-samples.json && git commit -m "chore(scraper): seed corpus"
```

## 11. Verify Google Search Console

1. On https://search.google.com/search-console, add your production domain as a **URL prefix property**.
2. Verify ownership via DNS TXT record (Vercel exposes this in the domains UI).
3. Under **Sitemaps**, submit `https://<your-domain>/sitemap.xml`.
4. Expect indexing to start within 24–72 hours.

## Handy commands

```bash
# Local backend
cd interview-bank-api && mvn spring-boot:run

# Local frontend
cd interview-bank-web && npm run dev

# Run scraper against Neon
cd interview-bank-api && java -jar target/ib-api-scraper.jar --source=reddit --mode=recent

# Run extractor
cd interview-bank-api && java -jar target/ib-api-scraper.jar --mode=extract --batch=100
```

If anything is stuck, `/api/health` is your first check. If `/api/health` is green but pages are empty, the extractor hasn't run — trigger it manually via the Actions workflow.
