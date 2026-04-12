# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.x     | ✅        |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

To report a security vulnerability, email **sureshkvn@gmail.com** with:

- A description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Any suggested mitigations (optional)

You will receive an acknowledgement within **48 hours**. We aim to release a patch within **7 days** for critical issues.

## Security Practices

- All secrets and credentials must be supplied via environment variables — never committed to source control
- The `.gitignore` explicitly excludes `application-prod.yml` and `*.env` files
- Dependencies are kept up-to-date; review GitHub Dependabot alerts regularly
- Database connections use parameterized queries (via Spring Data JPA) — no raw string concatenation
