# Checkin-Service API Contract

## Contract Baseline

All APIs should follow the sibling-service response style:

```json
{
  "status": 200,
  "message": "Fetched successfully",
  "data": {}
}
```

Errors should use a structured error response with HTTP status, machine-readable code, message, path, and timestamp, consistent with sibling `GlobalExceptionHandler` patterns.

Authentication assumptions:

- Buyer endpoints require an authenticated buyer JWT.
- Checker endpoints require authenticated checker role plus assignment validation.
- Support lookup requires checker/supervisor/support authorization as later finalized with IAM role names.
- Internal projection/reconciliation endpoints, if later added, require internal-service authentication and are not part of this public contract.

## Swagger Tag Convention

Controllers must use `@Tag` from `io.swagger.v3.oas.annotations.tags.Tag`. Tags are grouped by business capability, not by individual result states. Do not create Swagger tags for `QR_EXPIRED`, `WRONG_GATE`, `ALREADY_USED`, `INVALID_QR_VERSION`, `LOCKED_RESALE`, `CANCELLED`, or other scan result values.

Planned controller tag mapping:

| Controller | Tag name | Description |
| --- | --- | --- |
| `QrTokenController` | `Buyer QR` | APIs for issuing short-lived Dynamic QR tokens for ticket owners |
| `CheckerAssignmentController` | `Checker Assignments` | APIs for retrieving checker event, showtime, gate, and shift assignments |
| `CheckinScanController` | `Checker Scan` | APIs for online checker QR validation and atomic ticket check-in |
| `OfflinePackageController` | `Offline Package` | APIs for generating scoped offline packages for checker devices |
| `OfflineSyncController` | `Offline Sync` | APIs for synchronizing offline scans and classifying accepted, rejected, failed, and conflict results |
| `SupportLookupController` | `Checker Support` | Support-only APIs for masked ticket ownership lookup and dispute context |

Endpoint annotation conventions:

- Each endpoint should use `@Operation` with a concise summary and business-oriented description.
- Use `@ApiResponses` for transport-level errors such as 400, 401, 403, 404, and 500 where appropriate.
- HTTP status represents request, transport, authentication, authorization, and system status.
- Stable `resultCode` represents check-in business outcome.
- Business scan outcomes such as `ALREADY_USED`, `WRONG_GATE`, `QR_EXPIRED`, `INVALID_QR_VERSION`, `LOCKED_RESALE`, and `CANCELLED` should be represented by stable `resultCode` fields in the response body, not only by HTTP status.
- The scan API may return HTTP 200 when a scan request is validly processed but denied by business rules.

Example: HTTP 200 with a business-denied scan result.

```json
{
  "status": 200,
  "message": "Scan processed",
  "data": {
    "resultCode": "WRONG_GATE",
    "ticketAssetId": 12345,
    "eventId": 99,
    "showtimeId": 501,
    "gateId": "B1"
  }
}
```

## QR Token Envelope

The QR token is an envelope. The signature is not a field inside the payload.

Header/metadata:

- `alg`: signing algorithm. MVP uses `SHA256withECDSA`.
- `kid`: key id used by the verifier.
- `typ`: token type if applicable, for example `evoticket-qr`.

Payload claims:

- `ticketAssetId`
- `ticketCode`
- `eventId`
- `showtimeId`
- `qrVersion`
- `issuedAt`
- `expiresAt`
- `jti`

Signature:

- Generated over header plus payload as part of the token envelope.
- The MVP envelope is an EvoTicket QR token envelope, not strict JWS. ECDSA signature bytes are Java DER-encoded signature bytes.
- Private signing key is never sent to clients or offline packages.
- Offline packages may include public verification key material or key version metadata only.

Recommended QR TTL is 20 to 30 seconds. Recommended frontend refresh interval is 15 to 20 seconds.

## QR Key Deployment

Checkin-Service requires deployment-provided EC key material for QR signing. The application reads these environment variables at startup:

- `QR_KEY_ID`
- `QR_PRIVATE_KEY_BASE64`
- `QR_PUBLIC_KEY_BASE64`

`QR_PRIVATE_KEY_BASE64` must be a Base64-encoded PKCS8 EC private key. `QR_PUBLIC_KEY_BASE64` must be a Base64-encoded X509 EC public key. Startup must fail if any value is missing, blank, or invalid.

Local PowerShell example:

```powershell
$env:QR_KEY_ID="qr-key-local-v1"
$env:QR_PRIVATE_KEY_BASE64="<generated-private-key>"
$env:QR_PUBLIC_KEY_BASE64="<generated-public-key>"
.\gradlew.bat bootRun
```

Generate local key material with:

```powershell
javac .\tools\GenerateQrKeyPair.java
java -cp .\tools GenerateQrKeyPair
```

Production rules:

- Provide QR environment variables through server environment, Docker secret, CI/CD secret, or a platform secret manager.
- Do not commit the private key.
- Do not bake the private key into a Docker image.
- All Checkin-Service instances must use the same active QR key set.
- Rotate keys using a future `keyId`/`kid` strategy.

## Stable Result Codes

