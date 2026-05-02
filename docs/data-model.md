# Checkin-Service Data Model

## Ownership Model

Checkin-Service owns an admission-optimized projection and operational check-in audit. It does not own the original purchase, payment, resale, cancellation, or blockchain minting source-of-truth.

Source-of-truth services:

- Order-Service: TicketAsset, ownership, resale lock, cancellation, TicketProvenance.
- Payment-Service: payment completion state.
- Resale logic in Order-Service: resale listing and transfer state.
- Inventory-Service: event, showtime, ticket type, venue, and gate metadata.
- Future minting/blockchain flow: token identity and mint status.

Projection source:

- MVP/dev may seed `ticket_access_state` directly or create it through an internal reconciliation path.
- Production-like direction should populate projection from Order/Payment/Resale/Mint events or internal reconciliation APIs.
- Checkin-Service owns atomic admission updates, especially `VALID` to `USED`.

## MVP Tables

### ticket_access_state

Purpose:

- Admission projection used for QR issuing, online scan, offline package generation, and sync validation.

Fields:

| Field | Purpose |
| --- | --- |
| `id` | Internal primary key. |
| `ticket_asset_id` | Original TicketAsset id from Order-Service. |
| `ticket_code` | Human/display ticket code when available. |
| `order_id` | Original order id, nullable. |
| `event_id` | Event id used for assignment and scan validation. |
| `showtime_id` | Showtime id used for scan validation. |
| `ticket_type_name` | Display name included in packages and responses. |
| `zone_label` | Optional zone label. |
| `seat_label` | Optional seat label. |
| `current_owner_id` | Current owner user id projected from source-of-truth. |
| `qr_version` | Version used to invalidate QR after resale/transfer. |
| `access_status` | `VALID`, `LOCKED_RESALE`, `USED`, or `CANCELLED`. |
| `allowed_gate_ids` | Optional simple gate allow-list for MVP, stored as a JSON string in a text column. |
| `gate_policy_snapshot` | Optional JSON policy snapshot string if allow-list is not enough. |
| `used_at` | Server time when final check-in succeeded. |
| `used_by_checker_id` | Checker id that completed final online/synced check-in. |
| `used_at_gate_id` | Gate id used during final check-in. |
| `version` | Optimistic locking version. |
| `created_at` | Creation timestamp. |
| `updated_at` | Last update timestamp. |

Indexes:

- Unique index on `ticket_asset_id`.
- Index on `current_owner_id`.
- Index on `(event_id, showtime_id)`.
- Index on `access_status`.
- Index on `(event_id, showtime_id, access_status)` for package generation.

Unique constraints:

- `ticket_asset_id` must be unique.

Atomic update strategy:

- Online/synced success must use a conditional update similar to:
  `UPDATE ticket_access_state SET access_status='USED', used_at=?, used_by_checker_id=?, used_at_gate_id=?, version=version+1 WHERE ticket_asset_id=? AND qr_version=? AND access_status='VALID'`.
- If affected rows is 1, check-in succeeded.
- If affected rows is 0, reload current state and return the precise result: `ALREADY_USED`, `INVALID_QR_VERSION`, `LOCKED_RESALE`, `CANCELLED`, or `TICKET_NOT_FOUND`.
- Task 7 online scan implements this conditional update through `TicketAccessStateRepository.markUsedIfValid(...)`.

### check_in_log

Purpose:

- Operational audit for every online scan, offline accepted result, sync result, rejection, and conflict.

Fields:

| Field | Purpose |
| --- | --- |
| `id` | Primary key. |
| `ticket_asset_id` | TicketAsset id from QR/projection. |
| `event_id` | Event context used during scan. |
| `showtime_id` | Showtime context used during scan. |
| `gate_id` | Actual gate id used during scan, nullable. |
| `checker_id` | Checker user id. |
| `device_id` | Checker device id, nullable. |
| `scan_mode` | `ONLINE`, `OFFLINE_LOCAL`, or `OFFLINE_SYNC`. |
| `scan_result` | Stable scan result code. |
| `qr_token_id` | QR `jti`; do not store raw token. |
| `scanned_at` | Time scan happened. |
| `synced_at` | Time offline item synced, nullable. |
| `conflict_status` | Nullable conflict state, such as `NONE`, `CONFLICT`, `ESCALATED`, `RESOLVED`. |
| `failure_reason` | Human-readable failure reason for ops, nullable. |
| `raw_error_code` | Technical error code, nullable. |
| `created_at` | Creation timestamp. |

