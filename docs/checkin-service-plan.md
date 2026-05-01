# Checkin-Service Foundation Plan

## Repository Inspection Summary

Checkin-Service is currently a sibling-aligned Spring Boot service scaffold. No check-in domain code exists yet.

| Area | Current state |
| --- | --- |
| Repository | `Checkin-Service` |
| Service name | `checkin-service` |
| Build tool | Gradle wrapper, `gradlew.bat` / `gradlew` |
| Gradle group | `com.capstone` |
| Java version | 21 |
| Spring Boot | 3.3.1 |
| Spring Cloud | 2023.0.3 |
| Package | `com.capstone.checkinservice` |
| Main class | `com.capstone.checkinservice.CheckinServiceApplication` |
| Local port | 8087 |
| Database schema | `checkin_service` |
| Current config | `src/main/resources/application.yml` |

Current dependencies:

- Spring Security, Validation, Web, WebFlux
- Spring Boot Actuator
- Spring Data JPA
- Spring Data Redis
- Spring Cloud Netflix Eureka Client
- Spring Cloud OpenFeign
- PostgreSQL runtime driver
- Lombok
- Spring Boot test and JUnit Platform launcher

Current `application.yml` defines:

- `server.port: 8087`
- `spring.application.name: checkin-service`
- PostgreSQL datasource placeholders: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- Hibernate default schema: `checkin_service`
- Redis URL placeholder: `REDIS_URL`
- Eureka default zone placeholder: `EUREKA_DEFAULT_ZONE`

Current source structure:

- `src/main/java/com/capstone/checkinservice/CheckinServiceApplication.java`
- `src/test/java/com/capstone/checkinservice/CheckinServiceApplicationTests.java`
- `src/main/resources/application.yml`

Framework and convention status:

- Actuator dependency exists, but management exposure is not configured yet.
- Eureka dependency and config exist.
- Redis dependency and config exist.
- JPA dependency and config exist.
- Security dependency exists, but JWT filters, internal-service auth, and authorization rules are not implemented yet.
- Feign dependency exists, but no clients are implemented yet.
- Springdoc/Swagger is not present in Checkin-Service yet. Sibling services use Springdoc, so Task 1 should decide whether to add it immediately or defer until controller work.

Baseline verification:

- `.\gradlew.bat test` passes after Gradle wrapper/dependencies are available.
- Current test disables datasource, JPA, Redis, Eureka, and discovery auto-configuration for a lightweight context load.

## Service Responsibility

Checkin-Service owns the admission-control domain for EvoTicket:

- Dynamic QR token issuing for current ticket owners.
- Dynamic QR verification for checker scan flows.
- A local admission-optimized `ticket_access_state` projection.
- Online checker scan validation.
- Atomic check-in from `VALID` to `USED`.
- `CheckInLog` operational audit records.
- Checker assignment validation if assignment data is local.
- Checker device readiness/tracking when needed for offline fallback.
- Offline package generation for checker devices.
- Offline scan sync, rejection, failure, and conflict classification.
- Support lookup and owner info read model with masked PII.

Checkin-Service does not own:

- User login, password, refresh tokens, or core IAM behavior.
- Payment processing.
- Order creation.
- Original event creation.
- Original ticket inventory sale logic.
- Resale marketplace transaction rules.
- Blockchain minting transactions.
- Frontend UI implementation.
- Manual check-in override in MVP.

Relationships to sibling services:

- IAM-Service remains the source for authentication and user identity. Checkin-Service should validate JWTs using the same sibling conventions and should use IAM only for identity/user metadata lookups if needed.
- Order-Service remains the source of truth for ticket purchase, TicketAsset ownership, access status, resale locks, cancellation, and ticket provenance. Checkin-Service stores an admission projection, not the original TicketAsset aggregate.
- Payment-Service remains the source for payment state and successful purchase events.
- Inventory-Service remains the source for event, showtime, ticket type, venue, and gate metadata when not already projected.
- Resale behavior remains owned by Order-Service resale logic. Checkin-Service observes resale lock/unlock and ownership transfer through projection updates.
- Api-Gateway should route `/checkin-service/**` to this service in a later gateway task.
- EvoTicket_FE consumes stable result codes, QR refresh timing, checker assignments, scan responses, offline package data, sync summaries, and masked support lookup responses.

