package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.response.OwnerInfoResponse;
import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.OfflineSyncItem;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ConflictStatus;
import com.capstone.checkinservice.enums.FailureReason;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.OfflineSyncItemRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportLookupServiceTest {
    private static final Instant USED_AT = Instant.parse("2026-05-01T19:31:10Z");

    @Mock
    private TicketAccessStateRepository ticketAccessStateRepository;

    @Mock
    private CheckInLogRepository checkInLogRepository;

    @Mock
    private OfflineSyncItemRepository offlineSyncItemRepository;

    @Mock
    private CheckerAssignmentService checkerAssignmentService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private OwnerProfileProvider ownerProfileProvider;

    private SupportLookupService service;

    @BeforeEach
    void setUp() {
        service = new SupportLookupService(
                ticketAccessStateRepository,
                checkInLogRepository,
                offlineSyncItemRepository,
                checkerAssignmentService,
                currentUserProvider,
                ownerProfileProvider,
                new SupportMaskingService()
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        lenient().when(ownerProfileProvider.findOwnerProfile(any())).thenReturn(Optional.empty());
    }

    @Test
    void ownerInfoReturnsTicketSummaryForAssignedChecker() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        stubTicket(ticket);
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"VIP\"]")).thenReturn(Optional.of(List.of("A1", "VIP")));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getSupportOnly()).isTrue();
        assertThat(response.getCanOverride()).isFalse();
        assertThat(response.getAllowedActions()).contains("BACK_TO_SCAN");
        assertThat(response.getTicket().getTicketAssetId()).isEqualTo(12345L);
        assertThat(response.getTicket().getAllowedGateIds()).containsExactly("A1", "VIP");
        assertThat(response.getCurrentOwner().getOwnerId()).isEqualTo("usr_****0010");
        assertThat(response.getTicketAssetId()).isEqualTo(12345L);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
    }

    @Test
    void ownerInfoRejectsUnassignedChecker() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        org.mockito.Mockito.doThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                org.springframework.http.HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        )).when(checkerAssignmentService).assertCheckerAssignedToShowtime(7001L, 99L, 501L);

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.getOwnerInfo(12345L))
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.UNAUTHORIZED_CHECKER));
    }

    @Test
    void ownerInfoMasksEmailAndPhoneFromProfileProvider() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        stubTicket(ticket);
        when(ownerProfileProvider.findOwnerProfile(10L)).thenReturn(Optional.of(new OwnerProfileProvider.OwnerProfile(
                10L,
                "Tran Gia Han",
                "han.tran@gmail.com",
                "0987654328"
        )));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getCurrentOwner().getDisplayName()).isEqualTo("T*** G*** H***");
        assertThat(response.getCurrentOwner().getMaskedEmail()).isEqualTo("han.***@gmail.com");
        assertThat(response.getCurrentOwner().getMaskedPhone()).isEqualTo("09******28");
        assertThat(response.getMaskedOwnerEmail()).isEqualTo("han.***@gmail.com");
        assertThat(response.getMaskedOwnerPhone()).isEqualTo("09******28");
    }

    @Test
    void ownerInfoIncludesLatestSuccessfulCheckInForUsedTicket() {
        TicketAccessState ticket = ticket(TicketAccessStatus.USED);
        ticket.setUsedAt(USED_AT);
        ticket.setUsedAtGateId("A2");
        ticket.setUsedByCheckerId(7002L);
        stubTicket(ticket);
        when(checkInLogRepository.findTop10ByTicketAssetIdOrderByScannedAtDesc(12345L)).thenReturn(List.of(
                log(ScanResult.ALREADY_USED, ScanMode.ONLINE, USED_AT.plusSeconds(60), "device-b"),
                log(ScanResult.VALID_CHECKED_IN, ScanMode.ONLINE, USED_AT, "device-a")
        ));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getLatestSuccessfulCheckIn().getUsedAt()).isEqualTo(OffsetDateTime.ofInstant(USED_AT, ZoneOffset.UTC));
        assertThat(response.getLatestSuccessfulCheckIn().getUsedAtGateId()).isEqualTo("A2");
        assertThat(response.getLatestSuccessfulCheckIn().getUsedByCheckerId()).isEqualTo(7002L);
        assertThat(response.getLatestSuccessfulCheckIn().getDeviceId()).isEqualTo("device-a");
    }

    @Test
    void ownerInfoIncludesRecentScanAttempts() {
        TicketAccessState ticket = ticket(TicketAccessStatus.USED);
        ticket.setUsedAt(USED_AT);
        stubTicket(ticket);
        when(checkInLogRepository.findTop10ByTicketAssetIdOrderByScannedAtDesc(12345L)).thenReturn(List.of(
                log(ScanResult.ALREADY_USED, ScanMode.ONLINE, USED_AT.plusSeconds(60), "device-b")
        ));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getRecentScanAttempts()).singleElement().satisfies(attempt -> {
            assertThat(attempt.getScanResult()).isEqualTo(ScanResult.ALREADY_USED);
            assertThat(attempt.getScanMode()).isEqualTo(ScanMode.ONLINE);
            assertThat(attempt.getDeviceId()).isEqualTo("device-b");
            assertThat(attempt.getFailureReason()).isEqualTo(FailureReason.TICKET_STATE_INVALID);
        });
    }

    @Test
    void ownerInfoDoesNotExposeRawQrToken() throws Exception {
        TicketAccessState ticket = ticket(TicketAccessStatus.USED);
        ticket.setUsedAt(USED_AT);
        stubTicket(ticket);
        when(checkInLogRepository.findTop10ByTicketAssetIdOrderByScannedAtDesc(12345L)).thenReturn(List.of(
                log(ScanResult.ALREADY_USED, ScanMode.ONLINE, USED_AT.plusSeconds(60), "device-b")
        ));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);
        String json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValueAsString(response);

        assertThat(json).doesNotContain("qrToken", "rawQrToken", "signed-token", "privateKey", "jwt");
    }

    @Test
    void mapsUsedTicketToAlreadyUsedSupportContext() {
        TicketAccessState ticket = ticket(TicketAccessStatus.USED);
        ticket.setUsedAt(USED_AT);
        stubTicket(ticket);

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getSupportContext().getReason()).isEqualTo(ScanResult.ALREADY_USED);
        assertThat(response.getSupportContext().getRecommendedAction()).isEqualTo("CALL_SUPPORT");
    }

    @Test
    void mapsLockedResaleSupportContext() {
        stubTicket(ticket(TicketAccessStatus.LOCKED_RESALE));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getSupportContext().getReason()).isEqualTo(ScanResult.LOCKED_RESALE);
    }

    @Test
    void mapsCancelledSupportContext() {
        stubTicket(ticket(TicketAccessStatus.CANCELLED));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getSupportContext().getReason()).isEqualTo(ScanResult.CANCELLED);
    }

    @Test
    void mapsSyncConflictSupportContextFromOfflineSyncItem() {
        TicketAccessState ticket = ticket(TicketAccessStatus.USED);
        ticket.setUsedAt(USED_AT);
        stubTicket(ticket);
        when(offlineSyncItemRepository.findByTicketAssetIdOrderBySyncedAtDesc(12345L)).thenReturn(List.of(
                OfflineSyncItem.builder()
                        .packageId("pkg-1")
                        .localScanId("local-1")
                        .ticketAssetId(12345L)
                        .checkerId(7001L)
                        .deviceId("device-b")
                        .eventId(99L)
                        .showtimeId(501L)
                        .gateId("A1")
                        .localResultCode(ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC)
                        .syncResult(SyncResult.SYNC_CONFLICT)
                        .serverScanResult(ScanResult.ALREADY_USED)
                        .scannedAt(USED_AT.plusSeconds(10))
                        .syncedAt(USED_AT.plusSeconds(120))
                        .conflictDetails("{\"serverResult\":\"ALREADY_USED\"}")
                        .build()
        ));

        OwnerInfoResponse response = service.getOwnerInfo(12345L);

        assertThat(response.getSupportContext().getReason()).isEqualTo(ScanResult.SYNC_CONFLICT);
        assertThat(response.getOfflineSyncContexts()).singleElement().satisfies(context -> {
            assertThat(context.getSyncStatus()).isEqualTo(SyncResult.SYNC_CONFLICT);
            assertThat(context.getConflictDetails()).contains("ALREADY_USED");
        });
    }

    @Test
    void ticketNotFoundReturnsBusinessException() {
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.getOwnerInfo(12345L))
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.TICKET_NOT_FOUND));
    }

    private void stubTicket(TicketAccessState ticket) {
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkInLogRepository.findTop10ByTicketAssetIdOrderByScannedAtDesc(12345L)).thenReturn(List.of());
        when(offlineSyncItemRepository.findByTicketAssetIdOrderBySyncedAtDesc(12345L)).thenReturn(List.of());
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"VIP\"]")).thenReturn(Optional.of(List.of("A1", "VIP")));
    }

    private CheckInLog log(ScanResult result, ScanMode mode, Instant scannedAt, String deviceId) {
        return CheckInLog.builder()
                .ticketAssetId(12345L)
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .checkerId(7001L)
                .deviceId(deviceId)
                .scanMode(mode)
                .scanResult(result)
                .qrTokenId("jti-12345")
                .scannedAt(scannedAt)
                .failureReason(result == ScanResult.VALID_CHECKED_IN ? null : FailureReason.TICKET_STATE_INVALID)
                .conflictStatus(result == ScanResult.SYNC_CONFLICT ? ConflictStatus.CONFLICT : ConflictStatus.NONE)
                .build();
    }

    private TicketAccessState ticket(TicketAccessStatus status) {
        TicketAccessState ticket = TicketAccessState.builder()
                .ticketAssetId(12345L)
                .ticketCode("TCK-12345")
                .eventId(99L)
                .showtimeId(501L)
                .ticketTypeName("VIP Standing")
                .zoneLabel("Zone A")
                .seatLabel(null)
                .currentOwnerId(10L)
                .qrVersion(3)
                .accessStatus(status)
                .allowedGateIds("[\"A1\",\"VIP\"]")
                .build();
        ticket.setCreatedAt(Instant.parse("2026-05-01T18:00:00Z"));
        ticket.setUpdatedAt(Instant.parse("2026-05-01T18:57:00Z"));
        return ticket;
    }
}