Indexes:

- Index on `ticket_asset_id`.
- Index on `(event_id, showtime_id)`.
- Index on `checker_id`.
- Index on `device_id`.
- Index on `qr_token_id`.
- Index on `scanned_at`.
- Index on `scan_result`.

Unique constraints:

- Optional unique constraint on `qr_token_id` plus `scan_mode` is not recommended for MVP because the same QR may be scanned multiple times and must be logged. Use conflict/business logic instead.

Task 7 online scan logging:

- Writes `CheckInLog` for successful online scans and business-denied online scans where the signed QR payload or ticket id can be identified.
- Stores QR `jti` as `qr_token_id`; it does not store the raw QR token.
- Does not log totally invalid or untrusted QR tokens because `ticket_asset_id` is currently required and the service does not trust unsigned payload data.

Task 9 offline sync logging:

- Writes `CheckInLog` for offline sync items when enough identifiers are available.
- Uses `scan_mode=OFFLINE_SYNC`.
- Uses `scan_result=SYNC_ACCEPTED`, `SYNC_REJECTED`, `SYNC_FAILED`, or `SYNC_CONFLICT` so operations can distinguish final sync outcomes from online scan results.
- Stores the original offline `scanned_at` and the server-side `synced_at`.
- Stores QR `jti` as `qr_token_id` when available and never stores the raw QR token.
- Sets `conflict_status=CONFLICT` for `SYNC_CONFLICT`; otherwise uses `NONE`.
- Stores the server business result such as `VALID_CHECKED_IN`, `ALREADY_USED`, `WRONG_GATE`, or `INVALID_SIGNATURE` in `raw_error_code` for compact operational context.

### checker_assignment

Purpose:

- Local assignment model for validating checker access to event/showtime/gate.

Fields:

| Field | Purpose |
| --- | --- |
| `id` | Primary key. |
| `checker_id` | IAM user id for checker. |
| `event_id` | Assigned event. |
| `showtime_id` | Assigned showtime. |
| `allowed_gate_ids` | Optional assigned gates as a JSON string. Empty means all/default gates for that assignment. |
| `role_snapshot` | Role or assignment label, such as checker/supervisor. |
| `valid_from` | Assignment start time. |
| `valid_until` | Assignment end time. |
| `active` | Soft enable/disable flag. |
| `created_at` | Creation timestamp. |
| `updated_at` | Last update timestamp. |

Indexes:

- Index on `checker_id`.
- Index on `(event_id, showtime_id)`.
- Index on `(checker_id, event_id, showtime_id, active)`.

Unique constraints:

- Recommended unique constraint on `(checker_id, event_id, showtime_id, active)` only if duplicate active assignment rows are not required. If gate-level rows are preferred, include gate id/policy hash in the uniqueness rule.

### checker_device

Purpose:

- Track checker devices used for offline packages and sync.

Fields:

| Field | Purpose |
| --- | --- |
| `id` | Primary key. |
| `device_id` | Client-generated or server-issued stable device id. |
| `checker_id` | IAM user id that registered or last used the device. |
| `device_name` | Display name, nullable. |
| `platform` | Web, Android, iOS, or other. |
| `user_agent` | Client user agent string, nullable. |
| `app_version` | Checker app/PWA version, nullable. |
| `registered_at` | Server time when device was registered. |
| `trusted_at` | Server time when device was trusted, nullable. |
| `last_seen_at` | Last online contact. |
| `trusted` | Whether device is allowed to download offline packages. |
| `revoked_at` | Device revocation time, nullable. |
| `created_at` | Creation timestamp. |
| `updated_at` | Last update timestamp. |

Indexes:

- Unique index on `device_id`.
- Index on `checker_id`.
- Index on `(checker_id, trusted)`.

Effective device status:

- `PENDING`: `trusted=false` and `revoked_at is null`.
- `TRUSTED`: `trusted=true` and `revoked_at is null`.
- `REVOKED`: `revoked_at is not null`.

