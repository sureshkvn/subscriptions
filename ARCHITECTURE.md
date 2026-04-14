# ARCHITECTURE.md — subscriptions

System architecture reference for the flexible subscription management service.

---

## System Overview

The `subscriptions` service is a **recurring billing platform** built as a single deployable JAR with two distinct runtime modes controlled by Spring profiles. It handles plan management, subscription lifecycle, coupon/discount logic, and an asynchronous billing pipeline that processes charges across multiple recurrence intervals (HOURLY through YEARLY).

```
┌─────────────────────────────────────────────────────────────────┐
│                        External Clients                         │
│              (web app, mobile app, internal tools)              │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTPS REST
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   Cloud Run Service (API)                        │
│               Spring Boot — default profile                      │
│                                                                 │
│  ┌──────────┐ ┌────────────┐ ┌────────────┐ ┌───────────────┐  │
│  │  /plans  │ │  /subs     │ │  /coupons  │ │  /billing     │  │
│  └──────────┘ └────────────┘ └────────────┘ └───────────────┘  │
│                                              ┌───────────────┐  │
│                                              │POST /internal/ │  │
│                                              │billing/process│  │
│                                              └───────┬───────┘  │
└──────────────────────────┬──────────────────────────┼───────────┘
                           │ JPA / HikariCP           │ Pub/Sub push
                           ▼                          ▼
              ┌─────────────────────┐    ┌────────────────────────┐
              │     PostgreSQL      │    │   GCP Pub/Sub           │
              │  (prod) / H2 (dev)  │    │   subscription.         │
              └─────────────────────┘    │   billing.due           │
                                         └────────────┬───────────┘
                                                      │ publishes
                                         ┌────────────┴───────────┐
                           ┌─────────────│  Cloud Run Job          │
                           │             │  (BillingDispatcherJob) │
              ┌────────────┴──────┐      │  Spring — job profile   │
              │  Cloud Scheduler  │─────▶└────────────────────────┘
              │  (2 jobs)         │
              └───────────────────┘
```

---

## Runtime Modes

The same compiled JAR runs in two distinct modes, selected by `SPRING_PROFILES_ACTIVE`:

| Mode | Profile | Entry point | Infrastructure |
|---|---|---|---|
| **API Service** | `default` | Embedded Tomcat (virtual threads) | Cloud Run Service |
| **Billing Dispatcher** | `job` | `BillingDispatcherJob implements ApplicationRunner` | Cloud Run Job |

The `application-job.yml` profile disables the web server (`spring.main.web-application-type=none`) and sets DDL mode to `validate` (never re-creates tables). This prevents the job from accidentally starting a web server or corrupting schema.

---

## Domain Model

The four domain verticals and their core entities:

```
Plan
├── id (UUID)
├── name, description
├── price (BigDecimal)
├── billingInterval (BillingInterval enum)
├── intervalCount (int — e.g. 2 + WEEKLY = "every 2 weeks")
├── trialDays (int)
└── status (ACTIVE | INACTIVE | ARCHIVED)

Subscription
├── id (UUID)
├── plan → Plan (many-to-one)
├── userId (String — external identity)
├── status (TRIAL | ACTIVE | PAST_DUE | CANCELLED | PAUSED)
├── currentPeriodStart / currentPeriodEnd (OffsetDateTime)
├── trialStart / trialEnd (OffsetDateTime, nullable)
└── cancelAtPeriodEnd (boolean)

SubscriptionCoupon  (join entity — coupon applied to subscription)
├── id (UUID)
├── subscription → Subscription
├── coupon → Coupon
├── appliedAt (OffsetDateTime)
├── discountSnapshot (BigDecimal) ← amount locked at application time
└── discountTypeSnapshot (PERCENTAGE | FIXED_AMOUNT)

Coupon
├── id (UUID)
├── code (unique String)
├── discountType (PERCENTAGE | FIXED_AMOUNT)
├── discountValue (BigDecimal)
├── maxRedemptions (Integer, nullable — null = unlimited)
├── currentRedemptions (int)
├── validFrom / validUntil (OffsetDateTime, nullable)
├── stackable (boolean)
└── status (ACTIVE | INACTIVE | EXPIRED)

BillingCycle
├── id (UUID)
├── subscription → Subscription
├── periodStart / periodEnd (OffsetDateTime)
├── originalAmount (BigDecimal, nullable — null for pre-discount records)
├── totalDiscountAmount (BigDecimal, nullable)
├── amount (BigDecimal — final charged amount)
├── status (PENDING | PAID | FAILED | REFUNDED)
├── paymentReference (String, nullable)
└── failureReason (String, nullable)
```