| Result code | UI meaning |
| --- | --- |
| `VALID_CHECKED_IN` | Online scan succeeded and ticket is final-used. |
| `ALREADY_USED` | Ticket was already checked in. |
| `QR_EXPIRED` | QR token expired. |
| `INVALID_QR` | Token format or required claims are invalid. |
| `INVALID_SIGNATURE` | Signature verification failed. |
| `INVALID_QR_VERSION` | QR version no longer matches the current ticket state, usually after resale or transfer. |
| `WRONG_EVENT` | Ticket does not belong to selected event. |
| `WRONG_SHOWTIME` | Ticket does not belong to selected showtime. |
| `WRONG_GATE` | Ticket is not allowed at selected gate. |
| `LOCKED_RESALE` | Ticket is locked for resale/transfer and cannot be checked in. |
| `CANCELLED` | Ticket was cancelled, refunded, or revoked. |
| `OFFLINE_ACCEPTED_PENDING_SYNC` | Offline device accepted locally; server has not finalized it yet. |
| `SYNC_ACCEPTED` | Offline scan synced and server marked ticket used. |
| `SYNC_REJECTED` | Offline scan rejected by current business state. |
| `SYNC_FAILED` | Retryable or technical sync failure. |
| `SYNC_CONFLICT` | Offline local result conflicts with current server state. |
| `OWNERSHIP_MISMATCH` | Current authenticated buyer is not the ticket owner. |
| `UNAUTHORIZED_CHECKER` | Checker is not allowed for event/showtime/gate. |
| `TICKET_NOT_FOUND` | Ticket projection was not found. |
| `OFFLINE_PACKAGE_EXPIRED` | Offline package is expired. |
| `OFFLINE_PACKAGE_NOT_FOUND` | Package id is unknown or not assigned to this checker/device. |
| `OFFLINE_PACKAGE_TOO_LARGE` | Requested offline package exceeds the configured snapshot limit. |
| `DEVICE_NOT_ALLOWED` | Device is missing, unknown, or not owned by the current checker. |
| `DEVICE_NOT_TRUSTED` | Device is registered but pending trust approval. |
| `DEVICE_REVOKED` | Device has been revoked and cannot be used. |
| `DEVICE_MISMATCH` | `X-Device-Id` header and request body device id do not match. |
| `DEVICE_TIME_INVALID` | Device scan time is outside accepted tolerance. |

## GET /api/v1/tickets/{ticketAssetId}/qr-token

Swagger tag: `Buyer QR`

Purpose:

- Issue a short-lived dynamic QR token for the current ticket owner.

Auth/role:

- Authenticated buyer.
- User id from JWT must match `ticket_access_state.currentOwnerId`.

Path fields:

- `ticketAssetId`: ticket asset id.

Request fields:

- No body.

Response fields:

- `ticketAssetId`
- `ticketCode`
- `eventId`
- `showtimeId`
- `qrVersion`
- `qrToken`
- `issuedAt`
- `expiresAt`
- `refreshAfterSeconds`

Success example:

```json
{
  "status": 200,
  "message": "QR token issued successfully",
  "data": {
    "ticketAssetId": 12345,
    "ticketCode": "TCK-001",
    "eventId": 99,
    "showtimeId": 501,
    "qrVersion": 3,
    "qrToken": "header.payload.signature",
    "issuedAt": "2026-05-01T10:00:00Z",
    "expiresAt": "2026-05-01T10:00:30Z",
    "refreshAfterSeconds": 15
  }
}
```

Failure example:

```json
{
  "status": 409,
  "message": "Ticket is locked for resale",
  "data": {
    "resultCode": "LOCKED_RESALE",
    "title": "Ticket locked",
    "message": "This ticket cannot display an active QR while it is locked for resale."
  }
}
```

Result codes:

- `TICKET_NOT_FOUND`
- `OWNERSHIP_MISMATCH`
- `LOCKED_RESALE`
- `CANCELLED`
- `ALREADY_USED`
- `INVALID_QR_VERSION` if projection is stale or invalid

Business notes:

- Do not issue active QR for `USED`, `LOCKED_RESALE`, or `CANCELLED`.
- Do not issue active QR when ownership cannot be verified.
- Do not support buyer offline QR batch in MVP.
- Do not send raw secret seed to the client.

## GET /api/v1/checker/assignments

Swagger tag: `Checker Assignments`

Purpose:

- Return event/showtime/gate assignments available to the authenticated checker.

Auth/role:

- Authenticated checker role.

Request fields:

- Optional query fields may be added later, such as `eventId` or `activeOnly`.

Response fields:

- `assignments[]`
- `assignmentId`
- `eventId`
- `eventName`
- `showtimeId`
- `showtimeLabel`
- `gateIds[]`
- `role`
- `validFrom`
- `validUntil`

Success example:

```json
{
  "status": 200,
  "message": "Fetched checker assignments successfully",
  "data": {
    "assignments": [
      {
        "assignmentId": 10,
        "eventId": 99,
        "eventName": "Evo Music Night",
        "showtimeId": 501,
        "showtimeLabel": "2026-05-01 19:00",
        "gateIds": ["A1", "A2"],
        "role": "CHECKER",
        "validFrom": "2026-05-01T16:00:00Z",
        "validUntil": "2026-05-01T23:00:00Z"
      }
    ]
  }
}
```

Failure example:

```json
{
  "status": 403,
  "message": "Checker is not authorized",
  "data": {
    "resultCode": "UNAUTHORIZED_CHECKER"
  }
}
```

