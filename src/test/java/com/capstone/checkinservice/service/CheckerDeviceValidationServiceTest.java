package com.capstone.checkinservice.service;

import com.capstone.checkinservice.config.CheckerDeviceProperties;
import com.capstone.checkinservice.entity.CheckerDevice;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckerDeviceValidationServiceTest {
    private static final Instant NOW = Instant.parse("2026-05-01T10:00:00Z");

    @Mock
    private CheckerDeviceRepository checkerDeviceRepository;

    private CheckerDeviceProperties properties;
    private CheckerDeviceValidationService service;

    @BeforeEach
    void setUp() {
        properties = new CheckerDeviceProperties();
        service = new CheckerDeviceValidationService(
                checkerDeviceRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void trustedDeviceIsAcceptedByValidationService() {
        CheckerDevice device = device(7001L, true, null);
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.of(device));

        service.validateForOfflinePackage(7001L, "device-abc");

        assertThat(device.getLastSeenAt()).isEqualTo(NOW);
        verify(checkerDeviceRepository).save(device);
    }

    @Test
    void revokedDeviceIsRejectedByValidationService() {
        when(checkerDeviceRepository.findByDeviceId("device-abc"))
                .thenReturn(Optional.of(device(7001L, true, NOW.minusSeconds(1))));

        assertDeviceResult(() -> service.validateForOfflinePackage(7001L, "device-abc"), ScanResult.DEVICE_REVOKED);
    }

    @Test
    void untrustedDeviceIsRejectedByValidationService() {
        when(checkerDeviceRepository.findByDeviceId("device-abc"))
                .thenReturn(Optional.of(device(7001L, false, null)));

        assertDeviceResult(() -> service.validateForOfflinePackage(7001L, "device-abc"), ScanResult.DEVICE_NOT_TRUSTED);
    }

    @Test
    void anotherCheckerDeviceIsRejectedByValidationService() {
        when(checkerDeviceRepository.findByDeviceId("device-abc"))
                .thenReturn(Optional.of(device(7002L, true, null)));

        assertDeviceResult(() -> service.validateForOfflinePackage(7001L, "device-abc"), ScanResult.DEVICE_NOT_ALLOWED);
    }

    @Test
    void unknownDeviceCannotGenerateOfflinePackage() {
        when(checkerDeviceRepository.findByDeviceId("device-abc")).thenReturn(Optional.empty());

        assertDeviceResult(() -> service.validateForOfflinePackage(7001L, "device-abc"), ScanResult.DEVICE_NOT_ALLOWED);
    }

    @Test
    void onlineScanMissingDeviceAllowedByDefaultConfig() {
        service.validateForOnlineScan(7001L, null);
    }

    @Test
    void onlineScanMissingDeviceRejectedWhenRequiredByConfig() {
        properties.getDevice().setRequiredForOnlineScan(true);

        assertDeviceResult(() -> service.validateForOnlineScan(7001L, null), ScanResult.DEVICE_NOT_ALLOWED);
    }

    private void assertDeviceResult(Runnable action, ScanResult expected) {
        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> assertThat(exception.getResultCode()).isEqualTo(expected));
    }

    private CheckerDevice device(Long checkerId, boolean trusted, Instant revokedAt) {
        return CheckerDevice.builder()
                .deviceId("device-abc")
                .checkerId(checkerId)
                .trusted(trusted)
                .revokedAt(revokedAt)
                .registeredAt(NOW.minusSeconds(60))
                .lastSeenAt(NOW.minusSeconds(60))
                .build();
    }
}