## Core Business Flows

### Buyer QR Issuing

The buyer QR is server-generated, signed, short-lived, and not the ticket itself. It is proof that the current owner is allowed to display the ticket at that time.

Default MVP behavior:

1. Buyer calls `GET /api/v1/tickets/{ticketAssetId}/qr-token`.
2. Backend authenticates the buyer and loads `ticket_access_state`.
3. Backend verifies `currentOwnerId` matches the authenticated user.
4. Backend rejects issuing when status is `USED`, `LOCKED_RESALE`, `CANCELLED`, or ownership cannot be verified.
5. Backend signs a token envelope with a server-side private key.
6. Backend returns the token, `expiresAt`, and `refreshAfterSeconds`.

QR token envelope:

- Header/metadata: `alg`, `kid`, `typ` if applicable.
- Payload claims: `ticketAssetId`, `ticketCode`, `eventId`, `showtimeId`, `qrVersion`, `issuedAt`, `expiresAt`, `jti`.
- Signature: generated over header plus payload as part of the token envelope. The signature is not a payload field.

Recommended TTL is 20 to 30 seconds. Recommended frontend refresh is 15 to 20 seconds. MVP does not support buyer offline QR batches and never sends raw secret seed or private signing material to the client.

### Online Checker Scan

Online scan is the primary admission path.

1. Checker logs in through IAM.
2. Checker selects assigned event, showtime, and optional gate.
3. Checker scans QR.
4. Frontend sends QR token and scan context to `POST /api/v1/checker/scan`.
5. Backend validates checker assignment and device context.
6. Backend verifies token signature and expiration.
7. Backend extracts token claims and loads `ticket_access_state`.
8. Backend checks event, showtime, optional gate policy, `qrVersion`, and access status.
9. Backend atomically updates the ticket from `VALID` to `USED`.
10. Backend writes `CheckInLog` with `scanMode=ONLINE`.
11. Backend returns stable `ScanResultCode`.

Double check-in prevention is mandatory. The implementation must use a conditional update or optimistic-lock strategy so only one transaction can change a row from `VALID` to `USED`. A second scan must return `ALREADY_USED` or another precise non-success code after reloading current state.

### Offline Package

Offline package is only for checker devices, never buyer devices.

Offline package is scoped by:

- `eventId`
- `showtimeId`
- optional `gateId`
- `checkerId`
- `deviceId`

The package contains package identity, validity window, verification key metadata, a limited ticket snapshot, checksum, and package signature. It must not contain raw QR secrets, private signing keys, JWT secrets, unnecessary PII, full email, or full phone.

Offline acceptance is provisional and must be represented as `OFFLINE_ACCEPTED_PENDING_SYNC`.

### Offline Sync

When network is restored, the checker frontend sends pending offline scans to `POST /api/v1/checker/offline-sync`.

Backend sync behavior:

1. Verify checker, device, and package identity.
2. Verify package validity and package signature.
3. Revalidate each QR token.
4. Check QR expiration at `scannedAt`, not only current server time.
5. Load current `ticket_access_state`.
6. Attempt atomic `VALID` to `USED` update.
7. Write `CheckInLog` and `offline_sync_item`.
8. Return a sync summary.

Sync result meanings:

- `SYNC_ACCEPTED`: server accepted the offline scan and marked the ticket `USED`.
- `SYNC_REJECTED`: business state prevents check-in, such as `CANCELLED`, `LOCKED_RESALE`, `WRONG_EVENT`, `WRONG_SHOWTIME`, or `WRONG_GATE`.
- `SYNC_FAILED`: technical/retryable failure, malformed item, timeout, or temporary server issue.
- `SYNC_CONFLICT`: offline device accepted locally, but server state disagrees, usually because another device already checked in the ticket.

MVP does not allow checker override. Conflicts are escalated to support/supervisor.

### Support Lookup

Support lookup is a read-only diagnostic capability. It must not imply direct admission and must not enable manual check-in.

Support/owner info responses should:

