# interview-bank-api

Spring Boot 3.3 / Java 21 backend for the Interview Question Bank.

## Local development

Requirements: Java 21, Maven 3.9+, a Postgres 15+ database with the `vector` and `pg_trgm` extensions (Neon works out of the box).

```bash
cp .env.example .env   # then fill in the values
export $(grep -v '^#' .env | xargs)
mvn -B spring-boot:run
```

Health check: `curl http://localhost:8080/api/health`

## Environment variables

| Var | Purpose |
|-----|---------|
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://ep-xxx.neon.tech/interview_bank?sslmode=require` |
| `DB_USER` / `DB_PASSWORD` | Neon credentials |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list; include the Vercel URL and localhost:3000 |
| `REDDIT_CLIENT_ID` / `REDDIT_CLIENT_SECRET` / `REDDIT_USER_AGENT` | Reddit script-type app credentials |
| `GROQ_API_KEY` | Groq Cloud API key for llama-3.3-70b-versatile |
| `GEMINI_API_KEY` | Google AI Studio key for text-embedding-004 |
| `TAKEDOWN_CONTACT_EMAIL` | Displayed on `/api/takedown` responses |

## Building

```bash
mvn -B package
```

Produces two jars in `target/`:
- `ib-api-app.jar` — the web server (Render deploys this via the Dockerfile).
- `ib-api-scraper.jar` — the CLI used by the GitHub Actions cron workflow.

## Deploying

The default target is **Google Cloud Run** — see [`SETUP.md`](SETUP.md#6-deploy-the-backend-to-google-cloud-run) and the one-shot script in [`deploy/cloudrun/deploy.sh`](deploy/cloudrun/deploy.sh). Cold starts are ~1–3s and the free tier is permanent.

**Render** is kept as a fallback via [`render.yaml`](render.yaml) if you don't want to attach a billing account to Google Cloud. Its free tier idles the service after 15 minutes, so add an [UptimeRobot](https://uptimerobot.com/) HTTPS monitor pointing at `https://<your-render-host>/api/health` at a 5-minute interval to soften the cold-sleep.

## Layout

See the top-level project spec for full package layout. Phase-by-phase build progress lives in this repo's git log.
