# Contributing Guide

Thank you for taking the time to contribute to the Subscriptions API. This document outlines the standards and workflows expected from all contributors.

---

## Table of Contents

- [Branching Strategy](#branching-strategy)
- [Commit Conventions](#commit-conventions)
- [Pull Request Process](#pull-request-process)
- [Code Standards](#code-standards)
- [Testing Requirements](#testing-requirements)
- [Documentation Requirements](#documentation-requirements)

---

## Branching Strategy

This project follows **GitHub Flow** — a lightweight branching model:

| Branch               | Purpose                                              |
|----------------------|------------------------------------------------------|
| `main`               | Always deployable; protected. No direct pushes.      |
| `feature/<short-desc>`| New features — e.g. `feature/webhook-notifications` |
| `fix/<short-desc>`   | Bug fixes — e.g. `fix/subscription-cancel-status`   |
| `chore/<short-desc>` | Tooling, dependency upgrades, refactoring            |
| `docs/<short-desc>`  | Documentation-only changes                          |

**Rules:**
- Branch from `main`
- Keep branches short-lived (aim to merge within 3 days)
- Delete branches after merging

---

## Commit Conventions

This project follows the **Conventional Commits** specification.

### Format

```
<type>(<scope>): <short description>

[optional body]

[optional footer(s)]
```

### Types

| Type       | When to use                                                   |
|------------|---------------------------------------------------------------|
| `feat`     | A new feature                                                 |
| `fix`      | A bug fix                                                     |
| `refactor` | Code change that is neither a bug fix nor a new feature       |
| `test`     | Adding or updating tests                                      |
| `docs`     | Documentation changes only                                    |
| `chore`    | Build, CI/CD, or tooling changes                              |
| `perf`     | Performance improvements                                      |
| `style`    | Formatting, whitespace — no logic changes                     |

### Scope

The scope should match the domain or layer being changed: `plan`, `subscription`, `billing`, `common`, `config`, `ci`.

### Examples

```
feat(subscription): add pause and resume lifecycle endpoints

fix(billing): correct period-end calculation for WEEKLY interval

refactor(plan): extract name-uniqueness check to private method

test(subscription): add WebMvcTest for cancel endpoint

docs(architecture): update data access patterns section

chore(deps): bump spring-boot to 3.4.4
```

---

## Pull Request Process

1. **Open a PR** against `main` with a clear title using the same Conventional Commits format.
2. **Fill in the PR template** — describe what changed and why.
3. **Ensure all checks pass** — CI must be green (build + tests).
4. **Request at least one review** before merging.
5. **Squash and merge** — one commit per PR keeps history clean.

### PR Title Format

```
feat(plan): add archive endpoint with status guard
```

---

## Code Standards

### General

- Follow standard Java naming conventions (PascalCase classes, camelCase fields/methods)
- Maximum line length: 120 characters
- Keep methods focused — single responsibility
- Prefer explicit types over `var` for public method signatures

### Spring Boot Specifics

- Use constructor injection (Lombok `@RequiredArgsConstructor`) — never field injection
- Mark service classes `@Transactional(readOnly = true)` at class level; override write methods with `@Transactional`
- Define service contracts as interfaces; keep implementations in `service/impl/`
- Use `record` types for all DTOs
- Use MapStruct for all entity ↔ DTO conversions — no manual mapping in controllers or services

### DTOs and Validation

- All request DTOs must include Bean Validation annotations
- Validation error messages must be human-readable and actionable
- Response DTOs must be immutable Java records

### Exception Handling

- Services throw `ResourceNotFoundException` or `BusinessRuleException`
- Controllers never catch exceptions — let `GlobalExceptionHandler` handle them
- Never expose stack traces or internal details in API responses

---

## Testing Requirements

Every PR must include or update tests appropriate to the change:

| Change type          | Required tests                                           |
|----------------------|----------------------------------------------------------|
| New endpoint         | `@WebMvcTest` covering success + validation error paths  |
| New business rule    | Unit test for service with mocked repository             |
| New query            | `@DataJpaTest` if using non-trivial JPQL                 |
| Bug fix              | Regression test reproducing the bug                      |

**Minimum expectations:**
- All new code paths must be covered
- No PR should reduce overall test coverage

---

## Documentation Requirements

- Update `docs/ARCHITECTURE.md` for significant structural changes
- Add or update Javadoc on public methods and classes
- Update `README.md` if endpoints or configuration options change
- For significant architectural decisions, add an ADR in `docs/adr/`
