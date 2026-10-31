# ADR 0002: API Versioning and Deprecation Policy

- **Status:** Accepted
- **Date:** 2026-10-31
- **Platform:** CRANIAL — Universal AI Integration Platform

## Context

CRANIAL serves multiple autonomous client applications (Peggy for money management, SMS, Worker App, E-commerce, and future services). As the platform evolves across development phases (such as introducing streaming, new model routers, or enriched OCR extraction contracts), API contracts will inevitably undergo structural enhancements and version transitions.

Breaking contract changes without structured advance notice or standardized HTTP semantics risks breaking client operations. A clear, machine-readable deprecation policy is required.

## Decisions

1. **Path-Based Major Versioning:**
   - Public integration contracts reside under `/api/v{major}/**` (e.g. `/api/v1/gateway/...`).
   - Operator administration contracts reside under `/api/admin/v{major}/**` (e.g. `/api/admin/v1/...`).
   - Minor enhancements and non-breaking schema additions (additive optional fields) remain within the existing major version.

2. **Standard Machine-Readable Deprecation Headers (RFC 8594 / RFC 7231):**
   - Endpoints marked with `@ApiDeprecated` automatically emit standard HTTP response headers via `ApiDeprecationInterceptor`:
     - `Deprecation: true` (or `@<unix-timestamp>`) indicates that the endpoint has been deprecated.
     - `Sunset: <HTTP-date>` (RFC 7231 IMF-fixdate, e.g. `Sun, 01 Nov 2026 00:00:00 GMT`) specifies when the endpoint will become unresponsive or removed.
     - `Link: <url>; rel="successor-version"` references the replacement URI.
     - `Link: <url>; rel="deprecation"` references documentation describing migration steps.

3. **Advance Notice Period:**
   - In production environments, breaking deprecations require a minimum 6-month sunset notice window before decommission.
   - During the sunset window, client invocations of deprecated endpoints trigger warning-level platform audit events (`API_DEPRECATED_INVOCATION`) to notify operators of lagging client migrations.

4. **Backward Compatibility Guarantee:**
   - Existing endpoints continue to function without behavior regression throughout their sunset period.

