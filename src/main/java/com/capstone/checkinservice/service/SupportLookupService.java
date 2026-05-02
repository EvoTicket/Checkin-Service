package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.response.OwnerInfoResponse;
import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.OfflineSyncItem;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.OfflineSyncItemRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SupportLookupService {
    private static final int RECENT_SYNC_CONTEXT_LIMIT = 5;

    private final TicketAccessStateRepository ticketAccessStateRepository;
    private final CheckInLogRepository checkInLogRepository;
    private final OfflineSyncItemRepository offlineSyncItemRepository;
    private final CheckerAssignmentService checkerAssignmentService;
    private final CurrentUserProvider currentUserProvider;
    private final OwnerProfileProvider ownerProfileProvider;
    private final SupportMaskingService maskingService;

    @Transactional(readOnly = true)
    public OwnerInfoResponse getOwnerInfo(Long ticketAssetId) {
        Long checkerId = currentUserProvider.getCurrentUserId();
        TicketAccessState ticket = ticketAccessStateRepository.findByTicketAssetId(ticketAssetId)
                .orElseThrow(() -> new CheckinBusinessException(
                        ScanResult.TICKET_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Ticket access state was not found"
                ));

        checkerAssignmentService.assertCheckerAssignedToShowtime(
                checkerId,
                ticket.getEventId(),
                ticket.getShowtimeId()
        );

        List<CheckInLog> recentLogs = checkInLogRepository.findTop10ByTicketAssetIdOrderByScannedAtDesc(ticketAssetId);
        List<OfflineSyncItem> syncItems = offlineSyncItemRepository.findByTicketAssetIdOrderBySyncedAtDesc(ticketAssetId);
        OwnerInfoResponse.TicketInfo ticketInfo = toTicketInfo(ticket);
        OwnerInfoResponse.CurrentOwnerInfo currentOwner = toCurrentOwner(ticket);
        OwnerInfoResponse.LatestSuccessfulCheckIn latestSuccessfulCheckIn = toLatestSuccessfulCheckIn(ticket, recentLogs);
        List<OwnerInfoResponse.RecentScanAttempt> recentScanAttempts = recentLogs.stream()
                .map(this::toRecentScanAttempt)
                .toList();
        List<OwnerInfoResponse.OfflineSyncContext> offlineSyncContexts = syncItems.stream()
                .limit(RECENT_SYNC_CONTEXT_LIMIT)
                .map(this::toOfflineSyncContext)
                .toList();
        OwnerInfoResponse.SupportContext supportContext = supportContext(ticket, recentLogs, syncItems);
        ScanResult lastScanResult = recentLogs.isEmpty() ? null : recentLogs.getFirst().getScanResult();

        return OwnerInfoResponse.builder()
                .supportOnly(true)
                .canOverride(false)
                .allowedActions(allowedActions(supportContext.getRecommendedAction()))
                .ticket(ticketInfo)
                .currentOwner(currentOwner)
                .latestSuccessfulCheckIn(latestSuccessfulCheckIn)
                .recentScanAttempts(recentScanAttempts)
                .offlineSyncContexts(offlineSyncContexts)
                .supportContext(supportContext)
                .ticketAssetId(ticket.getTicketAssetId())
                .ticketCode(ticket.getTicketCode())
                .eventId(ticket.getEventId())
                .showtimeId(ticket.getShowtimeId())
                .ticketTypeName(ticket.getTicketTypeName())
                .zoneLabel(ticket.getZoneLabel())
                .seatLabel(ticket.getSeatLabel())
                .accessStatus(ticket.getAccessStatus())
                .maskedOwnerName(currentOwner.getDisplayName())
                .maskedOwnerEmail(currentOwner.getMaskedEmail())
                .maskedOwnerPhone(currentOwner.getMaskedPhone())
                .lastScanResult(lastScanResult)
                .usedAt(ticketInfo.getAccessStatus() == TicketAccessStatus.USED
                        ? TimeMapper.toOffsetDateTime(ticket.getUsedAt())
                        : null)
                .usedAtGateId(ticket.getUsedAtGateId())
                .build();
    }

    private List<String> allowedActions(String recommendedAction) {
        if ("BACK_TO_SCAN".equals(recommendedAction)) {
            return List.of("BACK_TO_SCAN");
        }
        return List.of(recommendedAction, "BACK_TO_SCAN");
    }

    private OwnerInfoResponse.TicketInfo toTicketInfo(TicketAccessState ticket) {
        return OwnerInfoResponse.TicketInfo.builder()
                .ticketAssetId(ticket.getTicketAssetId())
                .ticketCode(ticket.getTicketCode())
                .eventId(ticket.getEventId())
                .showtimeId(ticket.getShowtimeId())
                .ticketTypeName(ticket.getTicketTypeName())
                .zoneLabel(ticket.getZoneLabel())
                .seatLabel(ticket.getSeatLabel())
                .accessStatus(ticket.getAccessStatus())
                .qrVersion(ticket.getQrVersion())
                .allowedGateIds(allowedGateIds(ticket))
                .build();
    }

    private OwnerInfoResponse.CurrentOwnerInfo toCurrentOwner(TicketAccessState ticket) {
        Optional<OwnerProfileProvider.OwnerProfile> profile = ownerProfileProvider.findOwnerProfile(ticket.getCurrentOwnerId());
        return OwnerInfoResponse.CurrentOwnerInfo.builder()
                .ownerId(maskingService.maskOwnerRef(ticket.getCurrentOwnerId()))
                .displayName(profile.map(OwnerProfileProvider.OwnerProfile::displayName)
                        .map(maskingService::maskDisplayName)
                        .orElse(null))
                .maskedEmail(profile.map(OwnerProfileProvider.OwnerProfile::email)
                        .map(maskingService::maskEmail)
                        .orElse(null))
                .maskedPhone(profile.map(OwnerProfileProvider.OwnerProfile::phone)
                        .map(maskingService::maskPhone)
                        .orElse(null))
                .effectiveFrom(TimeMapper.toOffsetDateTime(ticket.getUpdatedAt() == null
                        ? ticket.getCreatedAt()
                        : ticket.getUpdatedAt()))
                .build();
    }

    private OwnerInfoResponse.LatestSuccessfulCheckIn toLatestSuccessfulCheckIn(
            TicketAccessState ticket,
            List<CheckInLog> recentLogs
    ) {
        if (ticket.getAccessStatus() != TicketAccessStatus.USED && ticket.getUsedAt() == null) {
            return null;
        }

        CheckInLog successLog = recentLogs.stream()
                .filter(this::isSuccessfulCheckInLog)
                .findFirst()
                .orElse(null);

        return OwnerInfoResponse.LatestSuccessfulCheckIn.builder()
                .usedAt(TimeMapper.toOffsetDateTime(ticket.getUsedAt()))
                .usedAtGateId(ticket.getUsedAtGateId())
                .usedByCheckerId(ticket.getUsedByCheckerId())
                .deviceId(successLog == null ? null : successLog.getDeviceId())
                .build();
    }

    private OwnerInfoResponse.RecentScanAttempt toRecentScanAttempt(CheckInLog log) {
        return OwnerInfoResponse.RecentScanAttempt.builder()
                .scanResult(log.getScanResult())
                .scanMode(log.getScanMode())
                .scannedAt(TimeMapper.toOffsetDateTime(log.getScannedAt()))
                .syncedAt(TimeMapper.toOffsetDateTime(log.getSyncedAt()))
                .gateId(log.getGateId())
                .checkerId(log.getCheckerId())
                .deviceId(log.getDeviceId())
                .failureReason(log.getFailureReason())
                .conflictStatus(log.getConflictStatus())
                .build();
    }

    private OwnerInfoResponse.OfflineSyncContext toOfflineSyncContext(OfflineSyncItem item) {
        return OwnerInfoResponse.OfflineSyncContext.builder()
                .localScanId(item.getLocalScanId())
                .packageId(item.getPackageId())
                .syncStatus(item.getSyncResult())
                .localResultCode(item.getLocalResultCode())
                .serverScanResult(item.getServerScanResult())
                .scannedAt(TimeMapper.toOffsetDateTime(item.getScannedAt()))
                .syncedAt(TimeMapper.toOffsetDateTime(item.getSyncedAt()))
                .gateId(item.getGateId())
                .checkerId(item.getCheckerId())
                .deviceId(item.getDeviceId())
                .failureReason(item.getFailureReason())
                .conflictDetails(item.getConflictDetails())
                .build();
    }

    private OwnerInfoResponse.SupportContext supportContext(
            TicketAccessState ticket,
            List<CheckInLog> recentLogs,
            List<OfflineSyncItem> syncItems
    ) {
        if (hasSyncConflict(recentLogs, syncItems)) {
            return supportContext(
                    ScanResult.SYNC_CONFLICT,
                    "Luot quet ngoai tuyen khong khop voi trang thai may chu.",
                    "CALL_SUPPORT"
            );
        }
        if (ticket.getAccessStatus() == TicketAccessStatus.USED) {
            return supportContext(
                    ScanResult.ALREADY_USED,
                    "Ve da duoc ghi nhan check-in truoc do.",
                    "CALL_SUPPORT"
            );
        }
        if (ticket.getAccessStatus() == TicketAccessStatus.LOCKED_RESALE) {
            return supportContext(
                    ScanResult.LOCKED_RESALE,
                    "Ve dang bi khoa boi quy trinh ban lai hoac chuyen nhuong.",
                    "CALL_SUPPORT"
            );
        }
        if (ticket.getAccessStatus() == TicketAccessStatus.CANCELLED) {
            return supportContext(
                    ScanResult.CANCELLED,
                    "Ve da bi huy hoac hoan tien.",
                    "CALL_SUPPORT"
            );
        }

        ScanResult latestResult = recentLogs.isEmpty() ? null : recentLogs.getFirst().getScanResult();
        if (latestResult == ScanResult.WRONG_GATE) {
            return supportContext(
                    ScanResult.WRONG_GATE,
                    "Ve hop le nhung khong vao tai cong hien tai.",
                    "DIRECT_TO_ALLOWED_GATE"
            );
        }
        if (latestResult != null) {
            return supportContext(
                    latestResult,
                    "Can kiem tra them ngu canh quet ve.",
                    "CALL_SUPPORT"
            );
        }
        return supportContext(
                ScanResult.VALID_CHECKED_IN,
                "Chua co loi check-in nao duoc ghi nhan cho ve nay.",
                "BACK_TO_SCAN"
        );
    }

    private OwnerInfoResponse.SupportContext supportContext(
            ScanResult reason,
            String message,
            String recommendedAction
    ) {
        return OwnerInfoResponse.SupportContext.builder()
                .reason(reason)
                .message(message)
                .recommendedAction(recommendedAction)
                .build();
    }

    private boolean hasSyncConflict(List<CheckInLog> recentLogs, List<OfflineSyncItem> syncItems) {
        return recentLogs.stream().anyMatch(log -> log.getScanResult() == ScanResult.SYNC_CONFLICT)
                || syncItems.stream().anyMatch(item -> item.getSyncResult() == SyncResult.SYNC_CONFLICT);
    }

    private boolean isSuccessfulCheckInLog(CheckInLog log) {
        return log.getScanResult() == ScanResult.VALID_CHECKED_IN
                || log.getScanResult() == ScanResult.SYNC_ACCEPTED;
    }

    private List<String> allowedGateIds(TicketAccessState ticket) {
        String rawGatePolicy = hasText(ticket.getAllowedGateIds())
                ? ticket.getAllowedGateIds()
                : ticket.getGatePolicySnapshot();
        return checkerAssignmentService.parseAllowedGateIds(rawGatePolicy).orElse(List.of());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
