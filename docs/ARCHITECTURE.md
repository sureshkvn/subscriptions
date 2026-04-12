# Architecture Reference

> **Project:** Subscriptions API
> **Stack:** Spring Boot 3.4 · Java 21 · H2 (dev) / PostgreSQL (prod)
> **Last Updated:** 2026-04-12

---

## Table of Contents

1. [Overview](#1-overview)
2. [Domain Model](#2-domain-model)
3. [Package Structure](#3-package-structure)
4. [Layered Architecture](#4-layered-architecture)
5. [API Design Principles](#5-api-design-principles)
6. [Error Handling Strategy](#6-error-handling-strategy)
7. [Data Access Patterns](#7-data-access-patterns)
8. [Java 21 Features](#8-java-21-features)
9. [Configuration Strategy](#9-configuration-strategy)
10. [Testing Strategy](#10-testing-strategy)
11. [Scalability Considerations](#11-scalability-considerations)
12. [Future Roadmap](#12-future-roadmap)

---

## 1. Overview

The Subscriptions API follows a **domain-driven, vertically sliced layered architecture**. Each business domain (`plan`, `subscription`, `billing`) owns its full stack from controller to repository, maximizing cohesion and minimizing coupling between unrelated domains.

```
Client ──► REST Controller ──► Service Interface ──► Service Impl ──► Repository ──► DB
                │                      │
          (Validation)           (Business Rules)
                │                      │
          ApiResponse<T>         Domain Exceptions
```

---

## 2. Domain Model

```
Plan ──┐
       ├──► Subscription ──► BillingCycle
       │
       └── defines billing interval, price, trial
```

### Plan

A template defining what customers subscribe to. Plans have a `BillingInterval` (HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM) and an `intervalCount` multiplier. Plans transition through `DRAFT → ACTIVE → ARCHIVED`.

### Subscription

A customer's association with a `Plan`. Lifecycle: `PENDING → TRIALING → ACTIVE → PAUSED → CANCELLED / EXPIRED`. A subscription computes its `currentPeriodEnd` from the plan's billing interval.

### BillingCycle

An immutable record of a single billing event for a `Subscription`. Tracks amount, period covered, payment status, and the external payment reference. Statuses: `PENDING → PAID` or `FAILED`, `REFUNDED`, `VOID`.

---

## 3. Package Structure

```
src/main/java/com/sureshkvn/subscriptions/
│
├── SubscriptionsApplication.java       # @SpringBootApplication entry point
│
├── config/
│   └── OpenApiConfig.java              # OpenAPI 3 / Swagger configuration
│
├── common/
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java # @RestControllerAdvice
│   │   ├── ResourceNotFoundException.java
│   │   └── BusinessRuleException.java
│   └── response/
│       └── ApiResponse.java            # Generic response envelope
│
├── plan/                               # Plan domain vertical slice
│   ├── controller/PlanController.java
│   ├── service/PlanService.java        # Interface
│   ├── service/impl/PlanServiceImpl.java
│   ├── repository/PlanRepository.java
│   ├── model/Plan.java                 # JPA entity (with enums)
│   ├── dto/PlanRequest.java            # Inbound record + validation
│   ├── dto/PlanResponse.java           # Outbound record
│   └── mapper/PlanMapper.java          # MapStruct mapper
│
├── subscription/                       # Subscription domain (same structure)
│   └── ...
│
└── billing/                            # Billing cycle domain (same structure)
    └── ...
```

### Design Rationale

**Vertical slices** are preferred over horizontal layers because:
- Adding a new domain only requires touching one package
- Team members can work on different domains without merge conflicts
- Each domain's dependencies are visible and explicit

---

## 4. Layered Architecture

### Controller Layer

- Annotated with `@RestController`
- Handles HTTP concerns only: routing, status codes, request parsing
- Delegates all business logic to the service interface
- Returns `ResponseEntity<ApiResponse<T>>` for consistent response shape
- Input is validated via Bean Validation (`@Valid`)

### Service Layer

- Defined as an interface (`PlanService`, `SubscriptionService`, etc.)
- Implementation is annotated `@Transactional(readOnly = true)` at class level
- Write operations override with `@Transactional`
- Contains all business rules and invariant enforcement
- Throws domain-specific exceptions (`ResourceNotFoundException`, `BusinessRuleException`)

### Repository Layer

- Extends `JpaRepository<Entity, ID>`
- Custom queries use Spring Data method naming conventions first
- Complex queries use `@Query` with JPQL
- No business logic — data access only

### Mapper Layer

- Uses **MapStruct** for compile-time, reflection-free DTO/entity conversion
- `componentModel = SPRING` makes mappers injectable as Spring beans
- Partial update pattern: `@BeanMapping(nullValuePropertyMappingStrategy = IGNORE)`

---

## 5. API Design Principles

### RESTful Conventions

| Concern            | Convention                                              |
|--------------------|---------------------------------------------------------|
| Resource naming    | Plural nouns: `/plans`, `/subscriptions`, `/billing`    |
| State transitions  | `PATCH /{id}/{action}` (activate, pause, cancel)        |
| Collection filters | Query parameters: `?status=ACTIVE&customerId=cust-123`  |
| Response envelope  | `ApiResponse<T>` with `success`, `message`, `data`      |
| HTTP status codes  | 201 Create, 200 OK, 400 Validation, 404 Not Found, 422 Business Rule |

### Response Envelope

All responses use the `ApiResponse<T>` wrapper:

```json
{
  "success": true,
  "message": "Plan created successfully",
  "data": { ... },
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Error responses:

```json
{
  "success": false,
  "message": "Plan not found with id: '99'",
  "timestamp": "2024-01-01T00:00:00Z"
}
```

Validation errors include a `data` map of field → message:

```json
{
  "success": false,
  "message": "Validation failed",
  "data": {
    "name": "Plan name is required",
    "price": "Price must be zero or greater"
  }
}
```

---

## 6. Error Handling Strategy

Centralized in `GlobalExceptionHandler` (`@RestControllerAdvice`):

| Exception                        | HTTP Status                     |
|----------------------------------|---------------------------------|
| `ResourceNotFoundException`      | 404 Not Found                   |
| `BusinessRuleException`          | 422 Unprocessable Entity        |
| `MethodArgumentNotValidException`| 400 Bad Request (with field map)|
| `IllegalArgumentException`       | 400 Bad Request                 |
| `Exception` (catch-all)          | 500 Internal Server Error       |

**Rule:** Services throw domain exceptions. Controllers never handle exceptions. The exception handler translates to HTTP.

---

## 7. Data Access Patterns

### Optimistic Locking (TODO)

Add `@Version` field to entities that are frequently updated concurrently (e.g., `Subscription`) to prevent lost-update anomalies.

### Lazy Loading

All `@ManyToOne` associations use `FetchType.LAZY` to avoid N+1 query problems. Fetch joins or `@EntityGraph` should be added when a specific use-case needs eagerly loaded data.

### Index Strategy

Entities define `@Table(indexes = {...})` for common query patterns:
- `subscriptions.customerId` — customer's subscription lookups
- `subscriptions.status` — status-based polling
- `billing_cycles.subscription_id` — billing history lookups

---

## 8. Java 21 Features

### Virtual Threads

Enabled via `spring.threads.virtual.enabled=true` in `application.yml`. This delegates all incoming request handling to virtual threads (Project Loom), dramatically improving throughput for I/O-bound workloads without the thread pool sizing challenges of platform threads.

### Records for DTOs

All request and response DTOs use Java `record` types for:
- Immutability by default
- Compact, boilerplate-free declarations
- Structural equality and `toString` for free

### Pattern Matching (switch expressions)

The billing period calculator in `SubscriptionServiceImpl` uses sealed/switch expressions for exhaustive handling of `BillingInterval` variants.

---

## 9. Configuration Strategy

| Profile   | Database              | SQL Logging | H2 Console |
|-----------|-----------------------|-------------|------------|
| (default) | H2 in-memory          | Enabled     | Enabled    |
| `prod`    | PostgreSQL (external) | Disabled    | Disabled   |

All environment-specific values (passwords, URLs) must use environment variables or a secrets manager — never committed to version control.

---

## 10. Testing Strategy

| Layer        | Annotation              | Scope                                         |
|--------------|-------------------------|-----------------------------------------------|
| Controller   | `@WebMvcTest`           | HTTP layer only; services mocked              |
| Service      | `@ExtendWith(Mockito)`  | Business logic; repository mocked             |
| Repository   | `@DataJpaTest`          | SQL queries against H2                        |
| Integration  | `@SpringBootTest`       | Full context, real HTTP, real DB              |

**Goal:** The majority of tests should be fast `@WebMvcTest` or pure unit tests. `@SpringBootTest` is reserved for smoke tests and critical integration paths.

---

## 11. Scalability Considerations

- **Stateless design** — no server-side session; safe to run behind a load balancer
- **Virtual threads** — handles high-concurrency I/O cheaply
- **Database connection pooling** — HikariCP (Spring Boot default); tune `maximum-pool-size` for production
- **Async billing** — billing cycle creation should move to an async queue (Kafka/RabbitMQ) at scale
- **Caching** — plan data is read-heavy and cacheable; add `@Cacheable` + Redis for production

---

## 12. Future Roadmap

| Feature                       | Notes                                              |
|-------------------------------|---------------------------------------------------|
| PostgreSQL support            | Add prod profile with Flyway migrations            |
| Spring Security / JWT         | Auth for multi-tenant use                         |
| Async event processing        | Kafka/RabbitMQ for billing events                  |
| Webhook notifications         | Notify customers of billing events                |
| GraalVM native image          | `spring-boot:build-image` for smaller containers  |
| Metrics / Tracing             | Micrometer + OpenTelemetry + Grafana               |
| Payment gateway integration   | Stripe/PayPal for real charge processing          |