- Return masked PII only.
- Include enough ticket/event/status context to explain scan failures.
- Preserve stable result codes.
- Separate operational scan state from provenance history.

## OpenAPI / Swagger Convention

Future controller work must use Springdoc/OpenAPI annotations consistently when Springdoc is enabled.

Controller tags must be grouped by business capability, not by individual scan result. Do not create Swagger tags for result states such as `QR_EXPIRED`, `WRONG_GATE`, `ALREADY_USED`, `INVALID_QR_VERSION`, `LOCKED_RESALE`, or `CANCELLED`.

Planned controller tag mapping:

| Controller | Tag name | Description |
| --- | --- | --- |
| `QrTokenController` | `Buyer QR` | APIs for issuing short-lived Dynamic QR tokens for ticket owners |
| `CheckerAssignmentController` | `Checker Assignments` | APIs for retrieving checker event, showtime, gate, and shift assignments |
| `CheckinScanController` | `Checker Scan` | APIs for online checker QR validation and atomic ticket check-in |
| `OfflinePackageController` | `Offline Package` | APIs for generating scoped offline packages for checker devices |
| `OfflineSyncController` | `Offline Sync` | APIs for synchronizing offline scans and classifying accepted, rejected, failed, and conflict results |
| `SupportLookupController` | `Checker Support` | Support-only APIs for masked ticket ownership lookup and dispute context |

Controller conventions:

- Each controller must use `@Tag` from `io.swagger.v3.oas.annotations.tags.Tag`.
- Each endpoint should use `@Operation` with a concise summary and business-oriented description.
- Use `@ApiResponses` for transport-level errors such as 400, 401, 403, 404, and 500 where appropriate.
- HTTP status represents request, transport, authentication, authorization, and system status.
- Stable `resultCode` fields represent check-in business outcomes.
- Business-denied scan outcomes such as `ALREADY_USED`, `WRONG_GATE`, `QR_EXPIRED`, `INVALID_QR_VERSION`, `LOCKED_RESALE`, and `CANCELLED` must be represented by response body `resultCode`, not only by HTTP status.
- The scan API may return HTTP 200 for a successfully processed scan that is denied by business rules.

Any future implementation task that creates or modifies controllers must include this Definition of Done item:

- Controller endpoints are documented with `@Tag`, `@Operation`, and transport-level `@ApiResponses` where appropriate.

## Implementation Phases

### Task 0 - Foundation planning and docs

- Goal: create the foundation docs and align all decisions before Java domain work starts.
- Scope: write `docs/checkin-service-plan.md`, `docs/api-contract.md`, `docs/data-model.md`, and `docs/test-plan.md`.
- Files: docs only.
- APIs affected: planned only, no endpoint implementation.
- Tables affected: planned only, no migrations/entities.
- Tests: no code tests required beyond optional baseline context test.
- Risks: stale references to old package/version values.
- Definition of done: docs use `com.capstone.checkinservice`, Spring Boot `3.3.1`, Spring Cloud `2023.0.3`, group `com.capstone`; old references are absent.
- Must not implement: controllers, services, repositories, entities, DTOs, QR crypto, scan logic, offline sync.

### Task 1 - Verify sibling-aligned base conventions

- Goal: verify and complete base service conventions before domain code.
- Scope: check `application.yml`, security baseline, Actuator, Eureka, Feign, Redis/JPA configuration, Springdoc if sibling services use it, and tests.
- Files: `build.gradle`, `application.yml`, configuration packages, baseline test files if needed.
- APIs affected: none, except optional Swagger/OpenAPI exposure.
- Tables affected: none.
- Tests: context load test; config binding tests for security/internal-service properties if added.
- Risks: adding Springdoc too early may create unused dependencies; skipping it may delay gateway Swagger integration.
- Definition of done: service starts with sibling-style config, health endpoint works, tests pass.
- Must not implement: admission domain behavior or API controllers.

### Task 2 - Domain enums, DTO foundations, and result mapping

