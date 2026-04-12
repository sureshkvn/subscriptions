#!/usr/bin/env bash
# =============================================================================
# pubsub-setup.sh
#
# Creates the Pub/Sub topics and subscriptions required by the billing pipeline.
# Run once per GCP project (or via CI during environment provisioning).
#
# Prerequisites:
#   - gcloud CLI authenticated with a service account that has the
#     roles/pubsub.admin role on the project.
#   - PROJECT_ID and API_SERVICE_URL environment variables must be set.
#
# Usage:
#   export PROJECT_ID=my-gcp-project
#   export API_SERVICE_URL=https://subscriptions-api-xxxx-uc.a.run.app
#   ./infra/pubsub-setup.sh
# =============================================================================

set -euo pipefail

: "${PROJECT_ID:?ERROR: PROJECT_ID environment variable is not set}"
: "${API_SERVICE_URL:?ERROR: API_SERVICE_URL environment variable is not set}"

BILLING_DUE_TOPIC="subscription.billing.due"
BILLING_DUE_SUB="subscription.billing.due-sub"
BILLING_FAILED_TOPIC="subscription.billing.failed"
BILLING_FAILED_SUB="subscription.billing.failed-sub"

# Maximum delivery attempts before a message is sent to the dead-letter topic.
MAX_DELIVERY_ATTEMPTS=5

echo "==> Setting up Pub/Sub for project: ${PROJECT_ID}"

# ---------------------------------------------------------------------------
# 1. Create the main billing-due topic
# ---------------------------------------------------------------------------
echo "Creating topic: ${BILLING_DUE_TOPIC}"
gcloud pubsub topics create "${BILLING_DUE_TOPIC}" \
    --project="${PROJECT_ID}" 2>/dev/null || echo "  (already exists)"

# ---------------------------------------------------------------------------
# 2. Create the dead-letter topic
# ---------------------------------------------------------------------------
echo "Creating dead-letter topic: ${BILLING_FAILED_TOPIC}"
gcloud pubsub topics create "${BILLING_FAILED_TOPIC}" \
    --project="${PROJECT_ID}" 2>/dev/null || echo "  (already exists)"

# ---------------------------------------------------------------------------
# 3. Create the push subscription pointing at the API service
# ---------------------------------------------------------------------------
PUSH_ENDPOINT="${API_SERVICE_URL}/api/internal/billing/process"
echo "Creating push subscription: ${BILLING_DUE_SUB}"
echo "  Endpoint: ${PUSH_ENDPOINT}"

gcloud pubsub subscriptions create "${BILLING_DUE_SUB}" \
    --topic="${BILLING_DUE_TOPIC}" \
    --project="${PROJECT_ID}" \
    --push-endpoint="${PUSH_ENDPOINT}" \
    --push-auth-service-account="pubsub-invoker@${PROJECT_ID}.iam.gserviceaccount.com" \
    --ack-deadline=60 \
    --min-retry-delay=10s \
    --max-retry-delay=600s \
    --dead-letter-topic="${BILLING_FAILED_TOPIC}" \
    --max-delivery-attempts="${MAX_DELIVERY_ATTEMPTS}" \
    2>/dev/null || echo "  (already exists)"

# ---------------------------------------------------------------------------
# 4. Create a pull subscription on the dead-letter topic for manual inspection
# ---------------------------------------------------------------------------
echo "Creating dead-letter pull subscription: ${BILLING_FAILED_SUB}"
gcloud pubsub subscriptions create "${BILLING_FAILED_SUB}" \
    --topic="${BILLING_FAILED_TOPIC}" \
    --project="${PROJECT_ID}" \
    --ack-deadline=60 \
    2>/dev/null || echo "  (already exists)"

# ---------------------------------------------------------------------------
# 5. Grant the Pub/Sub service account permission to attach dead-letter messages
# ---------------------------------------------------------------------------
PROJECT_NUMBER=$(gcloud projects describe "${PROJECT_ID}" --format="value(projectNumber)")
PUBSUB_SA="service-${PROJECT_NUMBER}@gcp-sa-pubsub.iam.gserviceaccount.com"

echo "Granting dead-letter permissions to ${PUBSUB_SA}"
gcloud pubsub topics add-iam-policy-binding "${BILLING_FAILED_TOPIC}" \
    --project="${PROJECT_ID}" \
    --member="serviceAccount:${PUBSUB_SA}" \
    --role="roles/pubsub.publisher"

gcloud pubsub subscriptions add-iam-policy-binding "${BILLING_DUE_SUB}" \
    --project="${PROJECT_ID}" \
    --member="serviceAccount:${PUBSUB_SA}" \
    --role="roles/pubsub.subscriber"

echo ""
echo "==> Pub/Sub setup complete!"
echo "    Topic:             projects/${PROJECT_ID}/topics/${BILLING_DUE_TOPIC}"
echo "    Push subscription: projects/${PROJECT_ID}/subscriptions/${BILLING_DUE_SUB}"
echo "    Dead-letter topic: projects/${PROJECT_ID}/topics/${BILLING_FAILED_TOPIC}"
