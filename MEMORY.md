# MEMORY.md — subscriptions

Persistent project memory. Update this file when important decisions are made, bugs are fixed, or architectural patterns are established.

---

## Parallel (Not Sequential) Discount Computation

**Decision:** `DiscountCalculator` computes each coupon's discount independently against the **original base price**, not against a running total.

**Why:** Sequential computation is order-dependent. If Coupon A (10%) is applied before Coupon B (10%), the result differs from B-before-A when they are percentage-based. Customers cannot predict their final price without knowing application order.

**Parallel approach:**
```
originalAmount = $100
Coupon A: 10% of $100 = $10
Coupon B: 20% of $100 = $20
Total discount = $30
Final = max($100 - $30, 0.00) = $70
```

**Implication:** Adding more percentage coupons beyond 100% combined cannot produce a negative charge — `max(..., 0.00)` caps the floor. The `totalDiscountAmount` can exceed `originalAmount` conceptually (100%+), but `amount` is always ≥ 0.00.

**Do not change this to sequential without revisiting the customer-facing pricing display and any existing subscriptions with multiple stackable coupons.** A change here would retroactively produce different effective amounts from what was shown at coupon-application time.

---

## Idempotency via Unique Constraint (Not Application Lock)

**Decision:** The billing idempotency guarantee is enforced by the database unique constraint `uq_billing_sub_period_start` on `(subscription_id, period_start)` — not by a distributed lock, Redis check, or application-level flag.

**Why this was chosen:**
- Pub/Sub guarantees at-least-once delivery — duplicate events are inevitable at scale
- DB unique constraint is the simplest, most reliable guard: it is transactional, survives restarts, and requires no additional infrastructure
- A Redis cache could miss an entry (TTL expiry, eviction), while the DB constraint never forgets

**How it works in code:**
```java
// BillingServiceImpl
try {
    billingCycleRepository.save(pendingCycle);
} catch (DataIntegrityViolationException e) {
    // Already processed — ack the duplicate and return
    return;
}
```

**Do not add a pre-check like `if (repository.existsBy...)` before the insert.** Pre-checks create a check-then-act race condition between duplicate deliveries processed in parallel. The exception-based approach is the correct pattern.

---

## Discount Snapshot at Application Time

**Decision:** `SubscriptionCoupon` stores `discountSnapshot` and `discountTypeSnapshot` — the resolved discount value locked at the moment the coupon was applied.

**Why this matters:** Coupons are mutable (admins can update `discountValue`). Without snapshots, updating a coupon would silently change the discount on all existing subscriptions retroactively. The snapshot ensures a subscription's billing amount is predictable for its lifetime.

**Implication:** Deactivating or expiring a coupon does **not** remove the discount from already-applied subscriptions. If you need to revoke a coupon from a specific subscription, you must delete the `SubscriptionCoupon` join record directly.

**Future consideration:** If an admin correction use case arises (e.g., a coupon was applied by mistake), add an explicit `removeAppliedCoupon` endpoint in `CouponService` rather than exposing the join table directly.

---

## Stackability Rules — Order of Enforcement

**Rules A, B, C** are checked in `CouponServiceImpl.applyCoupon()` in this specific order:

1. **Rule A:** If the new coupon is non-stackable AND the subscription already has any active coupon → reject
2. **Rule B:** If the subscription already has a non-stackable coupon → reject any new coupon
3. **Rule C:** If the same coupon code is already applied to this subscription → reject (no duplicates)

**Why this order matters:** Rules A and B are checked first because they reflect broader policy (stackability intent). Rule C is a narrower deduplication check. Checking C first would allow two non-stackable coupons if they happened to be different codes.

---

## BillingCycle: Nullable `originalAmount` / `totalDiscountAmount`

**Decision:** `BillingCycle.originalAmount` and `BillingCycle.totalDiscountAmount` are nullable (`@Column(nullable = true)`).

**Why:** These fields were added after initial deployment when discount support was introduced. Existing `BillingCycle` rows (created before the discount feature) have `NULL` in these columns. Making them non-nullable would require a backfill migration or would break reads of historical records.

