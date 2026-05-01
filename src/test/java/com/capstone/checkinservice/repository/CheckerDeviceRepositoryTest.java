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

    private CheckerDevice device(String deviceId, Long checkerId, boolean trusted) {
        return CheckerDevice.builder()
                .deviceId(deviceId)
                .checkerId(checkerId)
                .deviceName("Gate phone")
                .platform("WEB")
                .lastSeenAt(Instant.parse("2026-05-01T08:00:00Z"))
                .trusted(trusted)
                .build();
    }
}
