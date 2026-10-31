# ADR 0001: Standalone Modular Monolith and Phase 1 Security Foundation

- **Status:** Accepted
- **Date:** 2026-10-31
- **Platform:** CRANIAL — Universal AI Integration Platform

## Context

CRANIAL provides shared AI orchestration, OCR/document processing, RAG retrieval, and decision-support capabilities across independent applications (Peggy, Store Management System, Worker App, E-commerce, and future applications) while preserving strict application ownership over primary business data, deterministic domain calculations, and transaction validation.

Day-one microservices would introduce premature distributed operational complexity before workload boundaries justify splitting. At the same time, embedding CRANIAL inside a single client application (such as Peggy or SMS) would violate multi-application neutrality and tenant isolation.

## Decisions

1. **Standalone Modular Monolith:**
   - CRANIAL runs as an independent repository, backend (`Java 21` + `Spring Boot 3.4`), admin UI (`React` + `TypeScript`), and database (`PostgreSQL 16` with `pgvector` image ready for Phase 6).
   - Internal domain packages (`platform`, `audit`, `application`, `security`, `gateway`, `admin`, and future `provider`, `orchestration`, `prompt`, `schema`, `tool`, `conversation`, `document`, `knowledge`, `intelligence`, `job`, `usage`) enforce strict acyclic boundaries verified continuously via ArchUnit tests.

2. **Data Access and Incremental Migrations:**
   - Use **MyBatis-Plus** for explicit, predictable SQL/mapper control without heavy JPA magic, paired with **Flyway** versioned SQL migrations (`V1__phase1_platform_foundation.sql`).
   - Create only tables required by the active phase; evolve schema incrementally.

3. **Dual Security Plane and Anti-Spoofing Identity Context:**
   - **Integration Plane (`/api/v1/**`):** Server-to-server authentication via `X-Cranial-Api-Key` (`crn_<env>_<prefix>.<secret>`). API key secrets are hashed at rest using `HMAC-SHA256` with a server pepper and verified in constant time (`MessageDigest.isEqual`) to prevent both database secret exposure and CPU hash-DoS on high-throughput M2M traffic.
   - **Signed Context Exchange:** Tenant and user scope are never trusted from unverified request JSON bodies. Client backends sign `X-Cranial-Context` and `X-Cranial-Timestamp` (±300s replay window) alongside request method, path, and SHA-256 body digest using their credential's signing secret (`X-Cranial-Signature`). Signing secrets are encrypted at rest with `AES-256-GCM`.
   - **Operator Plane (`/api/admin/v1/**`):** Human platform operators authenticate with `BCrypt` (cost 12) password hashes and receive short-lived signed JWTs governed by fine-grained RBAC permissions (`SUPER_ADMIN`, `PLATFORM_OPERATOR`, `AUDITOR`).

4. **Local-First Governance Foundation:**
   - Every registered application defaults to `allow_hosted_providers = false` and `allow_sensitive_external = false` at provisioning time, laying the database and policy groundwork for Phase 2's strict no-silent-paid-fallback routing.

## Consequences

- Client applications must call CRANIAL from trusted backend services rather than embedding CRANIAL credentials in public mobile or browser clients.
- Module boundaries are enforced at build time by ArchUnit, allowing future extraction of heavy workers (e.g., OCR in Phase 5) without rewriting core contracts.