**Pattern for consumers:** Check for null before using these fields:
```java
if (cycle.getOriginalAmount() != null) {
    // discount-aware cycle — show breakdown
} else {
    // legacy cycle — originalAmount == amount, no discount
}
```

**Do not change to `nullable = false` without running a backfill migration** that sets `originalAmount = amount` and `totalDiscountAmount = 0` for all existing rows.

---

## PaymentGateway: @Profile Swap Pattern

**Decision:** `PaymentGateway` is a Spring interface. Implementations are selected by `@Profile`.

**The pattern:**
```java
@Component
@Profile("!prod")
public class StubPaymentGateway implements PaymentGateway { ... }

@Component
@Profile("prod")
public class StripePaymentGateway implements PaymentGateway { ... }
```

**Why not a flag/config property:** `@Profile` provides compile-time safety — if you forget to implement the interface fully, the build fails. A runtime flag just throws `NullPointerException` at charge time.

**What `StubPaymentGateway` does:** Always returns a successful `PaymentResult` with a generated UUID as the reference. It does **not** simulate declines or gateway failures — add a test-only profile or constructor injection if you need to test failure paths.

**Credentials:** Never add Stripe (or any) API keys to `application.yml`. Use GCP Secret Manager, injected as environment variables. The `PaymentGateway` implementation should read them from `@Value("${STRIPE_SECRET_KEY}")` which resolves from env at startup.

---

## Ack/Nack Contract — Why Declined Payments Return 200

**Decision:** A `PaymentDeclinedException` (card declined, insufficient funds) returns HTTP 200 to Pub/Sub, not 500.

**Reasoning:** A declined payment is a **business outcome**, not an infrastructure failure. The payment gateway responded correctly — it just said no. Returning 500 would cause Pub/Sub to retry the same declined charge up to 5 times, which:
- Wastes money on gateway API calls
- Could irritate the customer with multiple decline notifications
- Does not change the outcome (the card is still declined)

The correct response is to **acknowledge** the event (200), mark the `BillingCycle` as `FAILED`, and move the subscription to `PAST_DUE`. Retry logic for declined payments should be a separate business-initiated retry (e.g., after the customer updates their payment method), not Pub/Sub retries.

**Contrast with `PaymentGatewayException`:** This represents an infrastructure failure (gateway timeout, 5xx from Stripe). Returning 500 here is correct — Pub/Sub will retry and the charge may succeed when the gateway recovers.

---

## Cloud Run Job Partitioning — Why MOD(id, taskCount)

**Decision:** Subscriptions are partitioned across Cloud Run Job tasks using `MOD(subscription.id, taskCount) = taskIndex`.

**Why this works:** UUIDs stored as integers (or their hash) produce reasonably uniform distribution across modulo buckets. No two tasks will process the same subscription, and together all tasks cover the full set.

**Why not a range-based partition:** Range partitioning (e.g., first 25% of IDs to task 0) requires knowing the min/max ID distribution, which is skewed for UUIDs. MOD-based partitioning is stateless and self-correcting.

**Task count is read from `CLOUD_RUN_TASK_COUNT` env var** (injected by Cloud Run). The job code must never hardcode `4` — it reads the env var so that changing `parallelism` in the job spec automatically adjusts the partition math without a code change.

**Failure of one task:** If task 2 fails, its partition of subscriptions is not processed in that run. They will be picked up in the next scheduler invocation. This is acceptable for billing workloads where a one-run delay is tolerable.

---

## Two Scheduler Jobs vs. One with All Intervals

**Decision:** Two Cloud Scheduler jobs (`billing-hourly-dispatcher` and `billing-daily-dispatcher`) instead of one.

**Why not one job for all intervals:**
- Hourly subscriptions need to be charged every hour, on the hour
- Running a full scan of all intervals every hour processes DAILY/WEEKLY/MONTHLY subscriptions 23 extra times per day, adding unnecessary DB load
- The two-job design isolates the hourly workload so a slow daily batch (many monthly renewals on the 1st of the month) does not delay hourly billings