Result codes:

- `UNAUTHORIZED_CHECKER`

Business notes:

- Assignment validation is reused by online scan, offline package generation, and offline sync.
- MVP returns active assignments that are currently valid based on `validFrom` and `validUntil`.
- If `allowedGateIds` is absent or empty, later validation treats all/default gates as allowed.
- If `allowedGateIds` is present, later validation requires the requested `gateId` to be included.
- Checker-facing assignment endpoints use the `CHECKER` role. Checker device approval routes use `ORGANIZER` or `ADMIN`.

## Checker Device Management

Checker identity and checker device trust are separate. Authentication resolves the checker; the server-side device record decides whether a client installation is allowed to operate. Device id is not proof by itself.

Device approval belongs to Checkin-Service, not IAM-Service. IAM authenticates users and supplies roles/identity claims. Checkin-Service owns the `checker_device` trust state used by scan, offline package, and offline sync flows.

Device id convention:

- Public API `{deviceId}` values are external/business checker device ids, for example `checker-device-b-02` or `dev_xxx`.
- `{deviceId}` is not the database primary key.
- The database `checker_device.id` value is internal only and must not be used or exposed as an API identifier.

Device statuses:

- `PENDING`: registered but not trusted.
- `TRUSTED`: trusted and not revoked.
- `REVOKED`: blocked.

Role convention:

- `CHECKER` can register a device and view readiness/status for their own device.
- `CHECKER` must not approve, trust, or revoke any device, including their own device.
- `ORGANIZER` can approve/trust and revoke checker devices for event operations.
- `ADMIN` can approve/trust and revoke checker devices across the platform.

Management route security convention:

- `/api/v1/management/checker/devices/**` allows `ORGANIZER` and `ADMIN`.
- Regular `CHECKER` is denied on management checker device routes.

MVP organizer scope assumption:

- The current data model does not include organizer-event or organizer-organization ownership on `checker_device`.
- MVP management API allows `ORGANIZER` and `ADMIN` to approve/revoke checker devices.
- Stricter organizer ownership and event/organization scope validation is a future hardening task.

Management routes:

- `GET /api/v1/management/checker/devices/pending`
- `PATCH /api/v1/management/checker/devices/{deviceId}/trust`
- `PATCH /api/v1/management/checker/devices/{deviceId}/revoke`

Checker-facing APIs do not allow self-trust or self-revocation.

## GET /api/v1/management/checker/devices/pending

Swagger tag: `Management Checker Devices`

Implementation status: implemented.

Purpose:

- List checker devices awaiting management approval.

Auth/role:

- `ORGANIZER` or `ADMIN`.
- `CHECKER` is not allowed.

Query semantics:

- Pending means `trusted=false` and `revokedAt=null`.
- Results should be ordered by `registeredAt` descending.

Response:

- `data` is an array of existing `CheckerDeviceResponse` objects.
- Response objects include external/business `deviceId`.
- Response objects do not include database primary key `id`.

## PATCH /api/v1/management/checker/devices/{deviceId}/trust

Swagger tag: `Management Checker Devices`

Implementation status: implemented.

Purpose:

- Mark a checker device as trusted for checker operations.

Auth/role:

- `ORGANIZER` or `ADMIN`.
- `CHECKER` is not allowed, including for their own device.

Path fields:

- `deviceId`: external/business checker device id, not the database primary key.

State transition:

- Set `trusted=true`.
- Set `trustedAt` to current server time.
- Set `revokedAt=null`.

Failure result codes:

- Unknown `deviceId` returns HTTP 404 with `DEVICE_NOT_ALLOWED`.

## PATCH /api/v1/management/checker/devices/{deviceId}/revoke

Swagger tag: `Management Checker Devices`

Implementation status: implemented.

Purpose:

- Revoke a checker device so it cannot be used for trusted checker flows.

Auth/role:

- `ORGANIZER` or `ADMIN`.
- `CHECKER` is not allowed, including for their own device.

Path fields:

- `deviceId`: external/business checker device id, not the database primary key.

State transition:

- Set `trusted=false`.
- Set `revokedAt` to current server time.

Failure result codes:

- Unknown `deviceId` returns HTTP 404 with `DEVICE_NOT_ALLOWED`.

## POST /api/v1/checker/devices/register

Swagger tag: `Checker Devices`

Purpose:

- Register a checker client installation for later review/trust.

Auth/role:

- Authenticated `CHECKER` role.
- Checker id is resolved from the current user context.

Request fields:

- `deviceName`, optional.
- `platform`, optional.
- `userAgent`, optional.
- `appVersion`, optional.

Request example:

```json
{
  "deviceName": "iPhone Gate B - 01",
  "platform": "PWA",
  "userAgent": "Mozilla/5.0...",
  "appVersion": "0.9.3"
}
```

Response fields:

- `deviceId`
- `checkerId`
- `deviceName`
- `platform`
- `appVersion`
- `status`
- `trusted`
- `revoked`
- `registeredAt`
- `trustedAt`
- `revokedAt`
- `lastSeenAt`
- `message`

Success example:

