package com.capstone.checkinservice.service;

import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.crypto.QrTokenVerifier;
import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.dto.request.OfflineSyncRequest;
import com.capstone.checkinservice.dto.response.OfflineSyncResponse;
import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.OfflinePackage;
import com.capstone.checkinservice.entity.OfflineSyncItem;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ConflictStatus;
import com.capstone.checkinservice.enums.OfflinePackageStatus;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.OfflinePackageRepository;
import com.capstone.checkinservice.repository.OfflineSyncItemRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineSyncServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T20:05:00Z");
    private static final Instant SCANNED_AT = Instant.parse("2026-05-01T19:38:52Z");

    @Mock
    private TicketAccessStateRepository ticketAccessStateRepository;

    @Mock
    private CheckInLogRepository checkInLogRepository;

    @Mock
    private OfflinePackageRepository offlinePackageRepository;

    @Mock
    private OfflineSyncItemRepository offlineSyncItemRepository;

    @Mock
    private QrTokenVerifier qrTokenVerifier;

    @Mock
    private CheckerAssignmentService checkerAssignmentService;

    @Mock
    private CheckerDeviceValidationService checkerDeviceValidationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private OfflineSyncService service;

    @BeforeEach
    void setUp() {
        service = new OfflineSyncService(
                ticketAccessStateRepository,
                checkInLogRepository,
                offlinePackageRepository,
                offlineSyncItemRepository,
                qrTokenVerifier,
                checkerAssignmentService,
                checkerDeviceValidationService,
                currentUserProvider,
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        lenient().when(offlinePackageRepository.findByPackageId("pkg-1")).thenReturn(Optional.of(offlinePackage()));
        lenient().when(checkerAssignmentService.isCheckerAssigned(7001L, 99L, 501L, "A1")).thenReturn(true);
        lenient().when(offlineSyncItemRepository.findByPackageIdAndLocalScanId(anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void acceptedSyncMarksValidTicketUsedAndCreatesLog() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));
        when(ticketAccessStateRepository.markUsedIfValid(12345L, 3, SCANNED_AT, 7001L, "A1")).thenReturn(1);

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertThat(response.getSummary().getAccepted()).isEqualTo(1);
        assertThat(response.getAcceptedCount()).isEqualTo(1);
        assertThat(response.getItems()).singleElement().satisfies(result -> {
            assertThat(result.getSyncStatus()).isEqualTo(SyncResult.SYNC_ACCEPTED);
            assertThat(result.getResultCode()).isEqualTo(ScanResult.SYNC_ACCEPTED);
            assertThat(result.getScanResultCode()).isEqualTo(ScanResult.VALID_CHECKED_IN);
        });

        verify(ticketAccessStateRepository).markUsedIfValid(12345L, 3, SCANNED_AT, 7001L, "A1");
        ArgumentCaptor<CheckInLog> logCaptor = ArgumentCaptor.forClass(CheckInLog.class);
        verify(checkInLogRepository).save(logCaptor.capture());
        CheckInLog log = logCaptor.getValue();
        assertThat(log.getScanMode()).isEqualTo(ScanMode.OFFLINE_SYNC);
        assertThat(log.getScanResult()).isEqualTo(ScanResult.SYNC_ACCEPTED);
        assertThat(log.getScannedAt()).isEqualTo(SCANNED_AT);
        assertThat(log.getSyncedAt()).isEqualTo(NOW);
        assertThat(log.getQrTokenId()).isEqualTo("jti-12345");
        assertThat(log.getRawErrorCode()).isEqualTo("VALID_CHECKED_IN");
    }

    @Test
    void alreadyUsedTicketReturnsConflictWithServerContext() {
        TicketAccessState used = ticket(TicketAccessStatus.USED);
        used.setUsedAt(Instant.parse("2026-05-01T19:31:10Z"));
        used.setUsedAtGateId("A2");
        used.setUsedByCheckerId(7002L);
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(used));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));
        when(checkInLogRepository.findFirstByTicketAssetIdAndScanResultOrderByScannedAtAsc(
                12345L,
                ScanResult.VALID_CHECKED_IN
        )).thenReturn(Optional.of(CheckInLog.builder()
                .ticketAssetId(12345L)
                .eventId(99L)
                .showtimeId(501L)
                .checkerId(7002L)
                .deviceId("device-other")
                .scanMode(ScanMode.ONLINE)
                .scanResult(ScanResult.VALID_CHECKED_IN)
                .scannedAt(used.getUsedAt())
                .build()));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        OfflineSyncResponse.SyncItemResult result = response.getItems().getFirst();
        assertThat(result.getSyncStatus()).isEqualTo(SyncResult.SYNC_CONFLICT);
        assertThat(result.getServer().getUsedAt()).isEqualTo(OffsetDateTime.ofInstant(used.getUsedAt(), ZoneOffset.UTC));
        assertThat(result.getServer().getUsedAtGateId()).isEqualTo("A2");
        assertThat(result.getServer().getUsedByDeviceId()).isEqualTo("device-other");
        assertThat(result.getConflictDetails().getFirstSuccessfulGateId()).isEqualTo("A2");

        ArgumentCaptor<CheckInLog> logCaptor = ArgumentCaptor.forClass(CheckInLog.class);
        verify(checkInLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getConflictStatus()).isEqualTo(ConflictStatus.CONFLICT);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
    }

    @Test
    void cancelledTicketReturnsRejected() {
        assertRejectedForTicketStatus(TicketAccessStatus.CANCELLED, ScanResult.CANCELLED);
    }

    @Test
    void lockedResaleTicketReturnsRejected() {
        assertRejectedForTicketStatus(TicketAccessStatus.LOCKED_RESALE, ScanResult.LOCKED_RESALE);
    }

    @Test
    void wrongEventReturnsRejectedWrongEvent() {
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(new QrTokenPayload(
                12345L, "TCK-12345", 100L, 501L, 3,
                SCANNED_AT.minusSeconds(10), SCANNED_AT.plusSeconds(30), "jti-12345"
        ));
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.VALID)));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertRejected(response, ScanResult.WRONG_EVENT);
    }

    @Test
    void wrongShowtimeReturnsRejectedWrongShowtime() {
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(new QrTokenPayload(
                12345L, "TCK-12345", 99L, 999L, 3,
                SCANNED_AT.minusSeconds(10), SCANNED_AT.plusSeconds(30), "jti-12345"
        ));
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.VALID)));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertRejected(response, ScanResult.WRONG_SHOWTIME);
    }

    @Test
    void wrongGateReturnsRejectedWrongGate() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("B1")));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertRejected(response, ScanResult.WRONG_GATE);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
    }

    @Test
    void qrVersionMismatchReturnsRejectedInvalidQrVersion() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        ticket.setQrVersion(4);
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertRejected(response, ScanResult.INVALID_QR_VERSION);
    }

    @Test
    void qrExpiredAtScannedAtReturnsRejectedQrExpired() {
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT))
                .thenThrow(new QrTokenException(ScanResult.QR_EXPIRED, "expired", payload()));
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.VALID)));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertRejected(response, ScanResult.QR_EXPIRED);
        verify(qrTokenVerifier).verify("signed-token", SCANNED_AT);
    }

    @Test
    void invalidSignatureReturnsRejectedInvalidSignature() {
        OfflineSyncRequest.OfflineSyncItemRequest item = item("local-1", "signed-token");
        item.setTicketAssetId(12345L);
        item.setQrTokenId("jti-from-local");
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT))
                .thenThrow(new QrTokenException(ScanResult.INVALID_SIGNATURE, "bad signature"));
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.VALID)));

        OfflineSyncResponse response = service.syncOfflineScans(request(item));

        assertRejected(response, ScanResult.INVALID_SIGNATURE);
        verify(checkInLogRepository).save(any(CheckInLog.class));
    }

    @Test
    void malformedItemReturnsFailedWithoutMarkingUsed() {
        OfflineSyncRequest.OfflineSyncItemRequest item = item("local-1", null);

        OfflineSyncResponse response = service.syncOfflineScans(request(item));

        assertThat(response.getSummary().getFailed()).isEqualTo(1);
        assertThat(response.getItems().getFirst().getSyncStatus()).isEqualTo(SyncResult.SYNC_FAILED);
        assertThat(response.getItems().getFirst().getScanResultCode()).isEqualTo(ScanResult.INVALID_QR);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
        verify(checkInLogRepository, never()).save(any());
    }

    @Test
    void invalidQrFormatReturnsFailed() {
        when(qrTokenVerifier.verify("bad-token", SCANNED_AT))
                .thenThrow(new QrTokenException(ScanResult.INVALID_QR, "invalid format"));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "bad-token")));

        assertThat(response.getItems().getFirst().getSyncStatus()).isEqualTo(SyncResult.SYNC_FAILED);
        assertThat(response.getItems().getFirst().getScanResultCode()).isEqualTo(ScanResult.INVALID_QR);
    }

    @Test
    void failedAtomicUpdateReloadsUsedTicketAsConflict() {
        TicketAccessState initial = ticket(TicketAccessStatus.VALID);
        TicketAccessState used = ticket(TicketAccessStatus.USED);
        used.setUsedAt(SCANNED_AT.minusSeconds(30));
        used.setUsedAtGateId("A2");
        used.setUsedByCheckerId(7002L);
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L))
                .thenReturn(Optional.of(initial), Optional.of(used), Optional.of(used));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));
        when(ticketAccessStateRepository.markUsedIfValid(12345L, 3, SCANNED_AT, 7001L, "A1")).thenReturn(0);

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertThat(response.getItems().getFirst().getSyncStatus()).isEqualTo(SyncResult.SYNC_CONFLICT);
        assertThat(response.getItems().getFirst().getServer().getUsedAtGateId()).isEqualTo("A2");
    }

    @Test
    void mixedBatchReturnsCorrectSummaryCounts() {
        TicketAccessState valid = ticket(TicketAccessStatus.VALID);
        TicketAccessState cancelled = ticket(TicketAccessStatus.CANCELLED);
        cancelled.setTicketAssetId(12346L);
        when(qrTokenVerifier.verify(eq("accepted-token"), eq(SCANNED_AT))).thenReturn(payload());
        when(qrTokenVerifier.verify(eq("cancelled-token"), eq(SCANNED_AT))).thenReturn(new QrTokenPayload(
                12346L, "TCK-12346", 99L, 501L, 3,
                SCANNED_AT.minusSeconds(10), SCANNED_AT.plusSeconds(30), "jti-12346"
        ));
        when(qrTokenVerifier.verify(eq("bad-token"), eq(SCANNED_AT)))
                .thenThrow(new QrTokenException(ScanResult.INVALID_QR, "invalid"));
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(valid));
        when(ticketAccessStateRepository.findByTicketAssetId(12346L)).thenReturn(Optional.of(cancelled));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));
        when(ticketAccessStateRepository.markUsedIfValid(12345L, 3, SCANNED_AT, 7001L, "A1")).thenReturn(1);

        OfflineSyncResponse response = service.syncOfflineScans(request(
                item("local-1", "accepted-token"),
                item("local-2", "cancelled-token"),
                item("local-3", "bad-token"),
                item("local-4", null)
        ));

        assertThat(response.getSummary().getAccepted()).isEqualTo(1);
        assertThat(response.getSummary().getRejected()).isEqualTo(1);
        assertThat(response.getSummary().getFailed()).isEqualTo(2);
        assertThat(response.getSummary().getConflict()).isZero();
        assertThat(response.getSummary().getTotal()).isEqualTo(4);
    }

    @Test
    void duplicateLocalScanIdReturnsExistingResultWithoutDuplicateLog() {
        when(offlineSyncItemRepository.findByPackageIdAndLocalScanId("pkg-1", "local-1"))
                .thenReturn(Optional.of(OfflineSyncItem.builder()
                        .packageId("pkg-1")
                        .localScanId("local-1")
                        .ticketAssetId(12345L)
                        .qrTokenId("jti-12345")
                        .checkerId(7001L)
                        .deviceId("device-abc")
                        .eventId(99L)
                        .showtimeId(501L)
                        .gateId("A1")
                        .localResultCode(ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC)
                        .syncResult(SyncResult.SYNC_ACCEPTED)
                        .serverScanResult(ScanResult.VALID_CHECKED_IN)
                        .scannedAt(SCANNED_AT)
                        .syncedAt(NOW)
                        .build()));
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.USED)));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertThat(response.getItems().getFirst().getSyncStatus()).isEqualTo(SyncResult.SYNC_ACCEPTED);
        verify(qrTokenVerifier, never()).verify(anyString(), any());
        verify(checkInLogRepository, never()).save(any());
    }

    @Test
    void rawQrTokenIsNotStoredInCheckInLogOrOfflineSyncItem() {
        acceptedSyncMarksValidTicketUsedAndCreatesLog();

        ArgumentCaptor<OfflineSyncItem> syncItemCaptor = ArgumentCaptor.forClass(OfflineSyncItem.class);
        verify(offlineSyncItemRepository).save(syncItemCaptor.capture());
        assertThat(syncItemCaptor.getValue().getQrTokenId()).isEqualTo("jti-12345");
        assertThat(syncItemCaptor.getValue().getQrTokenId()).doesNotContain("signed-token");
    }

    @Test
    void unassignedCheckerCannotSyncBatch() {
        doThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        )).when(checkerAssignmentService).assertCheckerAssigned(7001L, 99L, 501L, "A1");

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.syncOfflineScans(request(item("local-1", "signed-token"))))
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.UNAUTHORIZED_CHECKER));

        verify(qrTokenVerifier, never()).verify(anyString(), any());
    }

    private void assertRejectedForTicketStatus(TicketAccessStatus status, ScanResult scanResult) {
        TicketAccessState ticket = ticket(status);
        when(qrTokenVerifier.verify("signed-token", SCANNED_AT)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));

        OfflineSyncResponse response = service.syncOfflineScans(request(item("local-1", "signed-token")));

        assertRejected(response, scanResult);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
    }

    private void assertRejected(OfflineSyncResponse response, ScanResult scanResult) {
        assertThat(response.getSummary().getRejected()).isEqualTo(1);
        assertThat(response.getItems()).singleElement().satisfies(result -> {
            assertThat(result.getSyncStatus()).isEqualTo(SyncResult.SYNC_REJECTED);
            assertThat(result.getResultCode()).isEqualTo(ScanResult.SYNC_REJECTED);
            assertThat(result.getScanResultCode()).isEqualTo(scanResult);
        });
    }

    private OfflineSyncRequest request(OfflineSyncRequest.OfflineSyncItemRequest... items) {
        return OfflineSyncRequest.builder()
                .packageId("pkg-1")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .deviceId("device-abc")
                .syncedAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))
                .items(List.of(items))
                .build();
    }

    private OfflineSyncRequest.OfflineSyncItemRequest item(String localScanId, String qrToken) {
        return OfflineSyncRequest.OfflineSyncItemRequest.builder()
                .localScanId(localScanId)
                .qrToken(qrToken)
                .ticketAssetId(null)
                .qrTokenId(null)
                .localResultCode(ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC)
                .scannedAt(OffsetDateTime.ofInstant(SCANNED_AT, ZoneOffset.UTC))
                .gateId("A1")
                .deviceId("device-abc")
                .build();
    }

    private QrTokenPayload payload() {
        return new QrTokenPayload(
                12345L,
                "TCK-12345",
                99L,
                501L,
                3,
                SCANNED_AT.minusSeconds(10),
                SCANNED_AT.plusSeconds(30),
                "jti-12345"
        );
    }

    private TicketAccessState ticket(TicketAccessStatus status) {
        return TicketAccessState.builder()
                .ticketAssetId(12345L)
                .ticketCode("TCK-12345")
                .eventId(99L)
                .showtimeId(501L)
                .ticketTypeName("VIP Standing")
                .zoneLabel("Zone A")
                .currentOwnerId(10L)
                .qrVersion(3)
                .accessStatus(status)
                .allowedGateIds("[\"A1\",\"A2\"]")
                .build();
    }

    private OfflinePackage offlinePackage() {
        return OfflinePackage.builder()
                .packageId("pkg-1")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .checkerId(7001L)
                .deviceId("device-abc")
                .issuedAt(NOW.minusSeconds(3600))
                .validUntil(NOW.plusSeconds(3600))
                .keyId("local-dev-key-v1")
                .ticketCount(1)
                .checksum("sha256:test")
                .status(OfflinePackageStatus.ACTIVE)
                .build();
    }
}