- Goal: define stable contracts without business logic.
- Scope: create enums for access status, scan mode, scan result, sync result, conflict status; create request/response DTOs and result message mapper.
- Files: enum package, DTO package, mapper/package-level tests.
- APIs affected: planned response shapes for all Checkin endpoints.
- Tables affected: none.
- Tests: enum serialization, required field validation, stable result code mapping.
- Risks: unstable enum names would break FE contract.
- Definition of done: all required result codes exist and serialize exactly as documented.
- Must not implement: QR signing, scan workflow, persistence.

### Task 3 - Database model for ticket access and check-in log

- Goal: add admission projection and operational audit persistence.
- Scope: entities/repositories for `ticket_access_state`, `check_in_log`, `checker_assignment`, `checker_device`, `offline_package`, and `offline_sync_item`.
- Files: entity/repository packages and repository tests.
- APIs affected: none.
- Tables affected: MVP tables listed in `data-model.md`.
- Tests: repository save/load tests, index/constraint assumptions, conditional update query tests.
- Risks: wrong constraints can allow duplicate projection rows or double use.
- Definition of done: repository can atomically mark one valid ticket used and reject a second update.
- Must not implement: controller endpoints or QR crypto.

### Task 4 - Dynamic QR token signing and verification

- Goal: implement token envelope signing and verification.
- Scope: key provider abstraction, signer/verifier, clock handling, TTL validation, token parsing.
- Files: QR/security utility packages and unit tests.
- APIs affected: none directly.
- Tables affected: optional `jti` audit only if justified later.
- Tests: sign/verify success, expired token, tamper, wrong key, invalid format, missing field, `jti`, `qrVersion`.
- Risks: treating signature as payload or leaking private key.
- Definition of done: verifier returns precise failures without exposing secrets.
- Must not implement: buyer QR endpoint or scan endpoint.

### Task 5 - Buyer QR issuing API

- Goal: expose current-owner QR issuing.
- Scope: `GET /api/v1/tickets/{ticketAssetId}/qr-token`, owner/status validation, response TTL.
- Files: controller, service, DTOs, security rule, tests.
- APIs affected: buyer QR endpoint.
- Tables affected: read `ticket_access_state`.
- Tests: owner success, non-owner rejected, `USED`, `LOCKED_RESALE`, `CANCELLED`, token verifies.
- Risks: issuing QR for stale owner after resale/transfer.
- Definition of done: active QR only issued to current owner for valid ticket state; controller endpoints are documented with `@Tag`, `@Operation`, and transport-level `@ApiResponses` where appropriate.
- Must not implement: buyer offline QR batch.

### Task 6 - Checker assignment and device readiness APIs

- Goal: let checker devices discover assigned events/showtimes/gates and register device readiness.
- Scope: assignments endpoint, device tracking, assignment validation helpers.
- Files: controller/service/repository DTOs for checker assignment and device.
- APIs affected: `GET /api/v1/checker/assignments`.
- Tables affected: `checker_assignment`, `checker_device`.
- Tests: assigned checker sees scoped assignments; unassigned checker gets none or forbidden for scan scope.
- Risks: unclear IAM role names.
- Definition of done: assignment checks can be reused by online scan and offline package generation; controller endpoints are documented with `@Tag`, `@Operation`, and transport-level `@ApiResponses` where appropriate.
- Must not implement: scan validation.

### Task 7 - Online scan validation and atomic check-in

- Goal: implement online-first checker scan.
- Scope: full validation chain and atomic `VALID` to `USED` update.
- Files: scan controller/service, repositories, log writing, tests.
- APIs affected: `POST /api/v1/checker/scan`.
- Tables affected: `ticket_access_state`, `check_in_log`.
- Tests: valid scan, second scan, expired QR, invalid signature, wrong event/showtime/gate, invalid version, locked resale, cancelled, concurrent double scan.
- Risks: race condition allowing duplicate check-in.
- Definition of done: only one concurrent scan succeeds and every scan writes the appropriate log; controller endpoints are documented with `@Tag`, `@Operation`, and transport-level `@ApiResponses` where appropriate.
- Must not implement: offline sync or override.

### Task 8 - Offline package generation