```json
{
  "status": 200,
  "message": "Checker device registered successfully",
  "data": {
    "deviceId": "dev_9d4d6c4a-...",
    "checkerId": 7001,
    "deviceName": "iPhone Gate B - 01",
    "platform": "PWA",
    "appVersion": "0.9.3",
    "status": "PENDING",
    "trusted": false,
    "revoked": false,
    "registeredAt": "2026-05-01T10:00:00Z",
    "trustedAt": null,
    "revokedAt": null,
    "lastSeenAt": "2026-05-01T10:00:00Z",
    "message": "Thiet bi da duoc ghi nhan. Vui long cho quan ly duyet."
  }
}
```

Business notes:

- Device ids are generated server-side.
- Client-provided `deviceId` is ignored for new registration.
- New devices are `PENDING` and `trusted=false`.
- This endpoint does not issue offline packages, trust/revoke a device, or perform admission logic.
- `CHECKER` cannot use this endpoint to approve their own device.

## GET /api/v1/checker/devices/{deviceId}

Swagger tag: `Checker Devices`

Purpose:

- Return current device status for a device owned by the authenticated checker.

Auth/role:

- Authenticated `CHECKER` role.
- Device must belong to the authenticated checker if it already exists.

Path fields:

- `deviceId`: external/business checker device id, not the database primary key.

Response fields:

- `deviceId`
- `checkerId`
- `deviceName`
- `platform`
- `appVersion`
- `status`
- `trusted`
- `revoked`
- `registeredAt`
- `trustedAt`
- `revokedAt`
- `lastSeenAt`

Success example:

```json
{
  "status": 200,
  "message": "Fetched checker device successfully",
  "data": {
    "deviceId": "device-abc",
    "checkerId": 7001,
    "deviceName": "Gate phone",
    "platform": "WEB",
    "appVersion": "0.9.3",
    "status": "TRUSTED",
    "trusted": true,
    "revoked": false,
    "registeredAt": "2026-05-01T09:00:00Z",
    "trustedAt": "2026-05-01T09:30:00Z",
    "revokedAt": null,
    "lastSeenAt": "2026-05-01T09:59:00Z",
    "message": "Device is trusted."
  }
}
```

Failure example:

```json
{
  "status": 403,
  "message": "Device is registered to another checker",
  "data": {
    "resultCode": "DEVICE_NOT_ALLOWED"
  }
}
```

Result codes:

- `DEVICE_NOT_ALLOWED`

## GET /api/v1/checker/devices/{deviceId}/readiness

Swagger tag: `Checker Devices`

Purpose:

- Compatibility readiness endpoint for checker UI status polling.

Business notes:

- Returns registered/trusted/revoked facts only.
- Does not trust/revoke the device.
- Another checker's device returns `DEVICE_NOT_ALLOWED`.

Business notes:

- Backend readiness only reports server-known facts: registered, trusted, revoked, checker id, device id, and server time.
- Camera permission, network strength, browser capability, and local clock UI checks remain frontend responsibilities.
- This endpoint does not issue offline packages and does not perform admission logic.

## POST /api/v1/checker/scan

Swagger tag: `Checker Scan`

Implementation status: implemented in Task 7.

Purpose:

- Validate a QR token online and perform final check-in.

Auth/role:

- Authenticated checker role.
- Checker must be assigned to the event/showtime/gate context.

Request fields:

- `qrToken`: scanned QR token envelope.
- `eventId`: checker selected event.
- `showtimeId`: checker selected showtime.
- `gateId`: optional gate identifier.
- `deviceId`: optional checker device id.
- `scannedAt`: client scan time.

Device id convention:

- `X-Device-Id` header is supported.
- Body `deviceId` is also supported for existing clients.
- If both are present, they must match or the API returns `DEVICE_MISMATCH`.

Request example:

```json
{
  "qrToken": "header.payload.signature",
  "eventId": 99,
  "showtimeId": 501,
  "gateId": "A1",
  "deviceId": "device-abc",
  "scannedAt": "2026-05-01T10:00:08Z"
}
```

Response fields:

- `resultCode`
- `ticketAssetId`
- `ticketCode`
- `eventId`
- `showtimeId`
- `gateId`
- `checkedInAt`
- `checkerId`
- `message`
- optional `ticketSummary`

Success example:

```json
{
  "status": 200,
  "message": "Ticket checked in successfully",
  "data": {
    "resultCode": "VALID_CHECKED_IN",
    "ticketAssetId": 12345,
    "ticketCode": "TCK-001",
    "eventId": 99,
    "showtimeId": 501,
    "gateId": "A1",
    "checkedInAt": "2026-05-01T10:00:09Z",
    "checkerId": 7001
  }
}
```

Failure example:

```json
{
  "status": 200,
  "message": "Ticket already used",
  "data": {
    "resultCode": "ALREADY_USED",
    "ticketAssetId": 12345,
    "ticketCode": "TCK-001",
    "firstCheckedInAt": "2026-05-01T09:58:40Z",
    "firstGateId": "A1"
  }
}
```

Result codes:

