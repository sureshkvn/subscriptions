# ADR-001: Spring Boot 3.4 with Java 21

**Date:** 2026-04-12
**Status:** Accepted

---

## Context

We need a server-side framework for the Subscriptions API. The primary requirements are:

- Strong ecosystem for REST APIs and data access
- Active maintenance and long-term support
- Java 21 compatibility (virtual threads, records)
- Fast local development with minimal setup

## Decision

Use **Spring Boot 3.4.x** with **Java 21** as the application framework and runtime.

## Rationale

**Spring Boot 3.4** was chosen because:

1. **Spring Boot 3.x requires Java 17+** — a good forcing function for adopting modern Java features (records, sealed classes, pattern matching, virtual threads)
2. **Mature ecosystem** — Spring Data JPA, Spring Validation, Spring Actuator, and Springdoc OpenAPI compose cleanly
3. **Virtual thread support** — `spring.threads.virtual.enabled=true` enables Project Loom with a single config change, providing high throughput without explicit async programming models
4. **Long release cadence** — Spring Boot 3.x is the current generation with active support through 2026+
5. **Team familiarity** — existing knowledge reduces ramp-up time

**Java 21** (LTS) was chosen specifically to gain:
- **Virtual threads** (Project Loom GA) for efficient I/O concurrency
- **Record patterns** and **switch expressions** for expressive domain modeling
- **Sequenced collections** for predictable iteration order

## Alternatives Considered

| Alternative       | Reason Rejected                                                  |
|-------------------|------------------------------------------------------------------|
| Quarkus           | Less mature ecosystem for JPA; steeper learning curve            |
| Micronaut         | Compile-time DI is valuable, but annotation processing friction  |
| Spring Boot 2.x   | End of OSS support; no virtual thread support                    |
| Ktor (Kotlin)     | Requires Kotlin adoption; team is Java-first                     |

## Consequences

- **Positive:** Fast local startup, minimal boilerplate via `@SpringBootApplication`, rich test support (`@WebMvcTest`, `@DataJpaTest`)
- **Positive:** OpenAPI docs via Springdoc are auto-generated from annotations
- **Neutral:** Must use Jakarta EE namespace (javax → jakarta) — a one-time migration already completed
- **Negative:** Spring Boot startup time (~2s) is higher than Quarkus native; mitigated by native image support if needed

---

## Related ADRs

- ADR-002 (planned): Database strategy — H2 vs PostgreSQL profiles
- ADR-003 (planned): Authentication strategy — Spring Security + JWT
