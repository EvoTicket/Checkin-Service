package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.CheckerDevice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class CheckerDeviceRepositoryTest {
    @Autowired
    private CheckerDeviceRepository repository;

    @Test
    void deviceIdUniquenessIsEnforced() {
        repository.saveAndFlush(device("device-abc", 7001L, true));

        assertThatThrownBy(() -> repository.saveAndFlush(device("device-abc", 7002L, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByDeviceIdWorks() {
        repository.saveAndFlush(device("device-abc", 7001L, true));

        assertThat(repository.findByDeviceId("device-abc"))
                .isPresent()
                .get()
                .extracting(CheckerDevice::getCheckerId)
                .isEqualTo(7001L);
    }

    @Test
    void findByDeviceIdAndCheckerIdWorks() {
        repository.saveAndFlush(device("device-abc", 7001L, true));

        assertThat(repository.findByDeviceIdAndCheckerId("device-abc", 7001L)).isPresent();
        assertThat(repository.findByDeviceIdAndCheckerId("device-abc", 7002L)).isEmpty();
    }

    @Test
    void findByCheckerIdWorks() {
        repository.saveAndFlush(device("device-abc", 7001L, true));
        repository.saveAndFlush(device("device-def", 7001L, false));
        repository.saveAndFlush(device("device-xyz", 7002L, true));

        assertThat(repository.findByCheckerId(7001L))
                .extracting(CheckerDevice::getDeviceId)
                .containsExactlyInAnyOrder("device-abc", "device-def");
    }

    @Test
    void findTrustedDevicesByCheckerId() {
        repository.saveAndFlush(device("device-abc", 7001L, true));
        repository.saveAndFlush(device("device-def", 7001L, false));

        assertThat(repository.findByCheckerIdAndTrustedTrue(7001L))
                .extracting(CheckerDevice::getDeviceId)
                .containsExactly("device-abc");
    }

    @Test
    void findPendingDevicesReturnsUntrustedNonRevokedOrderedByRegisteredAtDescending() {
        repository.saveAndFlush(device(
                "pending-old",
                7001L,
                false,
                Instant.parse("2026-05-01T08:00:00Z"),
                null
        ));
        repository.saveAndFlush(device(
                "pending-new",
                7002L,
                false,
                Instant.parse("2026-05-01T09:00:00Z"),
                null
        ));
        repository.saveAndFlush(device(
                "trusted-device",
                7003L,
                true,
                Instant.parse("2026-05-01T10:00:00Z"),
                null
        ));
        repository.saveAndFlush(device(
                "revoked-device",
                7004L,
                false,
                Instant.parse("2026-05-01T11:00:00Z"),
                Instant.parse("2026-05-01T11:30:00Z")
        ));

        assertThat(repository.findByTrustedFalseAndRevokedAtIsNullOrderByRegisteredAtDesc())
                .extracting(CheckerDevice::getDeviceId)
                .containsExactly("pending-new", "pending-old");
    }

    private CheckerDevice device(String deviceId, Long checkerId, boolean trusted) {
        return device(deviceId, checkerId, trusted, null, null);
    }

    private CheckerDevice device(
            String deviceId,
            Long checkerId,
            boolean trusted,
            Instant registeredAt,
            Instant revokedAt
    ) {
        return CheckerDevice.builder()
                .deviceId(deviceId)
                .checkerId(checkerId)
                .deviceName("Gate phone")
                .platform("WEB")
                .registeredAt(registeredAt)
                .lastSeenAt(Instant.parse("2026-05-01T08:00:00Z"))
                .trusted(trusted)
                .revokedAt(revokedAt)
                .build();
    }
}
