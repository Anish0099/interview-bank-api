#!/usr/bin/env bash
# One-shot Cloud Run deploy for interview-bank-api.
#
# Usage:
#   cd deploy/cloudrun
#   ./deploy.sh
#
# Prereqs:
#   - gcloud CLI installed and `gcloud auth login` done
#   - env vars below either set in your shell or exported from a local
#     .env (this script sources ../../.env if present)
#
# What this does:
#   1. Enables the Cloud Run + Cloud Build APIs (idempotent)
#   2. Builds the image directly from source via Cloud Build (no local Docker needed)
#   3. Deploys to Cloud Run with the runtime env vars from your shell/.env

set -euo pipefail

# ----- editable defaults -----
GCP_PROJECT="${GCP_PROJECT:-}"                     # required — your Google Cloud project id
REGION="${GCP_REGION:-asia-south1}"                # Mumbai; asia-southeast1 (Singapore) is a fine alternative
SERVICE_NAME="${GCP_SERVICE_NAME:-interview-bank-api}"

# ----- load local .env if present, so DB_URL/DB_USER/DB_PASSWORD/etc are available -----
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
if [[ -f "$REPO_ROOT/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "$REPO_ROOT/.env"
  set +a
fi

if [[ -z "$GCP_PROJECT" ]]; then
  echo "GCP_PROJECT is required. Export it or add GCP_PROJECT=... to your .env." >&2
  exit 1
fi

: "${DB_URL:?DB_URL is required}"
: "${DB_USER:?DB_USER is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${CORS_ALLOWED_ORIGINS:?CORS_ALLOWED_ORIGINS is required}"

echo "==> Enabling required APIs in project $GCP_PROJECT (idempotent)"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
  --project "$GCP_PROJECT"

echo "==> Deploying $SERVICE_NAME to Cloud Run in $REGION"
# --source uploads the current directory to Cloud Build, which uses our
# Dockerfile to build the image and hands it back to Cloud Run. No local
# Docker required.
gcloud run deploy "$SERVICE_NAME" \
  --project "$GCP_PROJECT" \
  --region "$REGION" \
  --source "$REPO_ROOT" \
  --allow-unauthenticated \
  --port 8080 \
  --memory 512Mi \
  --cpu 1 \
  --min-instances 0 \
  --max-instances 1 \
  --concurrency 40 \
  --timeout 60 \
  --cpu-boost \
  --set-env-vars \
"SPRING_PROFILES_ACTIVE=prod,\
DB_URL=$DB_URL,\
DB_USER=$DB_USER,\
DB_PASSWORD=$DB_PASSWORD,\
CORS_ALLOWED_ORIGINS=$CORS_ALLOWED_ORIGINS,\
REDDIT_CLIENT_ID=${REDDIT_CLIENT_ID:-},\
REDDIT_CLIENT_SECRET=${REDDIT_CLIENT_SECRET:-},\
REDDIT_USER_AGENT=${REDDIT_USER_AGENT:-interview-bank/1.0},\
GROQ_API_KEY=${GROQ_API_KEY:-},\
GEMINI_API_KEY=${GEMINI_API_KEY:-},\
TAKEDOWN_CONTACT_EMAIL=${TAKEDOWN_CONTACT_EMAIL:-}"

echo
echo "==> Live URL:"
gcloud run services describe "$SERVICE_NAME" \
  --project "$GCP_PROJECT" \
  --region "$REGION" \
  --format 'value(status.url)'
