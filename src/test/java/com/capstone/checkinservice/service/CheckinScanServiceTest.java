package com.capstone.checkinservice.service;

import com.capstone.checkinservice.config.CheckerDeviceProperties;
import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.crypto.QrTokenVerifier;
import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.dto.request.OnlineScanRequest;
import com.capstone.checkinservice.dto.response.ScanResultResponse;
import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import com.capstone.checkinservice.exception.CheckinBusinessException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinScanServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T19:42:18Z");
    private static final OffsetDateTime SCANNED_AT = OffsetDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC);

    @Mock
    private TicketAccessStateRepository ticketAccessStateRepository;

    @Mock
    private CheckInLogRepository checkInLogRepository;

    @Mock
    private QrTokenVerifier qrTokenVerifier;

    @Mock
    private CheckerAssignmentService checkerAssignmentService;

    @Mock
    private CheckerDeviceValidationService checkerDeviceValidationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CheckinScanService service;

    @BeforeEach
    void setUp() {
        service = new CheckinScanService(
                ticketAccessStateRepository,
                checkInLogRepository,
                qrTokenVerifier,
                checkerAssignmentService,
                checkerDeviceValidationService,
                currentUserProvider,
                new CheckerDeviceProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
    }

    @Test
    void validOnlineScanMarksTicketUsedAndCreatesLog() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));
        when(ticketAccessStateRepository.markUsedIfValid(12345L, 3, NOW, 7001L, "A1")).thenReturn(1);

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.VALID_CHECKED_IN);
        assertThat(response.getCheckedInAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(response.getTicketSummary().getAccessStatus()).isEqualTo(TicketAccessStatus.USED);

        ArgumentCaptor<CheckInLog> logCaptor = ArgumentCaptor.forClass(CheckInLog.class);
        verify(checkInLogRepository).save(logCaptor.capture());
        CheckInLog log = logCaptor.getValue();
        assertThat(log.getTicketAssetId()).isEqualTo(12345L);
        assertThat(log.getScanMode()).isEqualTo(ScanMode.ONLINE);
        assertThat(log.getScanResult()).isEqualTo(ScanResult.VALID_CHECKED_IN);
        assertThat(log.getQrTokenId()).isEqualTo("jti-12345");
        assertThat(log.getDeviceId()).isEqualTo("device-abc");
    }

    @Test
    void secondScanReturnsAlreadyUsed() {
        TicketAccessState ticket = ticket(TicketAccessStatus.USED);
        ticket.setUsedAt(NOW.minusSeconds(60));
        ticket.setUsedAtGateId("A1");
        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.ALREADY_USED);
        assertThat(response.getFirstCheckedInAt()).isEqualTo(OffsetDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC));
        assertThat(response.getFirstGateId()).isEqualTo("A1");
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
    }

    @Test
    void expiredQrReturnsQrExpiredAndDoesNotMarkUsed() {
        QrTokenException exception = new QrTokenException(ScanResult.QR_EXPIRED, "expired", payload());
        when(qrTokenVerifier.verify("signed-token", NOW)).thenThrow(exception);

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.QR_EXPIRED);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
        verify(checkInLogRepository).save(any(CheckInLog.class));
    }

    @Test
    void invalidSignatureReturnsInvalidSignatureAndDoesNotLogRawUntrustedPayload() {
        when(qrTokenVerifier.verify("signed-token", NOW))
                .thenThrow(new QrTokenException(ScanResult.INVALID_SIGNATURE, "bad signature"));

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.INVALID_SIGNATURE);
        verify(ticketAccessStateRepository, never()).findByTicketAssetId(any());
        verify(checkInLogRepository, never()).save(any());
    }

    @Test
    void wrongEventReturnsWrongEvent() {
        assertDenied(ticket(TicketAccessStatus.VALID), new QrTokenPayload(
                12345L, "TCK-12345", 100L, 501L, 3,
                NOW.minusSeconds(10), NOW.plusSeconds(30), "jti-12345"
        ), ScanResult.WRONG_EVENT);
    }

    @Test
    void wrongShowtimeReturnsWrongShowtime() {
        assertDenied(ticket(TicketAccessStatus.VALID), new QrTokenPayload(
                12345L, "TCK-12345", 99L, 999L, 3,
                NOW.minusSeconds(10), NOW.plusSeconds(30), "jti-12345"
        ), ScanResult.WRONG_SHOWTIME);
    }

    @Test
    void wrongGateReturnsWrongGateWhenPolicyExists() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("B1")));

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.WRONG_GATE);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
    }

    @Test
    void qrVersionMismatchReturnsInvalidQrVersion() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        ticket.setQrVersion(4);
        assertDenied(ticket, payload(), ScanResult.INVALID_QR_VERSION);
    }

    @Test
    void lockedResaleReturnsLockedResale() {
        assertDenied(ticket(TicketAccessStatus.LOCKED_RESALE), payload(), ScanResult.LOCKED_RESALE);
    }

    @Test
    void cancelledReturnsCancelled() {
        assertDenied(ticket(TicketAccessStatus.CANCELLED), payload(), ScanResult.CANCELLED);
    }

    @Test
    void ticketNotFoundReturnsTicketNotFound() {
        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.empty());

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.TICKET_NOT_FOUND);
        verify(checkInLogRepository).save(any(CheckInLog.class));
    }

    @Test
    void unauthorizedCheckerUsesExistingForbiddenConvention() {
        doThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        )).when(checkerAssignmentService).assertCheckerAssigned(7001L, 99L, 501L, "A1");

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.scanOnline(request()))
                .satisfies(exception -> {
                    assertThat(exception.getResultCode()).isEqualTo(ScanResult.UNAUTHORIZED_CHECKER);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });

        verify(qrTokenVerifier, never()).verify(any(), any());
    }

    @Test
    void onlineScanWithRevokedDeviceIsRejected() {
        doThrow(new CheckinBusinessException(
                ScanResult.DEVICE_REVOKED,
                HttpStatus.FORBIDDEN,
                "Device revoked"
        )).when(checkerDeviceValidationService).validateForOnlineScan(7001L, "device-abc");

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.scanOnline(request()))
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.DEVICE_REVOKED));

        verify(qrTokenVerifier, never()).verify(any(), any());
    }

    @Test
    void onlineScanWithUntrustedDeviceIsRejected() {
        doThrow(new CheckinBusinessException(
                ScanResult.DEVICE_NOT_TRUSTED,
                HttpStatus.FORBIDDEN,
                "Device pending"
        )).when(checkerDeviceValidationService).validateForOnlineScan(7001L, "device-abc");

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.scanOnline(request()))
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.DEVICE_NOT_TRUSTED));
    }

    @Test
    void onlineScanWithScannedAtTooFarInFutureReturnsDeviceTimeInvalid() {
        OnlineScanRequest request = request();
        request.setScannedAt(OffsetDateTime.ofInstant(NOW.plusSeconds(301), ZoneOffset.UTC));

        ScanResultResponse response = service.scanOnline(request);

        assertThat(response.getResultCode()).isEqualTo(ScanResult.DEVICE_TIME_INVALID);
        verify(qrTokenVerifier, never()).verify(any(), any());
    }

    @Test
    void onlineScanWithScannedAtTooFarInPastReturnsDeviceTimeInvalid() {
        OnlineScanRequest request = request();
        request.setScannedAt(OffsetDateTime.ofInstant(NOW.minusSeconds(301), ZoneOffset.UTC));

        ScanResultResponse response = service.scanOnline(request);

        assertThat(response.getResultCode()).isEqualTo(ScanResult.DEVICE_TIME_INVALID);
        verify(qrTokenVerifier, never()).verify(any(), any());
    }

    @Test
    void failedAtomicUpdateReloadsAndReturnsAlreadyUsed() {
        TicketAccessState initial = ticket(TicketAccessStatus.VALID);
        TicketAccessState used = ticket(TicketAccessStatus.USED);
        used.setUsedAt(NOW.minusSeconds(1));
        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L))
                .thenReturn(Optional.of(initial), Optional.of(used));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));
        when(ticketAccessStateRepository.markUsedIfValid(12345L, 3, NOW, 7001L, "A1")).thenReturn(0);

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.ALREADY_USED);
    }

    @Test
    void concurrentDoubleScanAllowsOnlyOneSuccess() throws Exception {
        AtomicInteger successfulUpdates = new AtomicInteger();
        TicketAccessState valid = ticket(TicketAccessStatus.VALID);
        TicketAccessState used = ticket(TicketAccessStatus.USED);
        used.setUsedAt(NOW);

        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L))
                .thenAnswer(invocation -> successfulUpdates.get() == 0 ? Optional.of(valid) : Optional.of(used));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\",\"A2\"]")).thenReturn(Optional.of(List.of("A1", "A2")));
        when(ticketAccessStateRepository.markUsedIfValid(eq(12345L), eq(3), eq(NOW), eq(7001L), eq("A1")))
                .thenAnswer(invocation -> successfulUpdates.getAndIncrement() == 0 ? 1 : 0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<ScanResultResponse> first = executor.submit(() -> scanAfter(start));
        Future<ScanResultResponse> second = executor.submit(() -> scanAfter(start));
        start.countDown();

        List<ScanResult> results = List.of(
                first.get(5, TimeUnit.SECONDS).getResultCode(),
                second.get(5, TimeUnit.SECONDS).getResultCode()
        );
        executor.shutdownNow();

        assertThat(results).containsExactlyInAnyOrder(ScanResult.VALID_CHECKED_IN, ScanResult.ALREADY_USED);
    }

    @Test
    void rawQrTokenIsNotStoredInCheckInLog() {
        validOnlineScanMarksTicketUsedAndCreatesLog();

        ArgumentCaptor<CheckInLog> logCaptor = ArgumentCaptor.forClass(CheckInLog.class);
        verify(checkInLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getQrTokenId()).isEqualTo("jti-12345");
    }

    @Test
    void malformedAllowedGateIdsDoesNotBecomeUnrestricted() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        ticket.setAllowedGateIds("[not-json");
        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload());
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[not-json")).thenReturn(Optional.empty());

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(ScanResult.WRONG_GATE);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
    }

    private ScanResultResponse scanAfter(CountDownLatch start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return service.scanOnline(request());
    }

    private void assertDenied(TicketAccessState ticket, QrTokenPayload payload, ScanResult expected) {
        when(qrTokenVerifier.verify("signed-token", NOW)).thenReturn(payload);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        lenient().when(checkerAssignmentService.parseAllowedGateIds(ticket.getAllowedGateIds()))
                .thenReturn(Optional.of(List.of("A1", "A2")));

        ScanResultResponse response = service.scanOnline(request());

        assertThat(response.getResultCode()).isEqualTo(expected);
        verify(ticketAccessStateRepository, never()).markUsedIfValid(any(), any(), any(), any(), any());
        verify(checkInLogRepository).save(any(CheckInLog.class));
    }

    private OnlineScanRequest request() {
        return OnlineScanRequest.builder()
                .qrToken("signed-token")
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .deviceId("device-abc")
                .scannedAt(SCANNED_AT)
                .build();
    }

    private QrTokenPayload payload() {
        return new QrTokenPayload(
                12345L,
                "TCK-12345",
                99L,
                501L,
                3,
                NOW.minusSeconds(10),
                NOW.plusSeconds(30),
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
                .seatLabel(null)
                .currentOwnerId(10L)
                .qrVersion(3)
                .accessStatus(status)
                .allowedGateIds("[\"A1\",\"A2\"]")
                .build();
    }
}
