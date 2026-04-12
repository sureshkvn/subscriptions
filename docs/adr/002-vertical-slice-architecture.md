# ADR-002: Vertical Slice / Domain Package Structure

**Date:** 2026-04-12
**Status:** Accepted

---

## Context

We need to decide how to organize Java packages. The two dominant options are:

- **Horizontal layers:** `controller/`, `service/`, `repository/` at the top level
- **Vertical slices:** `plan/`, `subscription/`, `billing/` each containing their own controller, service, and repository

## Decision

Use **vertical slices** — organize packages by domain, with each domain owning its full stack.

## Rationale

- **High cohesion:** Everything related to `plan` is in one place; a developer can understand a domain by reading one package
- **Low coupling:** Domains only share `common/` cross-cutting concerns
- **Parallel development:** Multiple contributors can work on different domains without touching the same files
- **Scalability path:** Each domain can be extracted into a separate module or service if needed
- **Test isolation:** `@WebMvcTest(PlanController.class)` loads only the plan slice

## Alternatives Considered

Horizontal layers (`controller/PlanController.java`, `service/PlanService.java`, etc.) were rejected because:
- As the domain count grows, each top-level package becomes a catch-all
- Changes to a domain touch multiple packages → noisier PRs
- No encapsulation between domains at the package level

## Consequences

- **Positive:** Adding a new domain (`coupon`, `invoice`) requires creating one new package with no changes to existing packages
- **Positive:** Domain boundaries are visible and enforced structurally
- **Negative:** Shared utilities (`ApiResponse`, `GlobalExceptionHandler`) live in `common/` — developers must know to look there
- **Rule:** No direct class references between domain packages. A `subscription` class must not import from `billing` directly; communication goes through service interfaces
