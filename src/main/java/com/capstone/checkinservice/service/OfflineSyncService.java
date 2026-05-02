package com.capstone.checkinservice.service;

import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.crypto.QrTokenVerifier;
import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.dto.common.ResultMessage;
import com.capstone.checkinservice.dto.request.OfflineSyncRequest;
import com.capstone.checkinservice.dto.response.OfflineSyncResponse;
import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.OfflinePackage;
import com.capstone.checkinservice.entity.OfflineSyncItem;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ConflictStatus;
import com.capstone.checkinservice.enums.FailureReason;
import com.capstone.checkinservice.enums.OfflinePackageStatus;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.mapper.ScanResultMessageMapper;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.OfflinePackageRepository;
import com.capstone.checkinservice.repository.OfflineSyncItemRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OfflineSyncService {
    private final TicketAccessStateRepository ticketAccessStateRepository;
    private final CheckInLogRepository checkInLogRepository;
    private final OfflinePackageRepository offlinePackageRepository;
    private final OfflineSyncItemRepository offlineSyncItemRepository;
    private final QrTokenVerifier qrTokenVerifier;
    private final CheckerAssignmentService checkerAssignmentService;
    private final CheckerDeviceValidationService checkerDeviceValidationService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public OfflineSyncResponse syncOfflineScans(OfflineSyncRequest request) {
        Long checkerId = currentUserProvider.getCurrentUserId();
        String batchGateId = normalize(request.getGateId());
        String batchDeviceId = normalize(request.getDeviceId());
        Instant syncedAt = clock.instant();

        checkerAssignmentService.assertCheckerAssigned(
                checkerId,
                request.getEventId(),
                request.getShowtimeId(),
                batchGateId
        );
        checkerDeviceValidationService.validateForOfflinePackage(checkerId, batchDeviceId);
        OfflinePackage offlinePackage = validatePackage(request, checkerId, batchDeviceId, batchGateId, syncedAt);

        List<OfflineSyncResponse.SyncItemResult> itemResults = request.getItems().stream()
                .map(item -> processItem(request, item, checkerId, offlinePackage, syncedAt))
                .toList();

        long accepted = itemResults.stream().filter(item -> item.getSyncStatus() == SyncResult.SYNC_ACCEPTED).count();
        long rejected = itemResults.stream().filter(item -> item.getSyncStatus() == SyncResult.SYNC_REJECTED).count();
        long failed = itemResults.stream().filter(item -> item.getSyncStatus() == SyncResult.SYNC_FAILED).count();
        long conflict = itemResults.stream().filter(item -> item.getSyncStatus() == SyncResult.SYNC_CONFLICT).count();

        return OfflineSyncResponse.builder()
                .packageId(request.getPackageId())
                .eventId(request.getEventId())
                .showtimeId(request.getShowtimeId())
                .gateId(batchGateId)
                .deviceId(batchDeviceId)
                .syncedAt(TimeMapper.toOffsetDateTime(syncedAt))
                .summary(OfflineSyncResponse.Summary.builder()
                        .total(itemResults.size())
                        .accepted(Math.toIntExact(accepted))
                        .rejected(Math.toIntExact(rejected))
                        .failed(Math.toIntExact(failed))
                        .conflict(Math.toIntExact(conflict))
                        .build())
                .acceptedCount(Math.toIntExact(accepted))
                .rejectedCount(Math.toIntExact(rejected))
                .failedCount(Math.toIntExact(failed))
                .conflictCount(Math.toIntExact(conflict))
                .items(itemResults)
                .build();
    }

    private OfflinePackage validatePackage(
            OfflineSyncRequest request,
            Long checkerId,
            String deviceId,
            String gateId,
            Instant syncedAt
    ) {
        OfflinePackage offlinePackage = offlinePackageRepository.findByPackageId(request.getPackageId())
                .orElseThrow(() -> new CheckinBusinessException(
                        ScanResult.OFFLINE_PACKAGE_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Offline package was not found"
                ));

        if (!Objects.equals(offlinePackage.getCheckerId(), checkerId)
                || !Objects.equals(offlinePackage.getDeviceId(), deviceId)
                || !Objects.equals(offlinePackage.getEventId(), request.getEventId())
                || !Objects.equals(offlinePackage.getShowtimeId(), request.getShowtimeId())
                || !packageGateMatches(offlinePackage.getGateId(), gateId)) {
            throw new CheckinBusinessException(
                    ScanResult.OFFLINE_PACKAGE_NOT_FOUND,
                    HttpStatus.FORBIDDEN,
                    "Offline package is not assigned to this checker, device, or scope"
            );
        }

        if (offlinePackage.getStatus() != OfflinePackageStatus.ACTIVE || syncedAt.isAfter(offlinePackage.getValidUntil())) {
            throw new CheckinBusinessException(
                    ScanResult.OFFLINE_PACKAGE_EXPIRED,
                    HttpStatus.BAD_REQUEST,
                    "Offline package is expired"
            );
        }

        return offlinePackage;
    }

    private OfflineSyncResponse.SyncItemResult processItem(
            OfflineSyncRequest request,
            OfflineSyncRequest.OfflineSyncItemRequest item,
            Long checkerId,
            OfflinePackage offlinePackage,
            Instant syncedAt
    ) {
        String localScanId = normalize(item.getLocalScanId());
        String itemGateId = normalize(item.getGateId()) == null ? normalize(request.getGateId()) : normalize(item.getGateId());
        String itemDeviceId = normalize(item.getDeviceId()) == null ? normalize(request.getDeviceId()) : normalize(item.getDeviceId());
        ScanResult localResult = item.getLocalResultCode();
        Instant scannedAt = item.getScannedAt() == null ? null : item.getScannedAt().toInstant();

        if (localScanId != null) {
            Optional<OfflineSyncItem> existing = offlineSyncItemRepository.findByPackageIdAndLocalScanId(
                    request.getPackageId(),
                    localScanId
            );
            if (existing.isPresent()) {
                return responseFromExisting(existing.get());
            }
        }

        if (localScanId == null || scannedAt == null || !hasText(item.getQrToken())) {
            return result(item, localScanId, null, null, localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_FAILED, ScanResult.INVALID_QR, null);
        }

        if (!Objects.equals(itemDeviceId, offlinePackage.getDeviceId())) {
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, item.getTicketAssetId(),
                    item.getQrTokenId(), localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_FAILED, ScanResult.DEVICE_MISMATCH, null);
        }

        Long eventId = item.getEventId() == null ? request.getEventId() : item.getEventId();
        Long showtimeId = item.getShowtimeId() == null ? request.getShowtimeId() : item.getShowtimeId();
        if (!Objects.equals(eventId, request.getEventId())) {
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, item.getTicketAssetId(),
                    item.getQrTokenId(), localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_REJECTED, ScanResult.WRONG_EVENT, null);
        }
        if (!Objects.equals(showtimeId, request.getShowtimeId())) {
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, item.getTicketAssetId(),
                    item.getQrTokenId(), localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_REJECTED, ScanResult.WRONG_SHOWTIME, null);
        }
        if (!packageGateMatches(offlinePackage.getGateId(), itemGateId)
                || !checkerAssignmentService.isCheckerAssigned(checkerId, eventId, showtimeId, itemGateId)) {
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, item.getTicketAssetId(),
                    item.getQrTokenId(), localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_REJECTED, ScanResult.WRONG_GATE, null);
        }

        QrTokenPayload payload;
        try {
            payload = qrTokenVerifier.verify(item.getQrToken(), scannedAt);
        } catch (QrTokenException exception) {
            SyncResult syncResult = syncResultForTokenFailure(exception.getResultCode());
            QrTokenPayload exceptionPayload = exception.getPayload();
            Long ticketAssetId = exceptionPayload == null ? item.getTicketAssetId() : exceptionPayload.ticketAssetId();
            String qrTokenId = exceptionPayload == null ? item.getQrTokenId() : exceptionPayload.jti();
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, ticketAssetId, qrTokenId,
                    localResult, scannedAt, itemGateId, itemDeviceId, syncResult, exception.getResultCode(), exception);
        } catch (RuntimeException exception) {
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, item.getTicketAssetId(),
                    item.getQrTokenId(), localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_FAILED, ScanResult.SYNC_FAILED, exception);
        }

        Optional<TicketAccessState> ticketCandidate = ticketAccessStateRepository.findByTicketAssetId(payload.ticketAssetId());
        if (ticketCandidate.isEmpty()) {
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, payload.ticketAssetId(),
                    payload.jti(), localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_REJECTED, ScanResult.TICKET_NOT_FOUND, null);
        }

        TicketAccessState ticket = ticketCandidate.get();
        ScanResult deniedResult = validateTicket(eventId, showtimeId, itemGateId, payload, ticket);
        if (deniedResult != null) {
            SyncResult syncResult = deniedResult == ScanResult.ALREADY_USED
                    && localResult == ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC
                    ? SyncResult.SYNC_CONFLICT
                    : SyncResult.SYNC_REJECTED;
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, payload.ticketAssetId(),
                    payload.jti(), localResult, scannedAt, itemGateId, itemDeviceId, syncResult, deniedResult, null);
        }

        int affectedRows = ticketAccessStateRepository.markUsedIfValid(
                payload.ticketAssetId(),
                payload.qrVersion(),
                scannedAt,
                checkerId,
                itemGateId
        );

        if (affectedRows == 1) {
            ticket.setAccessStatus(TicketAccessStatus.USED);
            ticket.setUsedAt(scannedAt);
            ticket.setUsedByCheckerId(checkerId);
            ticket.setUsedAtGateId(itemGateId);
            return persistAndRespond(request, item, checkerId, syncedAt, localScanId, payload.ticketAssetId(),
                    payload.jti(), localResult, scannedAt, itemGateId, itemDeviceId,
                    SyncResult.SYNC_ACCEPTED, ScanResult.VALID_CHECKED_IN, null);
        }

        TicketAccessState currentState = ticketAccessStateRepository.findByTicketAssetId(payload.ticketAssetId())
                .orElse(null);
        ScanResult currentResult = currentState == null
                ? ScanResult.TICKET_NOT_FOUND
                : resultAfterFailedAtomicUpdate(payload, currentState);
        SyncResult syncResult = currentResult == ScanResult.ALREADY_USED
                && localResult == ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC
                ? SyncResult.SYNC_CONFLICT
                : SyncResult.SYNC_REJECTED;
        return persistAndRespond(request, item, checkerId, syncedAt, localScanId, payload.ticketAssetId(),
                payload.jti(), localResult, scannedAt, itemGateId, itemDeviceId, syncResult, currentResult, null);
    }

    private ScanResult validateTicket(
            Long eventId,
            Long showtimeId,
            String gateId,
            QrTokenPayload payload,
            TicketAccessState ticket
    ) {
        if (!payload.eventId().equals(eventId) || !ticket.getEventId().equals(eventId)) {
            return ScanResult.WRONG_EVENT;
        }
        if (!payload.showtimeId().equals(showtimeId) || !ticket.getShowtimeId().equals(showtimeId)) {
            return ScanResult.WRONG_SHOWTIME;
        }
        if (!isGateAllowed(ticket, gateId)) {
            return ScanResult.WRONG_GATE;
        }
        if (!ticket.getQrVersion().equals(payload.qrVersion())) {
            return ScanResult.INVALID_QR_VERSION;
        }
        return resultForAccessStatus(ticket.getAccessStatus());
    }

    private ScanResult resultAfterFailedAtomicUpdate(QrTokenPayload payload, TicketAccessState currentState) {
        if (!currentState.getQrVersion().equals(payload.qrVersion())) {
            return ScanResult.INVALID_QR_VERSION;
        }
        ScanResult statusResult = resultForAccessStatus(currentState.getAccessStatus());
        return statusResult == null ? ScanResult.ALREADY_USED : statusResult;
    }

    private ScanResult resultForAccessStatus(TicketAccessStatus status) {
        if (status == TicketAccessStatus.VALID) {
            return null;
        }
        if (status == TicketAccessStatus.USED) {
            return ScanResult.ALREADY_USED;
        }
        if (status == TicketAccessStatus.LOCKED_RESALE) {
            return ScanResult.LOCKED_RESALE;
        }
        if (status == TicketAccessStatus.CANCELLED) {
            return ScanResult.CANCELLED;
        }
        return ScanResult.INVALID_QR;
    }

    private boolean isGateAllowed(TicketAccessState ticket, String gateId) {
        String rawGatePolicy = hasText(ticket.getAllowedGateIds())
                ? ticket.getAllowedGateIds()
                : ticket.getGatePolicySnapshot();
        Optional<List<String>> allowedGateIds = checkerAssignmentService.parseAllowedGateIds(rawGatePolicy);
        if (allowedGateIds.isEmpty()) {
            log.debug("Invalid allowedGateIds for ticket access state {}", ticket.getTicketAssetId());
            return false;
        }

        List<String> gates = allowedGateIds.get();
        if (gates.isEmpty()) {
            return true;
        }

        return gateId != null && gates.stream().anyMatch(gateId::equals);
    }

    private OfflineSyncResponse.SyncItemResult persistAndRespond(
            OfflineSyncRequest request,
            OfflineSyncRequest.OfflineSyncItemRequest item,
            Long checkerId,
            Instant syncedAt,
            String localScanId,
            Long ticketAssetId,
            String qrTokenId,
            ScanResult localResult,
            Instant scannedAt,
            String gateId,
            String deviceId,
            SyncResult syncResult,
            ScanResult serverResult,
            Exception exception
    ) {
        TicketAccessState currentState = ticketAssetId == null
                ? null
                : ticketAccessStateRepository.findByTicketAssetId(ticketAssetId).orElse(null);
        String ticketCode = currentState == null ? null : currentState.getTicketCode();
        OfflineSyncResponse.ConflictDetails conflictDetails = syncResult == SyncResult.SYNC_CONFLICT
                ? conflictDetails(ticketAssetId, localResult, scannedAt, gateId, currentState)
                : null;

        if (ticketAssetId != null && localScanId != null) {
            try {
                offlineSyncItemRepository.save(OfflineSyncItem.builder()
                        .packageId(request.getPackageId())
                        .localScanId(localScanId)
                        .ticketAssetId(ticketAssetId)
                        .qrTokenId(qrTokenId)
                        .checkerId(checkerId)
                        .deviceId(deviceId)
                        .eventId(item.getEventId() == null ? request.getEventId() : item.getEventId())
                        .showtimeId(item.getShowtimeId() == null ? request.getShowtimeId() : item.getShowtimeId())
                        .gateId(gateId)
                        .localResultCode(localResultOrDefault(localResult))
                        .syncResult(syncResult)
                        .serverScanResult(serverResult)
                        .scannedAt(scannedAt)
                        .syncedAt(syncedAt)
                        .conflictDetails(toJson(conflictDetails))
                        .failureReason(failureReason(syncResult, serverResult))
                        .build());
            } catch (DataIntegrityViolationException exceptionOnDuplicate) {
                return offlineSyncItemRepository.findByPackageIdAndLocalScanId(request.getPackageId(), localScanId)
                        .map(this::responseFromExisting)
                        .orElseThrow(() -> exceptionOnDuplicate);
            }

            writeLog(request, item, checkerId, ticketAssetId, qrTokenId, localResult, scannedAt, syncedAt,
                    gateId, deviceId, syncResult, serverResult, exception);
        }

        return result(item, localScanId, ticketAssetId, ticketCode, localResult, scannedAt, gateId, deviceId,
                syncResult, serverResult, conflictDetails);
    }

    private void writeLog(
            OfflineSyncRequest request,
            OfflineSyncRequest.OfflineSyncItemRequest item,
            Long checkerId,
            Long ticketAssetId,
            String qrTokenId,
            ScanResult localResult,
            Instant scannedAt,
            Instant syncedAt,
            String gateId,
            String deviceId,
            SyncResult syncResult,
            ScanResult serverResult,
            Exception exception
    ) {
        checkInLogRepository.save(CheckInLog.builder()
                .ticketAssetId(ticketAssetId)
                .eventId(item.getEventId() == null ? request.getEventId() : item.getEventId())
                .showtimeId(item.getShowtimeId() == null ? request.getShowtimeId() : item.getShowtimeId())
                .gateId(gateId)
                .checkerId(checkerId)
                .deviceId(deviceId)
                .scanMode(ScanMode.OFFLINE_SYNC)
                .scanResult(syncScanResult(syncResult))
                .qrTokenId(qrTokenId)
                .scannedAt(scannedAt)
                .syncedAt(syncedAt)
                .conflictStatus(syncResult == SyncResult.SYNC_CONFLICT ? ConflictStatus.CONFLICT : ConflictStatus.NONE)
                .failureReason(failureReason(syncResult, serverResult))
                .rawErrorCode(exception == null ? serverResult.name() : exception.getClass().getSimpleName())
                .build());
    }

    private OfflineSyncResponse.SyncItemResult responseFromExisting(OfflineSyncItem existing) {
        TicketAccessState currentState = ticketAccessStateRepository.findByTicketAssetId(existing.getTicketAssetId())
                .orElse(null);
        ScanResult localResult = existing.getLocalResultCode();
        SyncResult syncResult = existing.getSyncResult();
        ScanResult serverResult = existing.getServerScanResult();
        String gateId = existing.getGateId();
        String deviceId = existing.getDeviceId();
        Instant scannedAt = existing.getScannedAt();
        OfflineSyncResponse.ConflictDetails conflictDetails = syncResult == SyncResult.SYNC_CONFLICT
                ? conflictDetails(existing.getTicketAssetId(), localResult, scannedAt, gateId, currentState)
                : null;
        return result(null, existing.getLocalScanId(), existing.getTicketAssetId(),
                currentState == null ? null : currentState.getTicketCode(), localResult, scannedAt,
                gateId, deviceId, syncResult, serverResult, conflictDetails);
    }

    private OfflineSyncResponse.SyncItemResult result(
            OfflineSyncRequest.OfflineSyncItemRequest item,
            String localScanId,
            Long ticketAssetId,
            String ticketCode,
            ScanResult localResult,
            Instant scannedAt,
            String gateId,
            String deviceId,
            SyncResult syncResult,
            ScanResult serverResult,
            OfflineSyncResponse.ConflictDetails conflictDetails
    ) {
        ScanResult syncScanResult = syncScanResult(syncResult);
        ResultMessage message = ScanResultMessageMapper.toMessage(syncScanResult);
        OfflineSyncResponse.ServerContext serverContext = serverContext(serverResult, conflictDetails);

        return OfflineSyncResponse.SyncItemResult.builder()
                .localScanId(localScanId == null && item != null ? item.getLocalScanId() : localScanId)
                .ticketAssetId(ticketAssetId)
                .ticketCode(ticketCode)
                .syncStatus(syncResult)
                .syncResult(syncResult)
                .resultCode(syncScanResult)
                .scanResultCode(serverResult)
                .title(message.getTitle())
                .message(message.getMessage())
                .resultMessage(message)
                .local(OfflineSyncResponse.LocalContext.builder()
                        .resultCode(localResult)
                        .scannedAt(TimeMapper.toOffsetDateTime(scannedAt))
                        .gateId(gateId)
                        .deviceId(deviceId)
                        .build())
                .server(serverContext)
                .conflictDetails(conflictDetails)
                .build();
    }

    private OfflineSyncResponse.ServerContext serverContext(
            ScanResult serverResult,
            OfflineSyncResponse.ConflictDetails conflictDetails
    ) {
        if (conflictDetails != null) {
            return OfflineSyncResponse.ServerContext.builder()
                    .resultCode(serverResult)
                    .usedAt(conflictDetails.getFirstSuccessfulCheckInAt())
                    .usedAtGateId(conflictDetails.getFirstSuccessfulGateId())
                    .usedByCheckerId(conflictDetails.getFirstSuccessfulCheckerId())
                    .usedByDeviceId(conflictDetails.getFirstSuccessfulDeviceId())
                    .build();
        }
        return OfflineSyncResponse.ServerContext.builder()
                .resultCode(serverResult)
                .build();
    }

    private OfflineSyncResponse.ConflictDetails conflictDetails(
            Long ticketAssetId,
            ScanResult localResult,
            Instant scannedAt,
            String gateId,
            TicketAccessState currentState
    ) {
        CheckInLog firstSuccessfulLog = ticketAssetId == null ? null : firstSuccessfulLog(ticketAssetId);
        return OfflineSyncResponse.ConflictDetails.builder()
                .localResult(localResult)
                .serverResult(ScanResult.ALREADY_USED)
                .localScannedAt(TimeMapper.toOffsetDateTime(scannedAt))
                .currentGateId(gateId)
                .firstSuccessfulCheckInAt(currentState == null
                        ? null
                        : TimeMapper.toOffsetDateTime(currentState.getUsedAt()))
                .firstSuccessfulGateId(currentState == null ? null : currentState.getUsedAtGateId())
                .firstSuccessfulCheckerId(currentState == null ? null : currentState.getUsedByCheckerId())
                .firstSuccessfulDeviceId(firstSuccessfulLog == null ? null : firstSuccessfulLog.getDeviceId())
                .build();
    }

    private CheckInLog firstSuccessfulLog(Long ticketAssetId) {
        return checkInLogRepository
                .findFirstByTicketAssetIdAndScanResultOrderByScannedAtAsc(ticketAssetId, ScanResult.VALID_CHECKED_IN)
                .or(() -> checkInLogRepository.findFirstByTicketAssetIdAndScanResultOrderByScannedAtAsc(
                        ticketAssetId,
                        ScanResult.SYNC_ACCEPTED
                ))
                .orElse(null);
    }

    private SyncResult syncResultForTokenFailure(ScanResult resultCode) {
        if (resultCode == ScanResult.QR_EXPIRED || resultCode == ScanResult.INVALID_SIGNATURE) {
            return SyncResult.SYNC_REJECTED;
        }
        return SyncResult.SYNC_FAILED;
    }

    private ScanResult syncScanResult(SyncResult syncResult) {
        return switch (syncResult) {
            case SYNC_ACCEPTED -> ScanResult.SYNC_ACCEPTED;
            case SYNC_REJECTED -> ScanResult.SYNC_REJECTED;
            case SYNC_FAILED -> ScanResult.SYNC_FAILED;
            case SYNC_CONFLICT -> ScanResult.SYNC_CONFLICT;
        };
    }

    private FailureReason failureReason(SyncResult syncResult, ScanResult serverResult) {
        if (syncResult == SyncResult.SYNC_ACCEPTED) {
            return null;
        }
        return switch (serverResult) {
            case QR_EXPIRED -> FailureReason.TOKEN_EXPIRED;
            case INVALID_QR, INVALID_SIGNATURE -> FailureReason.TOKEN_INVALID;
            case WRONG_GATE -> FailureReason.GATE_POLICY_FAILED;
            case UNAUTHORIZED_CHECKER -> FailureReason.ASSIGNMENT_INVALID;
            case OFFLINE_PACKAGE_EXPIRED, OFFLINE_PACKAGE_NOT_FOUND -> FailureReason.PACKAGE_INVALID;
            case DEVICE_TIME_INVALID -> FailureReason.DEVICE_TIME_INVALID;
            case DEVICE_MISMATCH, DEVICE_NOT_ALLOWED, DEVICE_NOT_TRUSTED, DEVICE_REVOKED -> FailureReason.AUTHORIZATION_FAILED;
            case SYNC_FAILED -> FailureReason.TECHNICAL_ERROR;
            default -> FailureReason.TICKET_STATE_INVALID;
        };
    }

    private ScanResult localResultOrDefault(ScanResult localResult) {
        return localResult == null ? ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC : localResult;
    }

    private String toJson(OfflineSyncResponse.ConflictDetails conflictDetails) {
        if (conflictDetails == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(conflictDetails);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize offline sync conflict details", e);
        }
    }

    private boolean packageGateMatches(String packageGateId, String requestedGateId) {
        String normalizedPackageGateId = normalize(packageGateId);
        return normalizedPackageGateId == null || Objects.equals(normalizedPackageGateId, requestedGateId);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
