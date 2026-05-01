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
- Exact IAM role names are still open.

## POST /api/v1/checker/devices

Swagger tag: `Checker Assignments`

Purpose:

- Register or update a checker device record for later offline package and sync workflows.

Auth/role:

- Authenticated checker role.
- Checker id is resolved from the current user context.

Request fields:

- `deviceId`, required.
- `deviceName`, optional.
- `platform`, optional.

Request example:

```json
{
  "deviceId": "device-abc",
  "deviceName": "Gate phone",
  "platform": "WEB"
}
```

Response fields:

- `deviceId`
- `checkerId`
- `deviceName`
- `platform`
- `trusted`
- `revokedAt`
- `lastSeenAt`

Success example:

```json
{
  "status": 200,
  "message": "Checker device registered successfully",
  "data": {
    "deviceId": "device-abc",
    "checkerId": 7001,
    "deviceName": "Gate phone",
    "platform": "WEB",
    "trusted": true,
    "revokedAt": null,
    "lastSeenAt": "2026-05-01T10:00:00Z"
  }
}
```

Business notes:

- MVP defaults newly registered checker devices to `trusted=true` for local/demo readiness.
- Existing device records are rebound to the current checker on registration and `lastSeenAt` is updated.
- This endpoint does not issue offline packages and does not perform admission logic.

## GET /api/v1/checker/devices/{deviceId}/readiness

Swagger tag: `Checker Assignments`

Purpose:

- Return server-known checker device readiness metadata for the checker UI.

Auth/role:

- Authenticated checker role.
- Device must belong to the authenticated checker if it already exists.

Path fields:

- `deviceId`

Response fields:

- `deviceId`
- `checkerId`
- `registered`
- `trusted`
- `revoked`
- `serverTime`
- `message`

Success example:

```json
{
  "status": 200,
  "message": "Fetched checker device readiness successfully",
  "data": {
    "deviceId": "device-abc",
    "checkerId": 7001,
    "registered": true,
    "trusted": true,
    "revoked": false,
    "serverTime": "2026-05-01T10:00:00Z",
    "message": "Device is ready."
  }
}
```

Failure example:

```json
{
  "status": 403,
  "message": "Device is registered to another checker",
  "data": {
    "resultCode": "UNAUTHORIZED_CHECKER"
  }
}
```

Result codes:

- `UNAUTHORIZED_CHECKER`

Business notes:

- Backend readiness only reports server-known facts: registered, trusted, revoked, checker id, device id, and server time.
- Camera permission, network strength, browser capability, and local clock UI checks remain frontend responsibilities.
- This endpoint does not issue offline packages and does not perform admission logic.

## POST /api/v1/checker/scan

Swagger tag: `Checker Scan`

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

Business notes:

- Online success is final.
- Successful online scan atomically marks `ticket_access_state.accessStatus` from `VALID` to `USED`.
- A second scan must not succeed.
- If gate policy is absent or empty, gate validation is skipped/defaulted.
- If gate policy exists and `gateId` is not allowed, return `WRONG_GATE`.
- `CheckInLog` stores the actual `gateId` used during scan.

## POST /api/v1/checker/offline-packages

Swagger tag: `Offline Package`

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
- optional `requestedValidityMinutes`

Request example:

```json
{
  "eventId": 99,
  "showtimeId": 501,
  "gateId": "A1",
  "deviceId": "device-abc",
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
- `publicVerificationKey` or `keyVersion`
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
    "ticketSnapshots": [
      {
        "ticketAssetId": 12345,
        "ticketCode": "TCK-001",
        "ticketTypeName": "VIP",
        "zoneLabel": "Zone A",
        "seatLabel": "A-12",
        "qrVersion": 3,
        "accessStatus": "VALID",
        "usedAt": null,
        "allowedGateIds": ["A1"]
      }
    ],
    "checksum": "sha256:...",
    "packageSignature": "..."
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

Business notes:

- Offline package is for checker devices only.
- Package must not contain private signing key, raw QR secret, JWT secret, full sensitive PII, full email, or full phone.
- Offline package does not mark tickets used on the server.

## POST /api/v1/checker/offline-sync

Swagger tag: `Offline Sync`

Purpose:

- Sync provisional offline scan results and classify each item.

Auth/role:

- Authenticated checker role.
- Checker/device/package must match server records.

Request fields:

- `packageId`
- `deviceId`
- `items[]`
- `localScanId`
- `qrToken`
- `eventId`
- `showtimeId`
- `gateId`
- `localResultCode`
- `scannedAt`

Request example:

```json
{
  "packageId": "pkg-20260501-001",
  "deviceId": "device-abc",
  "items": [
    {
      "localScanId": "local-1",
      "qrToken": "header.payload.signature",
      "eventId": 99,
      "showtimeId": 501,
      "gateId": "A1",
      "localResultCode": "OFFLINE_ACCEPTED_PENDING_SYNC",
      "scannedAt": "2026-05-01T09:10:00Z"
    }
  ]
}
```

Response fields:

- `packageId`
- `summary`
- `acceptedCount`
- `rejectedCount`
- `failedCount`
- `conflictCount`
- `items[]`
- `localScanId`
- `ticketAssetId`
- `syncResult`
- `scanResultCode`
- optional `conflictDetails`

Success example:

```json
{
  "status": 200,
  "message": "Offline sync processed",
  "data": {
    "packageId": "pkg-20260501-001",
    "acceptedCount": 1,
    "rejectedCount": 0,
    "failedCount": 0,
    "conflictCount": 0,
    "items": [
      {
        "localScanId": "local-1",
        "ticketAssetId": 12345,
        "syncResult": "SYNC_ACCEPTED",
        "scanResultCode": "VALID_CHECKED_IN"
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
    "acceptedCount": 0,
    "rejectedCount": 0,
    "failedCount": 0,
    "conflictCount": 1,
    "items": [
      {
        "localScanId": "local-1",
        "ticketAssetId": 12345,
        "syncResult": "SYNC_CONFLICT",
        "scanResultCode": "ALREADY_USED",
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

- QR expiration must be checked against item `scannedAt`.
- `SYNC_CONFLICT` must be escalated; checker cannot override in MVP.
- Each sync item should write or update an `offline_sync_item` record and a `CheckInLog` entry where appropriate.

## GET /api/v1/checker/tickets/{ticketAssetId}/owner-info

Swagger tag: `Checker Support`

Purpose:

- Provide support/checker diagnostic owner and ticket context without enabling manual admission.

Auth/role:

- Authenticated checker assigned to the event/showtime or support/supervisor role.

Path fields:

- `ticketAssetId`

Response fields:

- `ticketAssetId`
- `ticketCode`
- `eventId`
- `showtimeId`
- `ticketTypeName`
- `zoneLabel`
- `seatLabel`
- `accessStatus`
- `maskedOwnerName`
- `maskedOwnerEmail`
- `maskedOwnerPhone`
- `lastScanResult`
- `usedAt`
- `usedAtGateId`

Success example:

```json
{
  "status": 200,
  "message": "Fetched ticket owner info successfully",
  "data": {
    "ticketAssetId": 12345,
    "ticketCode": "TCK-001",
    "eventId": 99,
    "showtimeId": 501,
    "ticketTypeName": "VIP",
    "zoneLabel": "Zone A",
    "seatLabel": "A-12",
    "accessStatus": "USED",
    "maskedOwnerName": "N*** V*** A",
    "maskedOwnerEmail": "n***@example.com",
    "maskedOwnerPhone": "******7890",
    "lastScanResult": "ALREADY_USED",
    "usedAt": "2026-05-01T09:05:00Z",
    "usedAtGateId": "A2"
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