### Key Relationships

- A `Plan` can have many `Subscriptions`
- A `Subscription` can have many `SubscriptionCoupons` (active applied coupons)
- A `Subscription` can have many `BillingCycles` (one per billing period)
- A `Coupon` can be applied to many `Subscriptions` (tracked via `SubscriptionCoupon`)

### Billing Interval Enum

```
HOURLY → DAILY → WEEKLY → MONTHLY → QUARTERLY → YEARLY → CUSTOM
```

`CUSTOM` uses `intervalCount` as raw days. All other intervals: `intervalCount` multiplies the base unit (e.g., `intervalCount=2` + `MONTHLY` = every 2 months).

---

## Billing Pipeline

The billing pipeline is fully asynchronous, event-driven, and idempotent.

```
Cloud Scheduler (two jobs)
    │  HTTP POST trigger → Cloud Run Job
    ▼
BillingDispatcherJob  [Spring profile: job]
    │  SELECT subscriptions WHERE
    │    currentPeriodEnd ≤ NOW()
    │    AND status IN (ACTIVE, PAST_DUE)
    │    AND MOD(id, taskCount) = taskIndex   ← partition for parallelism
    │  → Publishes one BillingDueEvent per subscription
    ▼
GCP Pub/Sub topic: subscription.billing.due
    │  Push subscription → POST /internal/billing/process
    ▼
BillingEventProcessor  [API Service]
    │  Decodes Base64-encoded Pub/Sub push envelope
    │  Calls BillingService.processBillingCycle(event)
    ▼
BillingServiceImpl.processBillingCycle()
    │
    ├── 1. Idempotency check
    │      Attempts INSERT of PENDING BillingCycle with (subscription_id, period_start)
    │      Unique constraint uq_billing_sub_period_start → catches duplicate delivery
    │      DataIntegrityViolationException → return 200 (safe ack, already processed)
    │
    ├── 2. Discount calculation  [DiscountCalculator — parallel strategy]
    │      For each active SubscriptionCoupon:
    │        PERCENTAGE: discountAmount = originalAmount × (value / 100)
    │        FIXED_AMOUNT: discountAmount = min(value, originalAmount)
    │      Sum all individual discounts → totalDiscount
    │      finalAmount = max(originalAmount - totalDiscount, 0.00)
    │
    ├── 3. Charge via PaymentGateway
    │      Success → mark PAID, store paymentReference
    │      PaymentDeclinedException → mark FAILED, store failureReason
    │        → return 200 (ack — business decline, not infrastructure failure)
    │      PaymentGatewayException → return 500
    │        → Pub/Sub will retry (up to maxDeliveryAttempts=5)
    │
    └── 4. Advance subscription period
           subscription.currentPeriodStart = currentPeriodEnd
           subscription.currentPeriodEnd = currentPeriodEnd + interval
```

### Idempotency Guarantee

Pub/Sub guarantees at-least-once delivery. The unique constraint `uq_billing_sub_period_start` on `(subscription_id, period_start)` ensures double-charging never occurs even if the same event is delivered multiple times. The processor detects the duplicate by catching `DataIntegrityViolationException` and returns 200 (ack) to drain the duplicate from the queue.

### Ack / Nack Contract

| Outcome | HTTP Status | Pub/Sub action |
|---|---|---|
| Cycle already processed (duplicate) | 200 | Ack — drain the duplicate |
| Charge succeeded | 200 | Ack |
| Charge declined (`PaymentDeclinedException`) | 200 | Ack — cycle marked FAILED, subscription moved to PAST_DUE |
| Gateway infrastructure failure (`PaymentGatewayException`) | 500 | Nack — Pub/Sub retries |
| Unknown exception | 500 | Nack — Pub/Sub retries |

