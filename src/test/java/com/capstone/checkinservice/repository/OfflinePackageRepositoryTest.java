package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.OfflinePackage;
import com.capstone.checkinservice.enums.OfflinePackageStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class OfflinePackageRepositoryTest {
    @Autowired
    private OfflinePackageRepository repository;

    @Test
    void packageIdUniquenessIsEnforced() {
        repository.saveAndFlush(offlinePackage("pkg-1", 7001L, "device-abc", 99L, 501L));

        assertThatThrownBy(() -> repository.saveAndFlush(
                offlinePackage("pkg-1", 7002L, "device-def", 99L, 501L)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByPackageIdWorks() {
        repository.saveAndFlush(offlinePackage("pkg-1", 7001L, "device-abc", 99L, 501L));

        assertThat(repository.findByPackageId("pkg-1"))
                .isPresent()
                .get()
                .extracting(OfflinePackage::getCheckerId)
                .isEqualTo(7001L);
    }

    @Test
    void findByCheckerIdAndDeviceIdWorks() {
        repository.saveAndFlush(offlinePackage("pkg-1", 7001L, "device-abc", 99L, 501L));
        repository.saveAndFlush(offlinePackage("pkg-2", 7001L, "device-abc", 99L, 502L));
        repository.saveAndFlush(offlinePackage("pkg-3", 7002L, "device-abc", 99L, 501L));

        assertThat(repository.findByCheckerIdAndDeviceId(7001L, "device-abc"))
                .extracting(OfflinePackage::getPackageId)
                .containsExactlyInAnyOrder("pkg-1", "pkg-2");
    }

    @Test
    void findByEventIdAndShowtimeIdWorks() {
        repository.saveAndFlush(offlinePackage("pkg-1", 7001L, "device-abc", 99L, 501L));
        repository.saveAndFlush(offlinePackage("pkg-2", 7001L, "device-abc", 99L, 502L));

        assertThat(repository.findByEventIdAndShowtimeId(99L, 501L))
                .extracting(OfflinePackage::getPackageId)
                .containsExactly("pkg-1");
    }

    private OfflinePackage offlinePackage(
            String packageId,
            Long checkerId,
            String deviceId,
            Long eventId,
            Long showtimeId
    ) {
        return OfflinePackage.builder()
                .packageId(packageId)
                .eventId(eventId)
                .showtimeId(showtimeId)
                .gateId("A1")
                .checkerId(checkerId)
                .deviceId(deviceId)
                .issuedAt(Instant.parse("2026-05-01T08:00:00Z"))
                .validUntil(Instant.parse("2026-05-01T12:00:00Z"))
                .keyId("qr-key-2026-05")
                .ticketCount(10)
                .checksum("sha256:test")
                .packageSignature("signature")
                .status(OfflinePackageStatus.ACTIVE)
                .build();
    }
}