**The `BILLING_INTERVALS` env var** is the only difference between the two scheduler invocations — both trigger the same Cloud Run Job image with a different env override. No code branching needed.

---

## Virtual Threads and Connection Pool Sizing

`spring.threads.virtual.enabled=true` enables Java 21 virtual threads for all Spring MVC request handling. This means a request blocked on DB I/O parks the virtual thread rather than blocking a platform thread.

**Implication for HikariCP:** With virtual threads, the bottleneck shifts from thread pool size to DB connection pool size. The default HikariCP `maximum-pool-size` is 10. Under heavy billing webhook load (many Pub/Sub push deliveries in parallel), this may be the limiting factor.

**If you see JDBC connection timeout under load:** Increase `spring.datasource.hikari.maximum-pool-size` in `application.yml`. Start with 20–50 and tune based on PostgreSQL `max_connections`.

**Do not set `maximum-pool-size` to a very large number** (e.g., 500) — PostgreSQL has its own `max_connections` limit and each connection consumes server memory.

---

## H2 Dev / PostgreSQL Prod Schema Divergence Risk

`application.yml` uses `spring.jpa.hibernate.ddl-auto=create-drop` with H2. This means the schema is recreated from entity annotations on every restart — no migration needed in development.

**Risk:** If an entity change works with H2's lenient SQL dialect but is incompatible with PostgreSQL syntax, you won't discover the problem until you deploy. Common traps:
- H2 accepts some data types PostgreSQL rejects (or vice versa)
- H2 is case-insensitive for identifiers; PostgreSQL is case-sensitive for quoted identifiers
- H2 auto-increments work differently from PostgreSQL sequences

**Recommendation:** Before any production deployment that touches entities, verify the generated DDL against PostgreSQL:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--spring.jpa.show-sql=true --spring.jpa.hibernate.ddl-auto=create"
```
Review the DDL output for PostgreSQL compatibility. Add a Flyway migration for any schema change going to production.

---

## Pub/Sub Setup Is One-Time Manual

`infra/pubsub-setup.sh` creates the Pub/Sub topic, push subscription, and dead-letter topic. This script is idempotent but must be run **once** per GCP environment (dev, staging, prod).

**Dead-letter configuration:** `maxDeliveryAttempts=5` on the push subscription. After 5 failed nacks, the event moves to `subscription.billing.failed` topic. Monitor this topic — events here represent subscriptions that could not be charged after 5 infrastructure retries.

**Push endpoint URL** in the subscription config (`infra/pubsub-setup.sh`) must match the deployed Cloud Run Service URL. Update it when the service URL changes (e.g., after deploying to a new GCP project or region).

---

## Known Limitations / Future Work

- **No webhook notifications** — there is no outbound event when a subscription renews, a payment fails, or a trial ends. Add a `WebhookService` that publishes to a `subscription.events` topic, then external systems (email, CRM) can react.
- **No dunning logic** — a `PAST_DUE` subscription stays `PAST_DUE` indefinitely. There is no automated retry schedule (e.g., retry 3 days later), grace period enforcement, or automatic cancellation after N failed attempts.
- **No proration** — upgrading or downgrading a plan mid-cycle does not produce a prorated charge. The next `BillingCycle` will use the new plan price in full.
- **No metered/usage-based billing** — `BillingCycle.amount` is computed from `Plan.price` only. Per-seat or usage-based overages are not modeled.
- **Internal billing endpoint is unauthenticated** — `POST /internal/billing/process` does not validate the Pub/Sub OIDC token. In production, configure Pub/Sub push authentication and validate the JWT on this endpoint.
- **No admin API** — there is no endpoint for bulk operations like cancelling all subscriptions for a given user, or pausing billing for all subscriptions on a plan. These would need to be added to `SubscriptionService`.
- **No soft-delete on subscriptions** — cancelled subscriptions use `status=CANCELLED` (not a deleted_at timestamp). Querying "all subscriptions including cancelled" vs "active only" requires explicit status filtering.
