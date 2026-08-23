# Engineering Hardening C-Depth Implementation Plan

> **For agentic workers:** Use subagent-driven-development or executing-plans. Steps use checkbox syntax.

**Goal:** Execute design `docs/superpowers/specs/2026-08-09-engineering-hardening-c-depth-design.md` session batch: Phase 0 + Phase 1 core + Phase 2 main path.

**Architecture:** Fix gateway/secrets first; collapse ApiResponse; add missing Command/Query Handlers so Controllers are Handler-only; unify frontend PageResult on `@bone/shared-types`.

**Tech Stack:** Spring Boot 3.2, bone-core ApiResponse, ArchUnit, React/TS micro-apps.

**Global Constraints:**
- No DDL changes (`biz_identity_code` docs-only)
- Phase 3 AbstractEntity purity deferred to follow-up unless trivial
- No commit unless user asks
- DDD: Controller → Handler only; domain purity rules for new code

## File map

| Area | Files |
|------|-------|
| Gateway | `bone-platform/bone-gateway/src/main/resources/application.yml` |
| Masterdata port | `bone-platform/bone-masterdata/.../application-prod.yml` |
| Secrets | iam/system/integration/extension-studio/studio-generator `application*.yml` |
| Docs | `AGENTS.md`, `CODE_WIKI.md` (stale SA-Token/Seata/Flyway/biz_identity/ports) |
| ApiResponse | delete generator local; fix CapabilityController |
| Masterdata CQRS | new Delete* / Quality*List Handlers; Controllers |
| IAM | LogoutCommandHandler; AuthController |
| Generator CQRS | Operation/History/Submit/Status Handlers; Controllers |
| Metadata CQRS | wrap Services in Handlers where practical this batch |
| Frontend | `packages/shared-types`, apps types + list consumers, `.env.example` |

---

### Task 1: Phase 0 Gateway + ports
- [ ] Set masterdata default URI `8084`, generator `8086` in gateway yml
- [ ] Align masterdata prod port to `8084`
- [ ] Verify: grep gateway yml for 8085 generator / 8080 masterdata → none as defaults

### Task 2: Phase 0 Secrets
- [ ] Remove weak JWT/DB/MinIO defaults from non-dev main/prod YAML; keep only in `*-dev.yml` / test
- [ ] studio-generator main: no `mysql123`; extension-studio main: no JWT default

### Task 3: Phase 0 Docs
- [ ] Fix CODE_WIKI / AGENTS claims: auth=Spring Security+JJWT; no Seata in POM; Flyway unused; biz_identity not in bone-init.sql; ports table accurate

### Task 4: ApiResponse unify
- [ ] CapabilityController → `com.bone.core.model.ApiResponse`
- [ ] Delete `studio-generator/.../common/result/ApiResponse.java`

### Task 5: Masterdata CQRS
- [ ] Delete entity/record CommandHandlers; Quality list QueryHandlers
- [ ] Strip Repository from Controllers

### Task 6: IAM Logout
- [ ] LogoutCommandHandler; AuthController Handler-only for logout

### Task 7: Generator + Metadata CQRS (batch)
- [ ] Thin Handlers for Operation/History/Async status/submit
- [ ] Metadata: move idempotency into publish handler; Runtime/Metadata Handlers as thin wrappers where feasible

### Task 8: Frontend contracts
- [ ] Enrich shared-types ApiResponse optional success/timestamp
- [ ] Migrate apps off local list/pageNum; fix .env.example

### Task 9: Verify
- [ ] `mvn -pl ... -am test` for touched backend modules
- [ ] Frontend typecheck/build for migrated apps if time
