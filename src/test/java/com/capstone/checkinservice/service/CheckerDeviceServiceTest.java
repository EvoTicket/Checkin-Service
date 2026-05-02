package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.request.CheckerDeviceRegisterRequest;
import com.capstone.checkinservice.dto.response.CheckerDeviceReadinessResponse;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.entity.CheckerDevice;
import com.capstone.checkinservice.enums.CheckerDeviceStatus;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckerDeviceServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T10:00:00Z");

    @Mock
    private CheckerDeviceRepository checkerDeviceRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private CheckerDeviceService service;

    @BeforeEach
    void setUp() {
        service = new CheckerDeviceService(
                checkerDeviceRepository,
                currentUserProvider,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void registerDevice_createsPendingUntrustedDeviceWithServerGeneratedId() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.registerDevice(request());

        assertThat(response.getDeviceId()).startsWith("dev_");
        assertThat(response.getCheckerId()).isEqualTo(7001L);
        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.PENDING);
        assertThat(response.isTrusted()).isFalse();
        assertThat(response.isRevoked()).isFalse();
        assertThat(response.getRegisteredAt().toInstant()).isEqualTo(NOW);
        assertThat(response.getLastSeenAt().toInstant()).isEqualTo(NOW);
    }

    @Test
    void registerDevice_ignoresClientProvidedDeviceIdForNewDevice() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.registerDevice(request());

        assertThat(response.getDeviceId()).isNotEqualTo("device-abc");
    }

    @Test
    void getDeviceStatusWorksForOwnerChecker() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device(7001L, true, null)));

        CheckerDeviceResponse response = service.getDevice("device-abc");

        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.TRUSTED);
        assertThat(response.isTrusted()).isTrue();
        assertThat(response.isRevoked()).isFalse();
    }

    @Test
    void readiness_returnsRegisteredTrustedState() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device(7001L, true, null)));

        CheckerDeviceReadinessResponse response = service.getReadiness("device-abc");

        assertThat(response.isRegistered()).isTrue();
        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.TRUSTED);
        assertThat(response.isTrusted()).isTrue();
        assertThat(response.isRevoked()).isFalse();
        assertThat(response.getServerTime().toInstant()).isEqualTo(NOW);
    }

    @Test
    void readiness_rejectsDeviceOwnedByAnotherChecker() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device(7002L, true, null)));

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.getReadiness("device-abc"))
                .satisfies(exception -> {
                    assertThat(exception.getResultCode()).isEqualTo(ScanResult.DEVICE_NOT_ALLOWED);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void revokedDevice_readinessShowsRevoked() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc"))
                .thenReturn(Optional.of(device(7001L, true, NOW.minusSeconds(1))));

        CheckerDeviceReadinessResponse response = service.getReadiness("device-abc");

        assertThat(response.isRegistered()).isTrue();
        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.REVOKED);
        assertThat(response.isRevoked()).isTrue();
        assertThat(response.getMessage()).containsIgnoringCase("revoked");
    }

    @Test
    void listPendingDevicesReturnsPendingDeviceResponses() {
        CheckerDevice newer = device("checker-device-b-02", 7002L, false, null);
        CheckerDevice older = device("dev_old", 7001L, false, null);
        older.setRegisteredAt(NOW.minusSeconds(7200));

        when(checkerDeviceRepository.findByTrustedFalseAndRevokedAtIsNullOrderByRegisteredAtDesc())
                .thenReturn(List.of(newer, older));

        List<CheckerDeviceResponse> response = service.listPendingDevices();

        assertThat(response)
                .extracting(CheckerDeviceResponse::getDeviceId)
                .containsExactly("checker-device-b-02", "dev_old");
        assertThat(response)
                .extracting(CheckerDeviceResponse::getStatus)
                .containsExactly(CheckerDeviceStatus.PENDING, CheckerDeviceStatus.PENDING);
        assertThat(response)
                .allSatisfy(device -> {
                    assertThat(device.isTrusted()).isFalse();
                    assertThat(device.isRevoked()).isFalse();
                });
    }

    @Test
    void trustDeviceMarksDeviceTrusted() {
        CheckerDevice device = device(7001L, false, NOW.minusSeconds(1));
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device));
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.trustDevice("device-abc");

        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.TRUSTED);
        assertThat(response.isTrusted()).isTrue();
        assertThat(response.isRevoked()).isFalse();
        assertThat(response.getTrustedAt().toInstant()).isEqualTo(NOW);
        assertThat(response.getRevokedAt()).isNull();
        assertThat(device.isTrusted()).isTrue();
        assertThat(device.getTrustedAt()).isEqualTo(NOW);
        assertThat(device.getRevokedAt()).isNull();
    }

    @Test
    void revokeDeviceMarksDeviceRevoked() {
        CheckerDevice device = device(7001L, true, null);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device));
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.revokeDevice("device-abc");

        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.REVOKED);
        assertThat(response.isTrusted()).isFalse();
        assertThat(response.isRevoked()).isTrue();
        assertThat(response.getRevokedAt().toInstant()).isEqualTo(NOW);
        assertThat(device.isTrusted()).isFalse();
        assertThat(device.getRevokedAt()).isEqualTo(NOW);
    }

    @Test
    void trustDeviceUnknownBusinessDeviceIdThrowsBusinessError() {
        when(checkerDeviceRepository.findByDeviceId("missing-device")).thenReturn(Optional.empty());

        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(() -> service.trustDevice("missing-device"))
                .withMessage("Device is not registered")
                .satisfies(exception -> {
                    assertThat(exception.getResultCode()).isEqualTo(ScanResult.DEVICE_NOT_ALLOWED);
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
    }

    @Test
    void trustDeviceUsesBusinessDeviceIdNotDatabaseId() {
        CheckerDevice device = device("checker-device-b-02", 7001L, false, null);
        device.setId(42L);
        when(checkerDeviceRepository.findByDeviceId("checker-device-b-02")).thenReturn(Optional.of(device));
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.trustDevice("checker-device-b-02");

        assertThat(response.getDeviceId()).isEqualTo("checker-device-b-02");
        verify(checkerDeviceRepository).findByDeviceId("checker-device-b-02");
        verify(checkerDeviceRepository, never()).findById(any(Long.class));
    }

    private CheckerDeviceRegisterRequest request() {
        return CheckerDeviceRegisterRequest.builder()
                .deviceId("device-abc")
                .deviceName("Gate phone")
                .platform("WEB")
                .userAgent("Mozilla/5.0")
                .appVersion("0.9.3")
                .build();
    }

    private CheckerDevice device(Long checkerId, boolean trusted, Instant revokedAt) {
        return device("device-abc", checkerId, trusted, revokedAt);
    }

    private CheckerDevice device(String deviceId, Long checkerId, boolean trusted, Instant revokedAt) {
        return CheckerDevice.builder()
                .deviceId(deviceId)
                .checkerId(checkerId)
                .deviceName("Old phone")
                .platform("WEB")
                .registeredAt(NOW.minusSeconds(3600))
                .lastSeenAt(NOW.minusSeconds(60))
                .trusted(trusted)
                .revokedAt(revokedAt)
                .build();
    }
}
