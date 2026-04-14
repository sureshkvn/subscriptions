# CLAUDE.md — subscriptions

This file gives Claude context for working in this repository. Read it before writing any code.

## What This Service Does

A **flexible subscription management API** supporting recurring billing across multiple intervals — HOURLY through YEARLY. It has two runtime modes baked into the same JAR:

1. **API Service** (default profile) — REST API for managing plans, subscriptions, coupons, and billing cycles. Runs as a Cloud Run Service.
2. **Billing Dispatcher Job** (`job` profile) — headless `ApplicationRunner` that scans for subscriptions due for billing and publishes `BillingDueEvent` messages to GCP Pub/Sub. Runs as a Cloud Run Job triggered by Cloud Scheduler.

The API service also receives Pub/Sub push deliveries at `POST /internal/billing/process` (`BillingEventProcessor`) and charges the payment gateway.

## Running Locally

```bash
./mvnw spring-boot:run
# API at http://localhost:8080/api
# Swagger UI at http://localhost:8080/api/swagger-ui.html
# H2 console at http://localhost:8080/api/h2-console
```

To run the billing dispatcher job locally:
```bash
SPRING_PROFILES_ACTIVE=job ./mvnw spring-boot:run
```

For Pub/Sub locally, start the emulator first:
```bash
gcloud beta emulators pubsub start
export PUBSUB_EMULATOR_HOST=localhost:8085
```

## Tech Stack

| Layer | Tech |
|---|---|
| Framework | Spring Boot 3.4 / Java 21 |
| Persistence | Spring Data JPA + Hibernate |
| Database (dev) | H2 in-memory (`create-drop`) |
| Database (prod) | PostgreSQL (via env vars) |
| DTO mapping | MapStruct 1.6 |
| Boilerplate | Lombok |
| Messaging | GCP Pub/Sub (spring-cloud-gcp 5.10) |
| API docs | SpringDoc OpenAPI 3 / Swagger UI |
| Tests | JUnit 5 + Mockito + `@WebMvcTest` |
| Concurrency | Java 21 virtual threads |
| Build | Maven (`./mvnw`) |

## Repository Structure

```
subscriptions/
├── src/main/java/com/sureshkvn/subscriptions/
│   ├── SubscriptionsApplication.java
│   ├── config/
│   │   ├── OpenApiConfig.java          ← Swagger / OpenAPI setup
│   │   └── PubSubConfig.java           ← ObjectMapper bean for Pub/Sub serialisation
│   ├── common/
│   │   ├── exception/
│   │   │   ├── GlobalExceptionHandler.java    ← @RestControllerAdvice — all HTTP error mapping
│   │   │   ├── ResourceNotFoundException.java ← 404
│   │   │   └── BusinessRuleException.java     ← 422
│   │   └── response/
│   │       └── ApiResponse.java        ← Generic response envelope
│   ├── plan/                           ← Plan domain vertical slice
│   ├── subscription/                   ← Subscription domain vertical slice
│   ├── coupon/                         ← Coupon domain vertical slice
│   └── billing/                        ← Billing domain vertical slice
│       ├── job/BillingDispatcherJob.java       ← Cloud Run Job entry-point
│       ├── messaging/
│       │   ├── BillingDueEvent.java            ← Pub/Sub event record
│       │   ├── BillingEventPublisher.java      ← Publishes to topic
│       │   ├── BillingEventProcessor.java      ← Receives push delivery
│       │   └── PubSubPushEnvelope.java         ← Pub/Sub push wrapper DTO
│       └── payment/
│           ├── PaymentGateway.java             ← Interface (swap for Stripe etc.)
│           └── StubPaymentGateway.java         ← Dev/test stub
├── src/main/resources/
│   ├── application.yml                 ← Default profile (H2, dev settings)
│   └── application-job.yml            ← Job profile (no web server, validate DDL)
├── infra/
│   ├── cloud-run-job.yaml             ← Cloud Run Job spec (4 parallel tasks)
│   ├── cloud-scheduler.yaml           ← Two Cloud Scheduler jobs (hourly + daily)
│   └── pubsub-setup.sh                ← One-time Pub/Sub topic/subscription setup
└── .github/workflows/
    ├── ci.yml                          ← Build + test on PR and push to main
    ├── deploy-api.yml                  ← Build → push image → deploy Cloud Run Service
    └── deploy-job.yml                  ← Build → push image → update Cloud Run Job
```

## Vertical Slice Pattern

Each domain owns its full stack. **Always follow this structure when adding to a domain or creating a new one:**

```
<domain>/
├── controller/<Domain>Controller.java    ← HTTP only. Returns ResponseEntity<ApiResponse<T>>
├── service/<Domain>Service.java          ← Interface
├── service/impl/<Domain>ServiceImpl.java ← @Transactional(readOnly=true) at class level
├── repository/<Domain>Repository.java    ← JpaRepository, no business logic
├── model/<Domain>.java                   ← @Entity, Lombok @Builder, enums as inner classes
├── dto/<Domain>Request.java              ← Java record + @Valid Bean Validation
├── dto/<Domain>Response.java             ← Java record (outbound)
└── mapper/<Domain>Mapper.java            ← MapStruct @Mapper(componentModel="spring")
```