After `maxDeliveryAttempts=5` failed retries, the event is forwarded to the dead-letter topic `subscription.billing.failed`.

---

## Coupon & Discount Architecture

### Stackability Rules

When applying a coupon to a subscription, three rules are enforced (in this order):

| Rule | Description |
|---|---|
| **A** | A non-stackable coupon cannot be added if the subscription already has any active coupon |
| **B** | No coupon (stackable or not) can be added if the subscription already has a non-stackable coupon |
| **C** | The same coupon code cannot be applied to the same subscription twice |

These are enforced in `CouponServiceImpl.applyCoupon()` before persisting the `SubscriptionCoupon` join entity.

### Discount Snapshot

At application time, `SubscriptionCoupon` captures `discountSnapshot` (the resolved amount/percentage value) and `discountTypeSnapshot`. This means:

- If a coupon's `discountValue` is updated after application, existing subscriptions are not affected — they use the locked snapshot.
- If a coupon is expired or deactivated, already-applied subscription coupons continue to apply (the discount is already locked).

### Parallel (Not Sequential) Discount Computation

`DiscountCalculator` computes each coupon's discount independently against the original base price — **not** against a running total. This matters for percentage coupons:

```
originalAmount = $100
Coupon A: 10% → $10 discount (10% of $100, not 10% of $90)
Coupon B: 20% → $20 discount (20% of $100, not 20% of $90)
Total discount: $30
Final amount: $70
```

This design was chosen over sequential application because it is order-independent and predictable for customers.

---

## Deployment Architecture (GCP)

```
┌─────────────────────────────────────────────────────────────────┐
│                     GCP Project                                  │
│                                                                  │
│  ┌─────────────────────┐   ┌──────────────────────────────────┐ │
│  │  Cloud Run Service  │   │  Cloud Run Job                   │ │
│  │  (billing-api)      │◀──│  (billing-dispatcher)            │ │
│  │  min 1 / max N inst │   │  4 parallel tasks                │ │
│  │  virtual threads    │   │  partitioned by MOD(id,4)        │ │
│  └──────────┬──────────┘   └──────────────────────────────────┘ │
│             │                           ▲                        │
│             │ push delivery             │ HTTP trigger           │
│             ▼                           │                        │
│  ┌─────────────────────┐   ┌──────────────────────────────────┐ │
│  │  GCP Pub/Sub        │   │  Cloud Scheduler                 │ │
│  │  subscription.      │   │  billing-hourly-dispatcher       │ │
│  │  billing.due        │   │    cron: 0 * * * *              │ │
│  │                     │   │    BILLING_INTERVALS=HOURLY      │ │
│  │  DLQ:               │   │  billing-daily-dispatcher        │ │
│  │  subscription.      │   │    cron: 5 0 * * *              │ │
│  │  billing.failed     │   │    BILLING_INTERVALS=DAILY,...   │ │
│  └─────────────────────┘   └──────────────────────────────────┘ │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Cloud SQL (PostgreSQL)                                     │ │
│  │  Connected via Cloud SQL Auth Proxy (Cloud Run native)      │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Two Cloud Scheduler Jobs

The hourly and daily dispatchers are separate scheduler jobs targeting the same Cloud Run Job, but with different `BILLING_INTERVALS` environment overrides:

| Scheduler Job | Cron | `BILLING_INTERVALS` | Purpose |
|---|---|---|---|
| `billing-hourly-dispatcher` | `0 * * * *` | `HOURLY` | Catches hourly subscriptions each hour |
| `billing-daily-dispatcher` | `5 0 * * *` | `DAILY,WEEKLY,MONTHLY,YEARLY,CUSTOM` | Processes all non-hourly subscriptions once per day |

The 5-minute offset on the daily dispatcher (`5 0` not `0 0`) avoids contention at the exact midnight boundary.

### Cloud Run Job Parallelism

The job spec sets `parallelism: 4` and `completions: 4`. Each task receives its own `CLOUD_RUN_TASK_INDEX` (0–3). The dispatcher query uses `MOD(subscription.id, 4) = taskIndex` to partition the workload so no two tasks process the same subscription, and all subscriptions are covered across the 4 tasks.

---

## Application Layers (Vertical Slice)

Each domain is a fully self-contained vertical slice:

```
Controller  ←  HTTP boundary only. Returns ResponseEntity<ApiResponse<T>>.
    │            Never catches domain exceptions.
    ▼
