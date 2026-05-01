package com.capstone.checkinservice.service;

import com.capstone.checkinservice.config.OfflinePackageProperties;
import com.capstone.checkinservice.crypto.key.DevelopmentQrKeyProvider;
import com.capstone.checkinservice.dto.request.OfflinePackageRequest;
import com.capstone.checkinservice.dto.response.OfflinePackageResponse;
import com.capstone.checkinservice.entity.OfflinePackage;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.OfflinePackageRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflinePackageServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T18:42:00Z");

    @Mock
    private TicketAccessStateRepository ticketAccessStateRepository;

    @Mock
    private OfflinePackageRepository offlinePackageRepository;

    @Mock
    private CheckerAssignmentService checkerAssignmentService;

    @Mock
    private CheckerDeviceValidationService checkerDeviceValidationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private OfflinePackageProperties properties;
    private OfflinePackageService service;

    @BeforeEach
    void setUp() {
        properties = new OfflinePackageProperties();
        properties.setTtlMinutes(360);
        properties.setMaxTicketSnapshots(10);
        service = new OfflinePackageService(
                ticketAccessStateRepository,
                offlinePackageRepository,
                checkerAssignmentService,
                checkerDeviceValidationService,
                currentUserProvider,
                new DevelopmentQrKeyProvider("local-dev-key-v1"),
                properties,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
    }

    @Test
    void assignedCheckerCanGenerateOfflinePackage() {
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L))
                .thenReturn(List.of(ticket(1002L, TicketAccessStatus.USED), ticket(1001L, TicketAccessStatus.VALID)));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\"]")).thenReturn(Optional.of(List.of("A1")));

        OfflinePackageResponse response = service.generateOfflinePackage(request());

        assertThat(response.getPackageId()).startsWith("pkg-");
        assertThat(response.getEventId()).isEqualTo(99L);
        assertThat(response.getShowtimeId()).isEqualTo(501L);
        assertThat(response.getGateId()).isEqualTo("A1");
        assertThat(response.getCheckerId()).isEqualTo(7001L);
        assertThat(response.getDeviceId()).isEqualTo("device-abc");
        assertThat(response.getIssuedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(response.getValidUntil()).isEqualTo(OffsetDateTime.ofInstant(NOW.plusSeconds(240 * 60), ZoneOffset.UTC));
        assertThat(response.getKeyId()).isEqualTo("local-dev-key-v1");
        assertThat(response.getKeyAlgorithm()).isEqualTo("EC");
        assertThat(response.getPublicVerificationKey()).isNotBlank();
        assertThat(response.getSnapshotCount()).isEqualTo(2);
        assertThat(response.getChecksum()).startsWith("sha256:");
        assertThat(response.getPackageSignature()).isNull();
        assertThat(response.getTicketSnapshots()).extracting(OfflinePackageResponse.TicketSnapshot::getTicketAssetId)
                .containsExactly(1001L, 1002L);

        ArgumentCaptor<OfflinePackage> packageCaptor = ArgumentCaptor.forClass(OfflinePackage.class);
        verify(offlinePackageRepository).save(packageCaptor.capture());
        assertThat(packageCaptor.getValue().getPackageId()).isEqualTo(response.getPackageId());
        assertThat(packageCaptor.getValue().getTicketCount()).isEqualTo(2);
        assertThat(packageCaptor.getValue().getPackageSignature()).isNull();
    }

    @Test
    void unassignedCheckerCannotGeneratePackage() {
        doThrow(new CheckinBusinessException(
                ScanResult.UNAUTHORIZED_CHECKER,
                HttpStatus.FORBIDDEN,
                "Checker is not authorized"
        )).when(checkerAssignmentService).assertCheckerAssigned(7001L, 99L, 501L, "A1");

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.generateOfflinePackage(request()))
                .satisfies(exception -> {
                    assertThat(exception.getResultCode()).isEqualTo(ScanResult.UNAUTHORIZED_CHECKER);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void packageIsScopedToRequestedEventAndShowtime() {
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L))
                .thenReturn(List.of(ticket(1001L, TicketAccessStatus.VALID)));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\"]")).thenReturn(Optional.of(List.of("A1")));

        OfflinePackageResponse response = service.generateOfflinePackage(request());

        verify(ticketAccessStateRepository).findByEventIdAndShowtimeId(99L, 501L);
        assertThat(response.getTicketSnapshots()).allSatisfy(snapshot -> {
            assertThat(snapshot.getEventId()).isEqualTo(99L);
            assertThat(snapshot.getShowtimeId()).isEqualTo(501L);
        });
    }

    @Test
    void snapshotsIncludeQrVersionAccessStatusAndGatePolicy() {
        TicketAccessState ticket = ticket(1001L, TicketAccessStatus.LOCKED_RESALE);
        ticket.setGatePolicySnapshot("{\"gates\":[\"A1\"]}");
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L)).thenReturn(List.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\"]")).thenReturn(Optional.of(List.of("A1")));

        OfflinePackageResponse.TicketSnapshot snapshot = service.generateOfflinePackage(request())
                .getTicketSnapshots()
                .getFirst();

        assertThat(snapshot.getQrVersion()).isEqualTo(3);
        assertThat(snapshot.getAccessStatus()).isEqualTo(TicketAccessStatus.LOCKED_RESALE);
        assertThat(snapshot.getAllowedGateIds()).containsExactly("A1");
        assertThat(snapshot.getUsedAtGateId()).isNull();
    }

    @Test
    void requestedValidityCannotExceedConfiguredTtl() {
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L)).thenReturn(List.of());

        OfflinePackageResponse response = service.generateOfflinePackage(OfflinePackageRequest.builder()
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .deviceId("device-abc")
                .requestedValidityMinutes(999)
                .build());

        assertThat(response.getValidUntil()).isEqualTo(OffsetDateTime.ofInstant(NOW.plusSeconds(360 * 60), ZoneOffset.UTC));
    }

    @Test
    void deviceValidationFailurePreventsPackageGeneration() {
        doThrow(new CheckinBusinessException(
                ScanResult.DEVICE_NOT_TRUSTED,
                HttpStatus.FORBIDDEN,
                "Device is not trusted"
        )).when(checkerDeviceValidationService).validateForOfflinePackage(7001L, "device-abc");

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.generateOfflinePackage(request()))
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.DEVICE_NOT_TRUSTED));
    }

    @Test
    void tooManySnapshotsReturnsOfflinePackageTooLarge() {
        properties.setMaxTicketSnapshots(1);
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L))
                .thenReturn(List.of(ticket(1001L, TicketAccessStatus.VALID), ticket(1002L, TicketAccessStatus.VALID)));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\"]")).thenReturn(Optional.of(List.of("A1")));

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.generateOfflinePackage(request()))
                .satisfies(exception -> {
                    assertThat(exception.getResultCode()).isEqualTo(ScanResult.OFFLINE_PACKAGE_TOO_LARGE);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void packageDoesNotExposeRawQrPrivateKeyOrPii() throws Exception {
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L))
                .thenReturn(List.of(ticket(1001L, TicketAccessStatus.VALID)));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\"]")).thenReturn(Optional.of(List.of("A1")));

        String json = new ObjectMapper().registerModule(new JavaTimeModule())
                .writeValueAsString(service.generateOfflinePackage(request()));

        assertThat(json).doesNotContain("qrToken", "rawQrToken", "privateKey", "currentOwnerId", "email", "phone");
    }

    @Test
    void malformedAllowedGateIdsFailsPackageGeneration() {
        TicketAccessState ticket = ticket(1001L, TicketAccessStatus.VALID);
        ticket.setAllowedGateIds("[not-json");
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L)).thenReturn(List.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[not-json")).thenReturn(Optional.empty());

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.generateOfflinePackage(request()))
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(ScanResult.WRONG_GATE));
    }

    @Test
    void usedTicketSnapshotIncludesUsedAtGateId() {
        TicketAccessState ticket = ticket(1001L, TicketAccessStatus.USED);
        ticket.setUsedAtGateId("A1");
        when(ticketAccessStateRepository.findByEventIdAndShowtimeId(99L, 501L)).thenReturn(List.of(ticket));
        when(checkerAssignmentService.parseAllowedGateIds("[\"A1\"]")).thenReturn(Optional.of(List.of("A1")));

        OfflinePackageResponse.TicketSnapshot snapshot = service.generateOfflinePackage(request())
                .getTicketSnapshots()
                .getFirst();

        assertThat(snapshot.getUsedAtGateId()).isEqualTo("A1");
    }

    private OfflinePackageRequest request() {
        return OfflinePackageRequest.builder()
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .deviceId("device-abc")
                .requestedValidityMinutes(240)
                .build();
    }

    private TicketAccessState ticket(Long ticketAssetId, TicketAccessStatus status) {
        return TicketAccessState.builder()
                .ticketAssetId(ticketAssetId)
                .ticketCode("TCK-" + ticketAssetId)
                .eventId(99L)
                .showtimeId(501L)
                .ticketTypeName("VIP Standing")
                .zoneLabel("Zone A")
                .seatLabel("A-" + ticketAssetId)
                .currentOwnerId(10L)
                .qrVersion(3)
                .accessStatus(status)
                .allowedGateIds("[\"A1\"]")
                .usedAt(status == TicketAccessStatus.USED ? NOW.minusSeconds(60) : null)
                .build();
    }
}
