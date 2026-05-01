# Checkin-Service Test Plan

## Overall Strategy

Testing should protect the admission contract, QR security, concurrency behavior, and offline conflict handling. The service must be tested at four levels:

- Unit tests for pure validation, QR crypto, result mapping, and DTO behavior.
- Repository tests for persistence, constraints, and atomic updates.
- API tests for request/response contracts, auth, and validation.
- Integration/concurrency tests for double scan and offline sync behavior.

Stable enum result codes are the contract. Tests should assert enum names and JSON values directly.

## Unit Tests

### Result codes and mapping

- Every required result code exists.
- Result codes serialize as exact strings.
- UI title/message mapper returns deterministic text for each code.
- Free-form message changes do not change the enum source of truth.

### Gate policy validation

- Missing gate policy skips/defaults gate validation.
- Empty gate policy skips/defaults gate validation.
- Present `allowedGateIds` containing current `gateId` passes.
- Present `allowedGateIds` missing current `gateId` returns `WRONG_GATE`.
- `CheckInLog` model always captures actual `gateId` when provided.

## QR Crypto Tests

Test class target: QR signer/verifier service when Task 4 is implemented.

Required cases:

- Sign and verify success.
- Expired token is rejected with `QR_EXPIRED`.
- Tampered payload is rejected with `INVALID_SIGNATURE`.
- Tampered header/key id is rejected with `INVALID_SIGNATURE` or `INVALID_QR`.
- Wrong key is rejected.
- Invalid token format is rejected with `INVALID_QR`.
- Missing `ticketAssetId`, `eventId`, `showtimeId`, `qrVersion`, `issuedAt`, `expiresAt`, or `jti` is rejected.
- `jti` is preserved after parsing.
- `qrVersion` is preserved after parsing.
- Signature is verified over header plus payload; signature is not treated as a payload field.
- Private key is not exposed by key provider API or offline package DTO.

## Buyer QR Issuing Tests

Endpoint target: `GET /api/v1/tickets/{ticketAssetId}/qr-token`.

Required cases:

- Valid current owner can request QR.
- Non-owner is rejected.
- Missing ticket projection returns `TICKET_NOT_FOUND`.
- `USED` ticket is rejected with `ALREADY_USED`.
- `LOCKED_RESALE` ticket is rejected with `LOCKED_RESALE`.
- `CANCELLED` ticket is rejected with `CANCELLED`.
- Ownership cannot be verified returns an auth/ownership failure and does not issue QR.
- Response includes `expiresAt` and `refreshAfterSeconds`.
- Returned token verifies.
- Returned token payload contains expected claims.
- Returned token envelope uses header/metadata, payload claims, and signature.
- Raw secret seed is never returned.

## Online Scan Tests

Endpoint target: `POST /api/v1/checker/scan`.

Required cases:

- Valid scan marks ticket `USED`.
- Valid scan sets `usedAt`.
- Valid scan sets `usedByCheckerId`.
- Valid scan sets `usedAtGateId` when gate is provided.
- Valid scan creates `CheckInLog` with `scanMode=ONLINE`.
- Valid scan returns `VALID_CHECKED_IN`.
- Second scan returns `ALREADY_USED`.
- Expired QR returns `QR_EXPIRED`.
- Invalid format returns `INVALID_QR`.
- Invalid signature returns `INVALID_SIGNATURE`.
- Wrong event returns `WRONG_EVENT`.
- Wrong showtime returns `WRONG_SHOWTIME`.
- Wrong gate returns `WRONG_GATE`.
- QR version mismatch returns `INVALID_QR_VERSION`.
- `LOCKED_RESALE` returns `LOCKED_RESALE`.
- `CANCELLED` returns `CANCELLED`.
- Unknown ticket returns `TICKET_NOT_FOUND`.
- Unassigned checker returns `UNAUTHORIZED_CHECKER`.
- Device time outside tolerance returns `DEVICE_TIME_INVALID` when time validation is active.

## Double Scan and Concurrency Tests

Repository/service target: atomic check-in update.

Required cases:

- Two concurrent scan requests for the same valid ticket allow exactly one `VALID_CHECKED_IN`.
- Losing request returns `ALREADY_USED` after state reload.
- Concurrent scan with stale `qrVersion` returns `INVALID_QR_VERSION`.
- Conditional update affects exactly one row for a valid ticket.
- Conditional update affects zero rows for `USED`, `LOCKED_RESALE`, `CANCELLED`, or mismatched `qrVersion`.
- `CheckInLog` records both attempts with their final result codes.

Implementation guidance:

- Use a real database integration test where possible.
- If using Testcontainers later, run PostgreSQL to test actual transaction behavior.
- If Testcontainers is not added yet, use repository tests plus service-level concurrency tests with controlled synchronization.

## Offline Package Tests

Endpoint target: `POST /api/v1/checker/offline-packages`.

Required cases:

- Package is created for assigned checker/device scope.
- Package only includes tickets for requested `eventId`.
- Package only includes tickets for requested `showtimeId`.
- Package only includes gate-allowed tickets when `gateId` is provided.
- Unassigned checker cannot create package.
- Package has `issuedAt` and `validUntil`.
- Package includes public verification key or key id/version.
- Package includes `ticketAssetId`, `ticketCode`, `ticketTypeName`, `zoneLabel`, `seatLabel`, `qrVersion`, `accessStatus`, `usedAt`, and gate permission snapshot.
- Package does not contain private signing key.
- Package does not contain raw QR secret.
- Package does not contain JWT secret.
- Package does not contain full email or phone.
- Package checksum verifies.
- Package signature verifies.