## Code Conventions

**Response envelope** — every endpoint returns `ResponseEntity<ApiResponse<T>>`:
```java
return ResponseEntity.ok(ApiResponse.success("Plan created", planResponse));
return ResponseEntity.status(201).body(ApiResponse.success("Created", response));
```

**Exception handling** — services throw domain exceptions; controllers never catch:
```java
throw new ResourceNotFoundException("Plan", "id", id);   // → 404
throw new BusinessRuleException("Plan is not active");    // → 422
```

**`@Transactional` pattern** — class-level `readOnly=true`, method-level override for writes:
```java
@Transactional(readOnly = true)   // on the class
public class FooServiceImpl { ... }

@Transactional                    // on write methods
public FooResponse create(...) { ... }
```

**MapStruct mappers** — use `componentModel = "spring"` and inject via `@RequiredArgsConstructor`. For partial updates use `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)`.

**DTOs are Java records** — never use mutable POJOs for request/response types.

**Lombok annotation order** on entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`. The `@Builder.Default` annotation is needed for fields with initialised defaults (e.g., `status`).

**`@Column` on every entity field** — always be explicit. Never rely on Hibernate's column name inference alone.

## Billing Pipeline

```
Cloud Scheduler (hourly / daily)
    │  HTTP POST trigger
    ▼
Cloud Run Job (BillingDispatcherJob)   — Spring profile: job
    │  Queries subscriptions WHERE currentPeriodEnd ≤ NOW AND status = ACTIVE
    │  Partitioned: MOD(subscription.id, taskCount) = taskIndex
    │  Publishes BillingDueEvent per subscription
    ▼
GCP Pub/Sub topic: subscription.billing.due
    │  Push delivery (Pub/Sub push subscription)
    ▼
Cloud Run Service → POST /api/internal/billing/process (BillingEventProcessor)
    │  Decodes Base64 Pub/Sub envelope
    │  Calls BillingService.processBillingCycle(event)
    │    → Idempotency check (unique constraint on subscription_id + period_start)
    │    → Compute discounts (DiscountCalculator — parallel strategy)
    │    → Create PENDING BillingCycle
    │    → Charge via PaymentGateway
    │    → Mark PAID / FAILED
    │    → Advance subscription.currentPeriodEnd
    │  Returns 200 (ack) or 500 (nack → Pub/Sub retries)
    ▼
Dead-letter topic: subscription.billing.failed  (after maxDeliveryAttempts=5)
```

## Two Cloud Scheduler Jobs

| Job | Schedule | `BILLING_INTERVALS` env var |
|---|---|---|
| `billing-hourly-dispatcher` | `0 * * * *` (every hour) | `HOURLY` |
| `billing-daily-dispatcher` | `5 0 * * *` (00:05 UTC) | `DAILY,WEEKLY,MONTHLY,YEARLY,CUSTOM` |

Both trigger the same Cloud Run Job (`billing-dispatcher`); the `BILLING_INTERVALS` env override controls what each run processes.

## Payment Gateway

`PaymentGateway` is an interface. `StubPaymentGateway` always returns success in dev. To swap in a real gateway (e.g., Stripe):
1. Implement `PaymentGateway`
2. Annotate with `@Profile("prod")` and annotate `StubPaymentGateway` with `@Profile("!prod")`
3. Never add real payment credentials to `application.yml` — use Secret Manager

## Testing

```bash
./mvnw test                  # run all tests
./mvnw test -pl . -Dtest=BillingServiceImplTest   # single test class
```

Test layers:
- `@WebMvcTest` — controller tests. Mock all service dependencies with `@MockBean`.
- `@ExtendWith(MockitoExtension.class)` — pure unit tests for service/calculator logic.
- No `@SpringBootTest` yet (reserved for integration smoke tests).

## Common Tasks

**Add a new field to an existing entity:**
1. Add the field to the `@Entity` class with `@Column`
2. Update the corresponding `Request` record (add the field + validation)
3. Update the `Response` record
4. Update the MapStruct mapper if the field name differs between entity and DTO
5. H2 recreates the schema on restart (`create-drop`) — no migration needed in dev
6. For prod: write a Flyway migration script

**Add a new API endpoint:**
1. Add method to the service interface
2. Implement in `*ServiceImpl` with appropriate `@Transactional`
3. Add handler in the controller returning `ResponseEntity<ApiResponse<T>>`
4. Add `@WebMvcTest` coverage

**Run the billing job locally against a test subscription:**
```bash
# Insert a subscription with currentPeriodEnd in the past via H2 console, then:
SPRING_PROFILES_ACTIVE=job BILLING_INTERVALS=DAILY ./mvnw spring-boot:run
```

**Inspect dead-lettered billing events:**
```bash
gcloud pubsub subscriptions pull subscription.billing.failed-sub --limit=10 --auto-ack
```