- `VALID_CHECKED_IN`
- `ALREADY_USED`
- `QR_EXPIRED`
- `INVALID_QR`
- `INVALID_SIGNATURE`
- `INVALID_QR_VERSION`
- `WRONG_EVENT`
- `WRONG_SHOWTIME`
- `WRONG_GATE`
- `LOCKED_RESALE`
- `CANCELLED`
- `UNAUTHORIZED_CHECKER`
- `TICKET_NOT_FOUND`
- `DEVICE_TIME_INVALID`
- `DEVICE_NOT_ALLOWED`
- `DEVICE_NOT_TRUSTED`
- `DEVICE_REVOKED`
- `DEVICE_MISMATCH`

Business notes:

- Online success is final.
- Successful online scan atomically marks `ticket_access_state.accessStatus` from `VALID` to `USED`.
- A second scan must not succeed.
- If gate policy is absent or empty, gate validation is skipped/defaulted.
- If gate policy exists and `gateId` is not allowed, return `WRONG_GATE`.
- `CheckInLog` stores the actual `gateId` used during scan.
- Check-in success uses server time for `checkedInAt`, `usedAt`, and the atomic update timestamp.
- If `deviceId` is provided, it must reference a registered, trusted, non-revoked device owned by the current checker.
- Missing `deviceId` is allowed by default for online scan while `checkin.checker.device.required-for-online-scan=false`; enabling that config requires a valid trusted device.
- Client `scannedAt` is logged only if within `checkin.checker.clock-skew-seconds`, default 300 seconds. Outside that window returns `DEVICE_TIME_INVALID`.
- QR `issuedAt` too far in the future is rejected as `INVALID_QR`.
- Malformed gate policies fail closed and do not become unrestricted access.
- The raw QR token is never stored; signed expired tokens are logged by QR `jti` when the verifier can identify the payload.
- Totally invalid or untrusted QR tokens are returned as `INVALID_QR` or `INVALID_SIGNATURE`; they are not logged because the current `check_in_log.ticket_asset_id` model is non-null and the payload is not trusted.

## POST /api/v1/checker/offline-packages

Swagger tag: `Offline Package`

Implementation status: implemented in Task 8.

Purpose:

- Generate a scoped offline package for a checker device.

Auth/role:

- Authenticated checker role.
- Checker must be assigned to requested event/showtime/gate.

Request fields:

- `eventId`
- `showtimeId`
- `gateId`, optional
- `deviceId`
- optional `requestedAt`
- optional `requestedValidityMinutes`

Request example:

```json
{
  "eventId": 99,
  "showtimeId": 501,
  "gateId": "A1",
  "deviceId": "device-abc",
  "requestedAt": "2026-05-01T08:00:00Z",
  "requestedValidityMinutes": 240
}
```

Response fields:

- `packageId`
- `eventId`
- `showtimeId`
- `gateId`
- `checkerId`
- `deviceId`
- `issuedAt`
- `validUntil`
- `keyId`
- `publicVerificationKey`
- `keyVersion`
- `keyAlgorithm`
- `snapshotCount`
- `ticketSnapshots[]`
- `checksum`
- `packageSignature`

Success example:

```json
{
  "status": 201,
  "message": "Offline package created successfully",
  "data": {
    "packageId": "pkg-20260501-001",
    "eventId": 99,
    "showtimeId": 501,
    "gateId": "A1",
    "checkerId": 7001,
    "deviceId": "device-abc",
    "issuedAt": "2026-05-01T08:00:00Z",
    "validUntil": "2026-05-01T12:00:00Z",
    "keyId": "qr-key-2026-05",
    "publicVerificationKey": "base64-subject-public-key-info",
    "keyVersion": "qr-key-2026-05",
    "keyAlgorithm": "EC",
    "snapshotCount": 1,
    "ticketSnapshots": [
      {
        "ticketAssetId": 12345,
        "ticketCode": "TCK-001",
        "eventId": 99,
        "showtimeId": 501,
        "ticketTypeName": "VIP",
        "zoneLabel": "Zone A",
        "seatLabel": "A-12",
        "qrVersion": 3,
        "accessStatus": "VALID",
        "usedAt": null,
        "usedAtGateId": null,
        "allowedGateIds": ["A1"]
      }
    ],
    "checksum": "sha256:...",
    "packageSignature": null
  }
}
```

Failure example:

```json
{
  "status": 403,
  "message": "Checker is not authorized for this gate",
  "data": {
    "resultCode": "UNAUTHORIZED_CHECKER"
  }
}
```

Result codes:

- `UNAUTHORIZED_CHECKER`
- `WRONG_EVENT`
- `WRONG_SHOWTIME`
- `WRONG_GATE`
- `OFFLINE_PACKAGE_TOO_LARGE`
- `DEVICE_NOT_ALLOWED`
- `DEVICE_NOT_TRUSTED`
- `DEVICE_REVOKED`
- `DEVICE_MISMATCH`

Business notes:

- Offline package is for checker devices only.
- Offline package generation requires a registered, trusted, non-revoked device owned by the current checker. Unknown or pending devices are rejected.
- Package must not contain private signing key, raw QR secret, JWT secret, full sensitive PII, full email, or full phone.
- Offline package does not mark tickets used on the server.
- Server time is the source of truth for `issuedAt`; `requestedAt` is accepted only as request metadata.
- Default package TTL is `checkin.offline-package.ttl-minutes` and currently defaults to 360 minutes. `requestedValidityMinutes` may shorten the package but cannot exceed the configured TTL.
- The configured max snapshot count is `checkin.offline-package.max-ticket-snapshots`, defaulting to 5000. Exceeding it returns `OFFLINE_PACKAGE_TOO_LARGE`.
- The package includes all ticket access statuses for the requested event/showtime so local validation can reject `USED`, `LOCKED_RESALE`, and `CANCELLED` offline.
- MVP packages are not strictly gate-filtered. They include each ticket's sanitized `allowedGateIds` so frontend local validation can return `WRONG_GATE`.
- Raw internal `gatePolicySnapshot` is not returned in package responses.
- Ticket snapshots include `usedAtGateId` for already-used tickets.
- `checksum` is SHA-256 over deterministic package metadata plus ticket snapshots, excluding `checksum` and `packageSignature`.
- `checksum` is an integrity checksum, not a tamper-proof signature.
- `publicVerificationKey` is the Base64-encoded public QR verification key for the current `keyId`. Private key material is never exposed.
- `packageSignature` is currently null because a dedicated package-signing key infrastructure is not implemented yet.
- The full ticket snapshot body is returned to the checker device but only package metadata is persisted in `offline_package`.

## POST /api/v1/checker/offline-sync

Swagger tag: `Offline Sync`

Implementation status: implemented in Task 9.

Purpose:

- Sync provisional offline scan results and classify each item.

Auth/role:

- Authenticated checker role.
- Checker/device/package must match server records.

Request fields:

- `packageId`
- `eventId`
- `showtimeId`
- `gateId`, optional batch gate.
- `deviceId`
- `syncedAt`, optional client timestamp; server sync time is used as the persisted sync time.
- `items[]`
- `localScanId`
- `qrToken`
- `ticketAssetId`, optional local cache hint used only when the QR cannot be trusted.
- `qrTokenId`, optional local cache hint used only when the QR cannot be trusted.
- `eventId`, optional item override; if present and different from the batch context it is rejected.
- `showtimeId`, optional item override; if present and different from the batch context it is rejected.
- `gateId`, optional item override. Item gate wins over batch gate for deterministic processing.
- `deviceId`, optional item override. Item device wins over batch device for local context, but a mismatch with the package device fails the item.
- `localResult` or `localResultCode`, usually `OFFLINE_ACCEPTED_PENDING_SYNC`.
- `scannedAt`

Request example:

```json
{
  "packageId": "pkg-20260501-001",
  "eventId": 99,
  "showtimeId": 501,
  "gateId": "A1",
  "deviceId": "device-abc",
  "syncedAt": "2026-05-01T20:05:00Z",
  "items": [
    {
      "localScanId": "local-1",
      "qrToken": "header.payload.signature",
      "ticketAssetId": 12345,
      "qrTokenId": "jti-12345",
      "localResult": "OFFLINE_ACCEPTED_PENDING_SYNC",
      "scannedAt": "2026-05-01T09:10:00Z",
      "gateId": "A1",
      "deviceId": "device-abc"
    }
  ]
}
```

Response fields:

- `packageId`
- `eventId`
- `showtimeId`
- `gateId`
- `deviceId`
- `syncedAt`
- `summary`
- `summary.total`
- `summary.accepted`
- `summary.rejected`
- `summary.failed`
- `summary.conflict`
- `acceptedCount`, `rejectedCount`, `failedCount`, and `conflictCount` are also returned for backward-compatible consumers.
- `items[]`
- `localScanId`
- `ticketAssetId`
- `ticketCode`
- `syncStatus` and `syncResult`
- `resultCode`, one of `SYNC_ACCEPTED`, `SYNC_REJECTED`, `SYNC_FAILED`, or `SYNC_CONFLICT`
- `scanResultCode`, the server-side business code such as `VALID_CHECKED_IN`, `ALREADY_USED`, `WRONG_GATE`, or `INVALID_SIGNATURE`
- `title`, `message`, and `resultMessage`
- `local.resultCode`, `local.scannedAt`, `local.gateId`, `local.deviceId`
- `server.resultCode`, with used-ticket fields when available
- optional `conflictDetails`

Success example:

```json
{
  "status": 200,
  "message": "Offline sync processed",
  "data": {
    "packageId": "pkg-20260501-001",
    "eventId": 99,
    "showtimeId": 501,
    "gateId": "A1",
    "deviceId": "device-abc",
    "syncedAt": "2026-05-01T20:05:00Z",
    "summary": {
      "total": 1,
      "accepted": 1,
      "rejected": 0,
      "failed": 0,
      "conflict": 0
    },
    "acceptedCount": 1,
    "rejectedCount": 0,
    "failedCount": 0,
    "conflictCount": 0,
    "items": [
      {
        "localScanId": "local-1",
        "ticketAssetId": 12345,
        "ticketCode": "TCK-001",
        "syncStatus": "SYNC_ACCEPTED",
        "syncResult": "SYNC_ACCEPTED",
        "resultCode": "SYNC_ACCEPTED",
        "scanResultCode": "VALID_CHECKED_IN",
        "local": {
          "resultCode": "OFFLINE_ACCEPTED_PENDING_SYNC",
          "scannedAt": "2026-05-01T09:10:00Z",
          "gateId": "A1",
          "deviceId": "device-abc"
        },
        "server": {
          "resultCode": "VALID_CHECKED_IN"
        }
      }
    ]
  }
}
```