## Offline Local Validation Test Scenarios

These tests may live in frontend/mobile code later, but backend contract tests should document expected behavior.

Required scenarios:

- Package missing returns `OFFLINE_PACKAGE_NOT_FOUND`.
- Package expired returns `OFFLINE_PACKAGE_EXPIRED`.
- Device time invalid returns `DEVICE_TIME_INVALID`.
- QR token signature verifies with package public key/key version.
- QR expiration is checked against local `scannedAt`.
- Ticket not in package is rejected.
- Event mismatch returns `WRONG_EVENT`.
- Showtime mismatch returns `WRONG_SHOWTIME`.
- Gate mismatch returns `WRONG_GATE`.
- QR version mismatch returns `INVALID_QR_VERSION`.
- Snapshot status other than `VALID` is rejected.
- Ticket already used locally on same device returns local already-used state.
- Accepted local scan creates pending sync item with `OFFLINE_ACCEPTED_PENDING_SYNC`.

## Offline Sync Tests

Endpoint target: `POST /api/v1/checker/offline-sync`.

Required cases:

- Accepted sync marks ticket `USED`.
- Accepted sync writes `CheckInLog` with sync metadata.
- Accepted sync returns `SYNC_ACCEPTED`.
- Already used by another device returns `SYNC_CONFLICT`.
- Conflict response includes local result, server result, local scannedAt, current gate, first successful server check-in if available, and checker/device metadata if available.
- Cancelled ticket returns `SYNC_REJECTED`.
- Locked resale ticket returns `SYNC_REJECTED`.
- Wrong event returns `SYNC_REJECTED` with `WRONG_EVENT`.
- Wrong showtime returns `SYNC_REJECTED` with `WRONG_SHOWTIME`.
- Wrong gate returns `SYNC_REJECTED` with `WRONG_GATE`.
- Malformed item returns `SYNC_FAILED` or validation error according to API design.
- Package not found returns `OFFLINE_PACKAGE_NOT_FOUND`.
- Package expired returns `OFFLINE_PACKAGE_EXPIRED` unless policy allows historical sync for packages valid at `scannedAt`; this policy must be explicit before implementation.
- QR expiration is checked at item `scannedAt`, not current server time.
- Sync summary counts accepted, rejected, failed, and conflict items correctly.
- Duplicate `(packageId, localScanId)` is idempotent.

## Repository Tests

Required cases:

- `ticket_access_state.ticket_asset_id` is unique.
- `offline_package.package_id` is unique.
- `checker_device.device_id` is unique.
- `offline_sync_item(package_id, local_scan_id)` is unique.
- `ticket_access_state` can be queried by owner for QR issuing.
- `ticket_access_state` can be queried by event/showtime/status for offline package generation.
- `check_in_log` can be queried by ticket for support lookup.
- Conditional `VALID` to `USED` update succeeds only once.

## API Tests

Required cases:

- Buyer QR endpoint requires authentication.
- Checker assignment endpoint requires checker authorization.
- Scan endpoint requires checker authorization.
- Offline package endpoint requires checker authorization.
- Offline sync endpoint requires checker authorization.
- Owner-info endpoint masks PII.
- Validation errors return structured error response.
- Every scan API response includes stable `resultCode`.
- HTTP status does not replace result code for business outcomes. For example, `ALREADY_USED` may be `200` with a non-success result code, while malformed requests may be `400`.

## Security and Privacy Tests

Required cases:

- Private QR signing key is loaded from environment/config, not hardcoded.
- JWT secret is not logged or returned.
- Raw QR token is not stored in `CheckInLog`.
- `jti` is stored instead of raw token.
- Offline package does not contain private key, raw secret, JWT secret, full email, or full phone.
- Owner info masks email and phone.
- Support lookup does not expose override or manual admission actions.
- Unassigned checker cannot scan event/showtime/gate.
- Revoked/untrusted device cannot download offline package if device trust is enabled.

## Manual Test Checklist

Buyer QR:

- Buyer opens a valid ticket and sees active QR.
- QR refreshes before expiry.
- Network lost state is shown when QR refresh fails.
- Used, locked resale, cancelled, and transferred/no-longer-owner states are shown.

Checker online:

- Checker sees assigned events.
- Valid online scan returns checked-in state.
- Second scan returns already-used state.
- Wrong event/showtime/gate states are visible.
- Expired QR and invalid signature states are visible.

Offline:

- Checker downloads package while online.
- Offline scan accepts valid ticket as pending sync.
- Offline package expired state is visible.
- Device time invalid state is visible.
- Sync queue shows pending items.
- Sync accepted, rejected, failed, and conflict states are visible.

Support:

- Owner info is masked.
- Support lookup does not provide manual admission.

## Commands To Run

Windows:

```powershell
.\gradlew.bat clean test
.\gradlew.bat bootJar
```

Unix/macOS:

```bash
./gradlew clean test
./gradlew bootJar
```

For docs-only changes, tests are optional, but the baseline test command should still pass before starting Task 1 implementation.
