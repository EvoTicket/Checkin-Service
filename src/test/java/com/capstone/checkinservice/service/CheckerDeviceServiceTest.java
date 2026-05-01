package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.request.CheckerDeviceRegisterRequest;
import com.capstone.checkinservice.dto.response.CheckerDeviceReadinessResponse;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.entity.CheckerDevice;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    void registerDevice_createsNewDevice() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.empty());
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.registerDevice(request());

        assertThat(response.getDeviceId()).isEqualTo("device-abc");
        assertThat(response.getCheckerId()).isEqualTo(7001L);
        assertThat(response.isTrusted()).isTrue();
        assertThat(response.getLastSeenAt().toInstant()).isEqualTo(NOW);
    }

    @Test
    void registerDevice_updatesExistingDeviceLastSeenAt() {
        CheckerDevice existing = device(7001L, true, null);
        existing.setLastSeenAt(NOW.minusSeconds(60));
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.registerDevice(request());

        ArgumentCaptor<CheckerDevice> captor = ArgumentCaptor.forClass(CheckerDevice.class);
        verify(checkerDeviceRepository).save(captor.capture());
        assertThat(captor.getValue().getLastSeenAt()).isEqualTo(NOW);
    }

    @Test
    void registerDevice_bindsDeviceToCurrentChecker() {
        CheckerDevice existing = device(7002L, true, null);
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(existing));
        when(checkerDeviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CheckerDeviceResponse response = service.registerDevice(request());

        assertThat(response.getCheckerId()).isEqualTo(7001L);
    }

    @Test
    void readiness_returnsRegisteredTrustedState() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(7001L);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device(7001L, true, null)));

        CheckerDeviceReadinessResponse response = service.getReadiness("device-abc");

        assertThat(response.isRegistered()).isTrue();
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
                    assertThat(exception.getResultCode()).isEqualTo(ScanResult.UNAUTHORIZED_CHECKER);
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
        assertThat(response.isRevoked()).isTrue();
        assertThat(response.getMessage()).containsIgnoringCase("revoked");
    }

    private CheckerDeviceRegisterRequest request() {
        return CheckerDeviceRegisterRequest.builder()
                .deviceId("device-abc")
                .deviceName("Gate phone")
                .platform("WEB")
                .build();
    }

    private CheckerDevice device(Long checkerId, boolean trusted, Instant revokedAt) {
        return CheckerDevice.builder()
                .deviceId("device-abc")
                .checkerId(checkerId)
                .deviceName("Old phone")
                .platform("WEB")
                .lastSeenAt(NOW.minusSeconds(60))
                .trusted(trusted)
                .revokedAt(revokedAt)
                .build();
    }
}