Service (interface)
    ▼
ServiceImpl  ←  @Transactional(readOnly=true) at class.
    │            @Transactional on write methods.
    │            Throws ResourceNotFoundException (404) or BusinessRuleException (422).
    ▼
Repository   ←  JpaRepository. No business logic.
    ▼
Entity       ←  @Entity + Lombok @Builder. Enums as inner classes.
    ▼
DTO          ←  Java records. @Valid on Request types.
    ▼
Mapper       ←  MapStruct @Mapper(componentModel="spring").
```

All HTTP error mapping lives in `GlobalExceptionHandler` (`@RestControllerAdvice`). Controllers never wrap exceptions in try/catch.

---

## Payment Gateway Abstraction

`PaymentGateway` is a Spring interface. The active implementation is controlled by `@Profile`:

```java
@Profile("!prod")  StubPaymentGateway  — always succeeds (dev/test)
@Profile("prod")   StripePaymentGateway (or similar) — real charges
```

To add a real gateway:
1. Implement `PaymentGateway`
2. Annotate with `@Profile("prod")`
3. Mark `StubPaymentGateway` with `@Profile("!prod")`
4. Store API keys in GCP Secret Manager, injected via environment variables

---

## Technology Choices

| Concern | Choice | Rationale |
|---|---|---|
| Virtual threads | Java 21 (`spring.threads.virtual.enabled=true`) | High concurrency on I/O-bound billing operations without tuning thread pools |
| DTO mapping | MapStruct (compile-time) | Zero-reflection, type-safe, fast — avoids ModelMapper/Jackson reflection overhead |
| Async billing | Pub/Sub push | Decouples scan (job) from charge (API); built-in retry + dead-letter; no additional broker infrastructure needed |
| Two scheduler jobs | One per interval group | Isolates hourly latency from daily batch; hourly subscriptions charged on the hour without waiting for daily batch completion |
| Job parallelism | 4 tasks, MOD partition | Linear scale-out without distributed lock complexity; partitioning is deterministic (same subscription always → same task index) |
| Idempotency | DB unique constraint | Simplest possible guarantee; avoids Redis/distributed lock overhead; works even if the service restarts mid-billing |
| H2 dev / PostgreSQL prod | Profile-based | Schema is `create-drop` in dev (no migration needed), `validate` in prod (Flyway manages prod schema changes) |

---

## CI/CD

Three GitHub Actions workflows in `.github/workflows/`:

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | PR + push to `main` | `./mvnw verify` — compile, test, package |
| `deploy-api.yml` | Push to `main` | Build Docker image → push to Artifact Registry → `gcloud run deploy billing-api` |
| `deploy-job.yml` | Push to `main` | Build Docker image → push to Artifact Registry → `gcloud run jobs update billing-dispatcher` |

Authentication uses Workload Identity Federation (no long-lived service account keys stored in GitHub secrets).

---

## Security Considerations

- **No secrets in `application.yml`** — all connection strings, API keys, and GCP project IDs come from environment variables injected at deploy time (Cloud Run env vars or Secret Manager references).
- **Internal billing endpoint** — `POST /internal/billing/process` is not authenticated in the current implementation. In production, this endpoint should be restricted to Pub/Sub service account JWTs or placed behind a VPC-internal service URL to prevent unauthorized billing triggers.
- **Pub/Sub push authentication** — the Pub/Sub push subscription should be configured with an OIDC token audience matching the Cloud Run service URL so Cloud Run's built-in IAM can validate the caller.
