# CRANIAL — Universal AI Integration Platform

> **Tagline:** One Brain. Many Connections.

CRANIAL is a standalone, model-independent, local-first **Universal AI Integration Platform**. It centralizes reusable AI orchestration, OCR/document processing, permission-aware retrieval (RAG), data explanations, and decision-support workflows for multiple client applications (**Peggy**, **SMS**, **Worker App**, **E-commerce**, and future applications) without taking ownership of their primary databases, deterministic domain calculations, or business validation.

- **Peggy boundary note:** Peggy is strictly a money management and financial education application—never a banking, payment-processing, or settlement system. CRANIAL never mutates application ledgers or executes transactions.
- **Local-first & cost rule:** Hosted/paid providers are disabled by default. CRANIAL never silently falls back to paid external providers when local inference is unavailable.

---

## Repository Structure (Phase 1 — Platform Foundation)

- `about_project.md` — Authoritative vision, boundaries, implemented features, and TODO backlog.
- `docs/adr/` — Architecture Decision Records (`0001-modular-monolith-and-phase1-foundation.md`).
- `backend/` — Java 21 + Spring Boot 3.4 modular monolith (`MyBatis-Plus`, `Flyway`, `Spring Security`, `OpenAPI 3`, `Micrometer/Prometheus`).
- `admin-ui/` — React 19 + TypeScript + Vite + Tailwind CSS operator console.
- `docker/` & `docker-compose.yml` — Minimal Phase 1 deployment (`pgvector/pgvector:pg16` + backend + admin UI).

---

## Quick Start (Docker Compose)

1. Copy `.env.example` to `.env` and adjust credentials if desired:
   ```powershell
   Copy-Item .env.example .env
   ```
2. Launch PostgreSQL (`pgvector`), Backend, and Admin UI:
   ```powershell
   docker compose up --build -d
   ```
3. Access endpoints:
   - **Admin Console:** http://localhost:3000
   - **Swagger / OpenAPI UI:** http://localhost:8080/swagger-ui.html
   - **Integration OpenAPI JSON:** http://localhost:8080/v3/api-docs/integration-v1
   - **Admin OpenAPI JSON:** http://localhost:8080/v3/api-docs/admin-v1
   - **Health / Readiness Probe:** http://localhost:8080/actuator/health/readiness

---

## Local Development & Verification

### Backend Tests (Maven Wrapper included)
```powershell
cd backend
.\mvnw.cmd test
```

### Admin UI Tests & Production Build
```powershell
cd admin-ui
npm install
npm test
npm run build
```

---

## Integration Authentication & Signed Context Contract (`/api/v1/**`)

1. **Application API Key:** Pass `X-Cranial-Api-Key: crn_<env>_<prefix>.<secret>` on every server-to-server call.
2. **Signed Tenant/User Context Exchange:** Never send unverified `tenant_id`, `user_id`, or `roles` in JSON bodies. Sign identity context using the credential's `raw_signing_secret`:
   - `X-Cranial-Context`: Base64URL-encoded JSON (`{"tenant_ref":"...","user_ref":"...","roles":["..."],"scopes":["..."]}`)
   - `X-Cranial-Timestamp`: Current UNIX epoch seconds (`±300s` replay window)
   - `X-Cranial-Signature`: Hex `HMAC-SHA256(signing_secret, timestamp + "." + HTTP_METHOD + "." + REQUEST_PATH + "." + X_Cranial_Context + "." + SHA256_HEX(RAW_BODY))`
3. **Idempotency:** Pass `Idempotency-Key: <unique-key>` on retriable POST operations. Reusing a key with a modified payload returns `409 IDEMPOTENCY_KEY_CONFLICT`.