Conflict example:

```json
{
  "status": 200,
  "message": "Offline sync processed with conflicts",
  "data": {
    "packageId": "pkg-20260501-001",
    "eventId": 99,
    "showtimeId": 501,
    "gateId": "A1",
    "deviceId": "device-abc",
    "syncedAt": "2026-05-01T20:05:00Z",
    "summary": {
      "total": 1,
      "accepted": 0,
      "rejected": 0,
      "failed": 0,
      "conflict": 1
    },
    "acceptedCount": 0,
    "rejectedCount": 0,
    "failedCount": 0,
    "conflictCount": 1,
    "items": [
      {
        "localScanId": "local-1",
        "ticketAssetId": 12345,
        "ticketCode": "TCK-001",
        "syncStatus": "SYNC_CONFLICT",
        "syncResult": "SYNC_CONFLICT",
        "resultCode": "SYNC_CONFLICT",
        "scanResultCode": "ALREADY_USED",
        "local": {
          "resultCode": "OFFLINE_ACCEPTED_PENDING_SYNC",
          "scannedAt": "2026-05-01T09:10:00Z",
          "gateId": "A1",
          "deviceId": "device-abc"
        },
        "server": {
          "resultCode": "ALREADY_USED",
          "usedAt": "2026-05-01T09:05:00Z",
          "usedAtGateId": "A2",
          "usedByCheckerId": 7002,
          "usedByDeviceId": "device-other"
        },
        "conflictDetails": {
          "localResult": "OFFLINE_ACCEPTED_PENDING_SYNC",
          "serverResult": "ALREADY_USED",
          "localScannedAt": "2026-05-01T09:10:00Z",
          "currentGateId": "A1",
          "firstSuccessfulCheckInAt": "2026-05-01T09:05:00Z",
          "firstSuccessfulGateId": "A2"
        }
      }
    ]
  }
}
```

Result codes:

- `SYNC_ACCEPTED`
- `SYNC_REJECTED`
- `SYNC_FAILED`
- `SYNC_CONFLICT`
- plus scan result codes such as `QR_EXPIRED`, `INVALID_SIGNATURE`, `WRONG_GATE`, `LOCKED_RESALE`, `CANCELLED`, `ALREADY_USED`

Business notes:

- A syntactically valid batch returns HTTP 200 with per-item results. Transport errors are reserved for malformed batch shape, unauthenticated access, checker/device/package denial, package not found, expired package, or unexpected system errors.
- `SYNC_ACCEPTED` means the server accepted the offline scan and atomically marked `ticket_access_state` from `VALID` to `USED`.
- `SYNC_REJECTED` means retry will not solve the business denial. Current implementation uses it for `QR_EXPIRED`, `INVALID_SIGNATURE`, `WRONG_EVENT`, `WRONG_SHOWTIME`, `WRONG_GATE`, `INVALID_QR_VERSION`, `LOCKED_RESALE`, `CANCELLED`, and trusted `TICKET_NOT_FOUND`.
- `SYNC_FAILED` means the item is malformed or cannot be safely processed, such as missing `localScanId`, missing `qrToken`, missing `scannedAt`, invalid QR format, device mismatch, or unexpected per-item technical failure.
- `SYNC_CONFLICT` means the offline device locally accepted the scan but the server state is already `USED`, including when the conditional update loses a race. MVP does not allow checker override.
- QR expiration must be checked against item `scannedAt`.
- The endpoint reuses the same conditional `markUsedIfValid(...)` atomic update strategy as online scan.
- Idempotency uses the `offline_sync_item` unique key `(packageId, localScanId)`. A retry of an already persisted item returns the stored sync result and does not create another check-in log.
- Each persisted sync item writes `offline_sync_item` with local id, package id, ticket id, QR `jti`, checker/device/context, sync result, server result, timestamps, and conflict metadata where applicable.
- `CheckInLog` is written for sync items that have enough trusted or locally supplied identifiers. It stores `scanMode=OFFLINE_SYNC`, `scanResult=SYNC_ACCEPTED/SYNC_REJECTED/SYNC_FAILED/SYNC_CONFLICT`, original `scannedAt`, server `syncedAt`, QR `jti`, failure reason, and conflict status. Raw QR tokens are not stored.

## GET /api/v1/checker/tickets/{ticketAssetId}/owner-info

Swagger tag: `Checker Support`

Implementation status: implemented in Task 10.

Purpose:

- Provide support/checker diagnostic owner and ticket context without enabling manual admission.

Auth/role:

- Authenticated checker assigned to the event/showtime or support/supervisor role.
- Current MVP implementation uses `CurrentUserProvider` and permits checker access when the checker has an active assignment for the ticket `eventId` and `showtimeId`. Gate-specific assignment is not required for owner-info because the response is read-only support context.
- Broader support/supervisor role handling is deferred until IAM role integration is finalized.

Path fields:

- `ticketAssetId`

Response fields:

- `supportOnly`, always `true`
- `canOverride`, always `false`
- `allowedActions`, currently `CALL_SUPPORT`, `DIRECT_TO_ALLOWED_GATE`, and/or `BACK_TO_SCAN`
- `ticket`
- `ticket.ticketAssetId`
- `ticket.ticketCode`
- `ticket.eventId`
- `ticket.showtimeId`
- `ticket.ticketTypeName`
- `ticket.zoneLabel`
- `ticket.seatLabel`
- `ticket.accessStatus`
- `ticket.qrVersion`
- `ticket.allowedGateIds`
- `currentOwner`
- `currentOwner.ownerId`, masked owner reference such as `usr_****1234`
- `currentOwner.displayName`, masked when profile data is available
- `currentOwner.maskedEmail`, masked when profile data is available
- `currentOwner.maskedPhone`, masked when profile data is available
- `currentOwner.effectiveFrom`, best-effort projection update timestamp
- `latestSuccessfulCheckIn`
- `latestSuccessfulCheckIn.usedAt`
- `latestSuccessfulCheckIn.usedAtGateId`
- `latestSuccessfulCheckIn.usedByCheckerId`
- `latestSuccessfulCheckIn.deviceId`
- `recentScanAttempts[]`, last 10 logs sorted by `scannedAt` descending
- `recentScanAttempts.scanMode`
- `recentScanAttempts.scanResult`
- `recentScanAttempts.scannedAt`
- `recentScanAttempts.syncedAt`
- `recentScanAttempts.gateId`
- `recentScanAttempts.checkerId`
- `recentScanAttempts.deviceId`
- `recentScanAttempts.failureReason`
- `recentScanAttempts.conflictStatus`
- `offlineSyncContexts[]`, latest offline sync rows when available
- `supportContext.reason`
- `supportContext.message`
- `supportContext.recommendedAction`
- Backward-compatible flat fields are also returned: `ticketAssetId`, `ticketCode`, `eventId`, `showtimeId`, `ticketTypeName`, `zoneLabel`, `seatLabel`, `accessStatus`, `maskedOwnerName`, `maskedOwnerEmail`, `maskedOwnerPhone`, `lastScanResult`, `usedAt`, and `usedAtGateId`.

Success example:

```json
{
  "status": 200,
  "message": "Fetched ticket owner info successfully",
  "data": {
    "supportOnly": true,
    "canOverride": false,
    "allowedActions": ["CALL_SUPPORT", "BACK_TO_SCAN"],
    "ticket": {
      "ticketAssetId": 12345,
      "ticketCode": "TCK-001",
      "eventId": 99,
      "showtimeId": 501,
      "ticketTypeName": "VIP",
      "zoneLabel": "Zone A",
      "seatLabel": "A-12",
      "accessStatus": "USED",
      "qrVersion": 3,
      "allowedGateIds": ["A1", "A2"]
    },
    "currentOwner": {
      "ownerId": "usr_****0010",
      "displayName": null,
      "maskedEmail": null,
      "maskedPhone": null,
      "effectiveFrom": "2026-05-01T18:57:00Z"
    },
    "latestSuccessfulCheckIn": {
      "usedAt": "2026-05-01T09:05:00Z",
      "usedAtGateId": "A2",
      "usedByCheckerId": 7002,
      "deviceId": "device-a"
    },
    "recentScanAttempts": [
      {
        "scanResult": "ALREADY_USED",
        "scanMode": "ONLINE",
        "scannedAt": "2026-05-01T09:06:00Z",
        "gateId": "A1",
        "checkerId": 7001,
        "deviceId": "device-b",
        "failureReason": "TICKET_STATE_INVALID",
        "conflictStatus": "NONE"
      }
    ],
    "supportContext": {
      "reason": "ALREADY_USED",
      "message": "Ve da duoc ghi nhan check-in truoc do.",
      "recommendedAction": "CALL_SUPPORT"
    }
  }
}
```

Failure example:

```json
{
  "status": 403,
  "message": "Checker is not authorized",
  "data": {
    "resultCode": "UNAUTHORIZED_CHECKER"
  }
}
```

Result codes:

- `TICKET_NOT_FOUND`
- `UNAUTHORIZED_CHECKER`

Business notes:

- PII must be masked.
- Response is for support lookup only.
- Manual lookup must not imply direct admission or override.
- The response never includes raw QR tokens, private keys, JWT/internal secrets, or payment details.
- Checkin-Service currently has no IAM/user profile client. The production implementation therefore returns a masked owner reference from `currentOwnerId`. If a future IAM adapter supplies owner display name, email, or phone, those values must be masked by the support masking utility before returning.
- Email masking keeps only a safe local-part prefix and the domain, for example `han.***@gmail.com`.
- Phone masking keeps the first two and last two digits for long phone numbers, for example `09******28`.
- Support context mapping is deterministic:
  - `SYNC_CONFLICT` log or sync item -> `SYNC_CONFLICT`, `CALL_SUPPORT`
  - `USED` ticket -> `ALREADY_USED`, `CALL_SUPPORT`
  - `LOCKED_RESALE` ticket -> `LOCKED_RESALE`, `CALL_SUPPORT`
  - `CANCELLED` ticket -> `CANCELLED`, `CALL_SUPPORT`
  - Latest `WRONG_GATE` log -> `WRONG_GATE`, `DIRECT_TO_ALLOWED_GATE`
- Optional free-form support lookup by `ticketCode`, `orderCode`, or `assetCode` is deferred as a future extension. Task 10 implements only `GET /api/v1/checker/tickets/{ticketAssetId}/owner-info`.