- Goal: generate scoped offline packages for checker devices.
- Scope: package creation, ticket snapshot filtering, checksum, package signature, public verification key metadata.
- Files: offline package controller/service/entities/tests.
- APIs affected: `POST /api/v1/checker/offline-packages`.
- Tables affected: `offline_package`, read `ticket_access_state`.
- Tests: scope enforcement, package expiry, no private secrets, package signature.
- Risks: over-broad package or PII leakage.
- Definition of done: package contains only assigned scope and safe verification material; controller endpoints are documented with `@Tag`, `@Operation`, and transport-level `@ApiResponses` where appropriate.
- Must not implement: buyer offline QR or offline sync.

### Task 9 - Offline sync, rejected, failed, and conflict handling

- Goal: reconcile provisional offline scans.
- Scope: sync endpoint, item processing, conflict classification, summary response.
- Files: offline sync controller/service/entities/tests.
- APIs affected: `POST /api/v1/checker/offline-sync`.
- Tables affected: `ticket_access_state`, `check_in_log`, `offline_sync_item`.
- Tests: accepted, already-used conflict, cancelled rejected, locked resale rejected, malformed failed, summary counts.
- Risks: checking QR expiration against current time instead of `scannedAt`.
- Definition of done: every item receives `SYNC_ACCEPTED`, `SYNC_REJECTED`, `SYNC_FAILED`, or `SYNC_CONFLICT`; controller endpoints are documented with `@Tag`, `@Operation`, and transport-level `@ApiResponses` where appropriate.
- Must not implement: checker conflict override.

### Task 10 - Support lookup and owner info

- Goal: provide read-only diagnostic owner/ticket context.
- Scope: masked owner info and support-oriented ticket state.
- Files: owner-info controller/service/DTOs/tests.
- APIs affected: `GET /api/v1/checker/tickets/{ticketAssetId}/owner-info`.
- Tables affected: read `ticket_access_state`, optionally `check_in_log`.
- Tests: PII masked, unauthorized access denied, no override action.
- Risks: leaking full email/phone or turning lookup into manual admission.
- Definition of done: response explains state without exposing sensitive data; controller endpoints are documented with `@Tag`, `@Operation`, and transport-level `@ApiResponses` where appropriate.
- Must not implement: manual check-in.

### Task 11 - Api-Gateway integration plan

- Goal: route Checkin-Service through Api-Gateway.
- Scope: gateway route and Swagger aggregation if Springdoc is enabled.
- Files: Api-Gateway config only in that later task.
- APIs affected: all Checkin-Service external endpoints.
- Tables affected: none.
- Tests: route smoke test and Swagger route if present.
- Risks: route prefix mismatch.
- Definition of done: `/checkin-service/**` routes to service and preserves auth headers.
- Must not implement: domain changes.

### Task 12 - Frontend integration contract plan

- Goal: document FE state mapping and timing.
- Scope: QR refresh states, scan result display, offline queue, sync summary, support lookup.
- Files: docs only or shared API contract update.
- APIs affected: no implementation changes.
- Tables affected: none.
- Tests: contract review with FE.
- Risks: FE relying on free-form messages instead of stable result codes.
- Definition of done: FE can map every documented result code to UI state.
- Must not implement: frontend UI.

## Key Assumptions

- Package remains `com.capstone.checkinservice`.
- Gradle group remains `com.capstone`.
- Java remains 21.
- Spring Boot remains 3.3.1 and Spring Cloud remains 2023.0.3.
- Checkin-Service keeps `checkin-service`, port 8087, and schema `checkin_service`.
- JWT and internal-service security should follow sibling service conventions.
- Current owner, access status, resale lock, cancellation, and transfer are sourced from Order/Payment/Resale state, then projected into Checkin-Service.
- MVP/dev projection data may be seeded directly or created through an internal reconciliation path.
- Production-like projection should be populated from Order/Payment/Resale/Mint events or internal reconciliation APIs.
- Gate policy uses `gate_policy_snapshot` or `allowedGateIds` on `ticket_access_state` for MVP.
- Missing/empty gate policy means gate validation is skipped or defaulted.
- CheckInLog is operational audit; TicketProvenance is ticket lifecycle/history.

## Open Questions

- Exact IAM role names for checker and support users.
- Whether the first projection feed should be event-driven or internal reconciliation API.
- Whether Springdoc should be added in Task 1 or deferred until controller endpoints exist.