Rules:

- Device id is generated by the server during checker device registration.
- Database `id` is internal only; API routes use external/business `device_id`.
- New checker-facing registrations are pending/untrusted by default.
- Offline package generation requires `TRUSTED`.
- Online scan validates a provided device id, and may require one when `checkin.checker.device.required-for-online-scan=true`.
- Public checker APIs do not allow self-trust. Device trust/revoke is owned by Checkin-Service management APIs and is allowed for `ORGANIZER` and `ADMIN`, not `CHECKER`.

### offline_package

Purpose:

- Server record of an offline package issued to a checker device.

Fields:

| Field | Purpose |
| --- | --- |
| `id` | Primary key. |
| `package_id` | Public package identifier. |
| `event_id` | Package event scope. |
| `showtime_id` | Package showtime scope. |
| `gate_id` | Optional gate scope. |
| `checker_id` | Checker that downloaded package. |
| `device_id` | Device that downloaded package. |
| `issued_at` | Package issue time. |
| `valid_until` | Package expiry time. |
| `key_id` | QR verification key id/version at issue time. |
| `ticket_count` | Number of ticket snapshots. |
| `checksum` | Checksum over package content. |
| `package_signature` | Signature over package content. |
| `status` | `ACTIVE`, `EXPIRED`, `REVOKED` if needed. |
| `created_at` | Creation timestamp. |

Indexes:

- Unique index on `package_id`.
- Index on `(checker_id, device_id)`.
- Index on `(event_id, showtime_id, gate_id)`.
- Index on `valid_until`.

Unique constraints:

- `package_id` must be unique.

Storage note:

- The full package body may be returned to the client and optionally stored as compressed JSON if needed for audit/debug. If stored, it must exclude private secrets and full sensitive PII.
- Task 8 persists package metadata only; ticket snapshots are generated from current `ticket_access_state` and returned to the checker device.
- Task 8 stores `package_signature` as null because there is no dedicated package-signing key infrastructure yet.
- Task 8 checksum covers deterministic package metadata plus ticket snapshots and excludes `checksum` and `package_signature`.
- Task 8 exposes QR public verification key material as Base64-encoded public key bytes plus `key_id`; it never exposes private key material.
- Task 8 package TTL defaults to `checkin.offline-package.ttl-minutes=360`; max snapshot count defaults to `checkin.offline-package.max-ticket-snapshots=5000`.
- Task 8.5 hardening requires a trusted checker device before package metadata is persisted.
- Task 8.5 package snapshots expose sanitized `allowedGateIds` and `usedAtGateId`; raw `gatePolicySnapshot` is no longer returned.

### offline_sync_item

Purpose:

- Track each pending offline scan item synced by a checker device.
- Task 9 implements this MVP persistence model and uses `(package_id, local_scan_id)` for idempotency.

Fields:

| Field | Purpose |
| --- | --- |
| `id` | Primary key. |
| `package_id` | Offline package id. |
| `local_scan_id` | Client local id for idempotency. |
| `ticket_asset_id` | TicketAsset id extracted from QR. |
| `qr_token_id` | QR `jti`; do not store raw QR token. |
| `checker_id` | Checker id. |
| `device_id` | Device id. |
| `event_id` | Event context. |
| `showtime_id` | Showtime context. |
| `gate_id` | Gate context. |
| `local_result_code` | Usually `OFFLINE_ACCEPTED_PENDING_SYNC`. |
| `sync_result` | `SYNC_ACCEPTED`, `SYNC_REJECTED`, `SYNC_FAILED`, or `SYNC_CONFLICT`. |
| `server_scan_result` | Server result code after revalidation. |
| `scanned_at` | Original offline scan time. |
| `synced_at` | Server sync time. |
| `conflict_details` | JSON details for support escalation, nullable. |
| `failure_reason` | Failure/rejection explanation, nullable. |
| `created_at` | Creation timestamp. |

Indexes:

- Unique index on `(package_id, local_scan_id)` for idempotency.
- Index on `ticket_asset_id`.
- Index on `qr_token_id`.
- Index on `(sync_result, synced_at)`.
- Index on `(checker_id, device_id)`.

