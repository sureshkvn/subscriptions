# Subscriptions API

A flexible subscription management REST API built with **Spring Boot 3.4** and **Java 21**, supporting recurring billing intervals from hourly to monthly and beyond, with a stackable coupon and discount engine.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Coupon & Discount Logic](#coupon--discount-logic)
- [Configuration](#configuration)
- [Development](#development)
- [Testing](#testing)
- [Contributing](#contributing)

---

## Features

- **Flexible Billing Intervals** — hourly, daily, weekly, monthly, yearly, or custom
- **Full Subscription Lifecycle** — pending → trialing → active → paused → cancelled
- **Plan Management** — draft, activate, and archive subscription plans
- **Coupon & Discount Engine** — percentage and fixed-amount coupons with configurable stackability
- **Stacked Coupon Support** — apply multiple coupons to a single subscription with parallel discount computation
- **Discount Snapshots** — discount value locked at application time; immune to later coupon edits
- **Discount-Aware Billing Cycles** — every cycle records original amount, total discount, and final charged amount separately
- **Billing Cycle Tracking** — immutable audit trail of all billing events
- **Virtual Threads** — Java 21 Project Loom for improved concurrency under load
- **OpenAPI / Swagger UI** — interactive API docs out of the box
- **H2 In-Memory DB** — zero-config local development (swap for PostgreSQL in production)
- **Structured Error Responses** — consistent `ApiResponse<T>` envelope across all endpoints

---

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full architecture reference.

The application follows a **vertical slice / domain-driven layered** structure:

```
src/main/java/com/sureshkvn/subscriptions/
├── config/          # Spring configuration beans (OpenAPI, web)
├── common/          # Cross-cutting: exceptions, response envelope, utilities
├── plan/            # Plan domain: model, dto, mapper, repository, service, controller
├── subscription/    # Subscription domain (same structure as plan)
├── coupon/          # Coupon domain: stackability rules, discount snapshots, apply/remove
└── billing/         # Billing cycle domain — discount-aware amount fields
```

Each domain contains its own `model/`, `dto/`, `mapper/`, `repository/`, `service/`, and `controller/` packages. No cross-domain service dependencies — the `billing` domain reads coupon snapshots from `SubscriptionCoupon` directly rather than calling `CouponService`.

---

## Getting Started

### Prerequisites

| Tool  | Version |
|-------|---------|
| JDK   | 21+     |
| Maven | 3.9+    |

### Run Locally

```bash
git clone https://github.com/sureshkvn/subscriptions.git
cd subscriptions
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080/api`.

### H2 Console

Browse the in-memory database at `http://localhost:8080/api/h2-console`

- **JDBC URL:** `jdbc:h2:mem:subscriptionsdb`
- **Username:** `sa`
- **Password:** *(empty)*

### Swagger UI

Interactive API docs at `http://localhost:8080/api/swagger-ui.html`

---

## API Endpoints

### Plans

| Method | Endpoint                    | Description               |
|--------|-----------------------------|---------------------------|
| POST   | `/v1/plans`                 | Create a new plan         |
| GET    | `/v1/plans`                 | List all plans            |
| GET    | `/v1/plans/{id}`            | Get plan by ID            |
| PUT    | `/v1/plans/{id}`            | Update plan               |
| PATCH  | `/v1/plans/{id}/archive`    | Archive a plan            |
| DELETE | `/v1/plans/{id}`            | Delete plan (draft only)  |

### Subscriptions

| Method | Endpoint                                           | Description                         |
|--------|----------------------------------------------------|-------------------------------------|
| POST   | `/v1/subscriptions`                                | Create a subscription               |
| GET    | `/v1/subscriptions`                                | List subscriptions                  |
| GET    | `/v1/subscriptions/{id}`                           | Get subscription by ID              |
| PATCH  | `/v1/subscriptions/{id}/activate`                  | Activate a pending subscription     |
| PATCH  | `/v1/subscriptions/{id}/pause`                     | Pause an active subscription        |
| PATCH  | `/v1/subscriptions/{id}/resume`                    | Resume a paused subscription        |
| PATCH  | `/v1/subscriptions/{id}/cancel`                    | Cancel a subscription               |
| POST   | `/v1/subscriptions/{id}/coupons`                   | Apply a coupon to a subscription    |
| DELETE | `/v1/subscriptions/{id}/coupons/{couponCode}`      | Remove a coupon from a subscription |
| GET    | `/v1/subscriptions/{id}/coupons`                   | List coupons applied to a subscription |

### Coupons

| Method | Endpoint                        | Description                              |
|--------|---------------------------------|------------------------------------------|
| POST   | `/v1/coupons`                   | Create a new coupon                      |
| GET    | `/v1/coupons`                   | List all coupons                         |
| GET    | `/v1/coupons/{id}`              | Get coupon by ID                         |
| GET    | `/v1/coupons/code/{code}`       | Look up coupon by code                   |
| PUT    | `/v1/coupons/{id}`              | Update a coupon                          |
| PATCH  | `/v1/coupons/{id}/deactivate`   | Deactivate a coupon (stops new applications) |
| DELETE | `/v1/coupons/{id}`              | Delete a coupon (unused only)            |

### Billing

| Method | Endpoint                                        | Description                                        |
|--------|-------------------------------------------------|----------------------------------------------------|
| GET    | `/v1/billing/subscriptions/{id}/cycles`         | List billing cycles (includes discount breakdown)  |
| GET    | `/v1/billing/cycles/{id}`                       | Get billing cycle by ID                            |
| GET    | `/v1/billing/cycles?status=PENDING`             | List cycles by status                              |
| PATCH  | `/v1/billing/cycles/{id}/pay`                   | Mark cycle as paid                                 |
| PATCH  | `/v1/billing/cycles/{id}/void`                  | Void a billing cycle                               |

Billing cycle responses include three amount fields:

```json
{
  "originalAmount": 99.00,
  "totalDiscountAmount": 19.80,
  "amount": 79.20
}
```

`originalAmount` and `totalDiscountAmount` are `null` on cycles created before discount support was introduced (backward compatible).

---

## Coupon & Discount Logic

### Coupon Types

| `discountType` | Behavior |
|---|---|
| `PERCENTAGE` | Reduces the plan price by a percentage (e.g., `10` = 10% off) |
| `FIXED_AMOUNT` | Reduces the plan price by a fixed amount (e.g., `15.00` = $15 off) |

Both types are bounded: a coupon can never reduce the charge below $0.00.

### Stackability

Every coupon has a `stackable` boolean field. Stackable coupons can coexist with other coupons on the same subscription; non-stackable coupons must be the sole discount.

Three rules are enforced when applying a coupon:

| Rule | Condition | Result |
|---|---|---|
| **A** | New coupon is non-stackable AND subscription already has any coupon | Rejected |
| **B** | Subscription already has a non-stackable coupon | Rejected (regardless of new coupon's stackability) |
| **C** | Same coupon code already applied to this subscription | Rejected (no duplicates) |

### Parallel Discount Computation

When a subscription has multiple active coupons, each discount is computed **independently against the original plan price** — not sequentially off a running total. This makes the final amount order-independent and predictable.

```
Plan price:   $100.00
Coupon A:     10%  →  $10.00  (10% of $100)
Coupon B:     20%  →  $20.00  (20% of $100, not 20% of $90)
─────────────────────────────
Total discount:       $30.00
Final charged:        $70.00
```

### Discount Snapshots

The `SubscriptionCoupon` join entity records `discountSnapshot` and `discountTypeSnapshot` at the moment a coupon is applied. This means:

- Editing a coupon's `discountValue` after it has been applied does **not** affect existing subscriptions.
- Deactivating a coupon does **not** remove it from subscriptions it has already been applied to. Use `DELETE /v1/subscriptions/{id}/coupons/{couponCode}` to explicitly remove it.

### Redemption Limits

Set `maxRedemptions` on a coupon to cap how many subscriptions can use it. Set to `null` for unlimited. The counter is checked atomically — two concurrent applications of the same limited coupon will not both succeed if only one slot remains.

---

## Configuration

All configuration lives in `src/main/resources/application.yml`.

| Property                           | Default              | Description                         |
|------------------------------------|----------------------|-------------------------------------|
| `server.port`                      | `8080`               | HTTP port                           |
| `spring.datasource.url`            | H2 in-memory         | Database connection URL             |
| `spring.threads.virtual.enabled`   | `true`               | Enable Java 21 virtual threads      |
| `springdoc.swagger-ui.path`        | `/swagger-ui.html`   | Swagger UI path                     |
| `management.endpoints.web.exposure`| `health,info,metrics`| Exposed actuator endpoints          |

---

## Development

### Build

```bash
./mvnw clean package
```

### Run Tests

```bash
./mvnw test
```

### Code Style

This project uses standard Java conventions. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

---

## Testing

The project uses Spring Boot's test slice annotations for fast, focused tests:

- `@WebMvcTest` — controller layer tests with mocked services
- `@SpringBootTest` — full integration tests
- `@DataJpaTest` — (add as needed) repository-layer tests with H2

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for branching strategy, commit conventions, and PR guidelines.

---

## License

This project is licensed under the [MIT License](LICENSE).
