# Subscriptions API

A flexible subscription management REST API built with **Spring Boot 3.4** and **Java 21**, supporting recurring billing intervals from hourly to monthly and beyond.

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.4.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Endpoints](#api-endpoints)
- [Configuration](#configuration)
- [Development](#development)
- [Testing](#testing)
- [Contributing](#contributing)

---

## Features

- **Flexible Billing Intervals** — hourly, daily, weekly, monthly, yearly, or custom
- **Full Subscription Lifecycle** — pending → trialing → active → paused → cancelled
- **Plan Management** — draft, activate, and archive subscription plans
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
└── billing/         # Billing cycle domain (same structure as plan)
```

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

| Method | Endpoint                           | Description                    |
|--------|------------------------------------|--------------------------------|
| POST   | `/v1/subscriptions`                | Create a subscription          |
| GET    | `/v1/subscriptions`                | List subscriptions             |
| GET    | `/v1/subscriptions/{id}`           | Get subscription by ID         |
| PATCH  | `/v1/subscriptions/{id}/activate`  | Activate a pending subscription|
| PATCH  | `/v1/subscriptions/{id}/pause`     | Pause an active subscription   |
| PATCH  | `/v1/subscriptions/{id}/resume`    | Resume a paused subscription   |
| PATCH  | `/v1/subscriptions/{id}/cancel`    | Cancel a subscription          |

### Billing

| Method | Endpoint                                        | Description                       |
|--------|-------------------------------------------------|-----------------------------------|
| GET    | `/v1/billing/subscriptions/{id}/cycles`         | List billing cycles               |
| GET    | `/v1/billing/cycles/{id}`                       | Get billing cycle by ID           |
| GET    | `/v1/billing/cycles?status=PENDING`             | List cycles by status             |
| PATCH  | `/v1/billing/cycles/{id}/pay`                   | Mark cycle as paid                |
| PATCH  | `/v1/billing/cycles/{id}/void`                  | Void a billing cycle              |

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