Task 9 sync persistence behavior:

- `SYNC_ACCEPTED` items are persisted after the same conditional `ticket_access_state` update used by online scan succeeds.
- `SYNC_REJECTED`, `SYNC_FAILED`, and `SYNC_CONFLICT` items are persisted when `local_scan_id` and a ticket identifier are available.
- Duplicate retries are resolved by `package_id + local_scan_id`; the service returns the existing stored result and does not create duplicate `CheckInLog` rows.
- Conflict metadata is stored as JSON in `conflict_details` when a server-used ticket conflicts with local offline acceptance.
- `qr_token_id` stores QR `jti` only. Raw QR tokens are not persisted.

## Future Extension Tables

### qr_token_audit

Optional. Use only if issuing/audit requirements justify it.

Potential fields:

- `id`
- `jti`
- `ticket_asset_id`
- `issued_to_user_id`
- `kid`
- `issued_at`
- `expires_at`
- `used_for_scan_at`
- `created_at`

Rules:

- Store `jti`, key id, and timestamps only.
- Do not store raw QR token.

### ticket_access_gate

Optional normalized gate policy table if `allowed_gate_ids` or `gate_policy_snapshot` becomes too limited.

Potential fields:

- `id`
- `ticket_access_state_id`
- `gate_id`
- `policy_source`
- `created_at`

### support_conflict_case

Optional supervisor/support escalation table if conflict workflows need ownership, comments, or resolution status.

Potential fields:

- `id`
- `ticket_asset_id`
- `offline_sync_item_id`
- `opened_by_checker_id`
- `assigned_to_user_id`
- `status`
- `resolution_note`
- `created_at`
- `resolved_at`

## Gate Policy Approach

MVP uses `gate_policy_snapshot` or `allowed_gate_ids` on `ticket_access_state`.

Rules:

- If no gate policy exists, gate validation is skipped or defaulted.
- If `allowed_gate_ids` or `gate_policy_snapshot` exists and current `gateId` is not allowed, return `WRONG_GATE`.
- Malformed `allowed_gate_ids` or gate policy data fails closed; it must not be treated as unrestricted access.
- `check_in_log.gate_id` always stores the actual gate used during scan.
- Offline packages include sanitized `allowedGateIds` needed for local validation, not raw internal gate policy JSON.

## CheckInLog vs TicketProvenance

`CheckInLog`:

- Owned by Checkin-Service.
- Operational audit for scans, check-ins, offline local results, sync processing, and conflicts.
- Used by gate operations, support, incident review, and scan troubleshooting.
- Stores `qr_token_id`/`jti`, not raw QR token.
- Task 10 owner-info reads recent `CheckInLog` rows for a ticket and returns only safe support fields: scan mode, scan result, scanned/synced timestamps, gate id, checker id, device id, failure reason, and conflict status. It does not return `qr_token_id`.

`TicketProvenance`:

- Owned by Order-Service or the ticket lifecycle service.
- Ticket lifecycle/history for user, admin, and blockchain-facing provenance.
- Covers primary issue, resale listed, resale cancelled, resale purchased, ownership transferred, QR rotated, and similar lifecycle events.
- Not a replacement for scan operational logs.

## Data Privacy Notes

- Support owner info must mask PII.
- Task 10 owner-info returns `supportOnly=true`, `canOverride=false`, and no state-changing action.
- Task 10 owner-info uses `ticket_access_state.current_owner_id` as a masked owner reference when no IAM/user profile adapter is available.
- If a future IAM/user profile adapter supplies owner display name, email, or phone, those values must be masked before they leave Checkin-Service.
- Offline packages must not include full email, full phone, payment data, or unnecessary user profile data.
- Store `jti`/`qr_token_id`, not raw QR token.
- Store public verification key or key version, never private signing key.
- Device metadata should be minimal and operationally necessary.

## What Not To Store

- Raw QR secret.
- Buyer-side QR seed.
- Private QR signing key.
- JWT secret.
- Full raw QR token unless a future security review explicitly approves a short-lived encrypted audit store.
- Full sensitive PII in offline packages.
- Payment card/payment gateway details.
- Manual checker override decisions in MVP.
