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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    void trustDeviceMarksDeviceTrusted() {
        CheckerDevice device = device(7001L, false, null);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device));
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.trustDevice("device-abc");

        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.TRUSTED);
        assertThat(response.getTrustedAt().toInstant()).isEqualTo(NOW);
    }

    @Test
    void revokeDeviceMarksDeviceRevoked() {
        CheckerDevice device = device(7001L, true, null);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device));
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.revokeDevice("device-abc");

        assertThat(response.getStatus()).isEqualTo(CheckerDeviceStatus.REVOKED);
        assertThat(response.getRevokedAt().toInstant()).isEqualTo(NOW);
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
        return CheckerDevice.builder()
                .deviceId("device-abc")
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
