package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@RepositoryTest
class CheckInLogRepositoryTest {
    @Autowired
    private CheckInLogRepository repository;

    @Test
    void saveLogWithQrTokenId() {
        CheckInLog saved = repository.saveAndFlush(log(1001L, ScanResult.VALID_CHECKED_IN,
                Instant.parse("2026-05-01T10:00:00Z")));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getQrTokenId()).isEqualTo("jti-1001");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByTicketAssetIdOrderByScannedAtDescWorks() {
        repository.saveAndFlush(log(1001L, ScanResult.QR_EXPIRED, Instant.parse("2026-05-01T10:00:00Z")));
        repository.saveAndFlush(log(1001L, ScanResult.ALREADY_USED, Instant.parse("2026-05-01T10:03:00Z")));
        repository.saveAndFlush(log(1001L, ScanResult.VALID_CHECKED_IN, Instant.parse("2026-05-01T10:01:00Z")));

        assertThat(repository.findByTicketAssetIdOrderByScannedAtDesc(1001L))
                .extracting(CheckInLog::getScanResult)
                .containsExactly(ScanResult.ALREADY_USED, ScanResult.VALID_CHECKED_IN, ScanResult.QR_EXPIRED);
    }

    @Test
    void findFirstSuccessfulCheckInWorks() {
        repository.saveAndFlush(log(1001L, ScanResult.VALID_CHECKED_IN, Instant.parse("2026-05-01T10:03:00Z")));
        repository.saveAndFlush(log(1001L, ScanResult.VALID_CHECKED_IN, Instant.parse("2026-05-01T10:01:00Z")));

        assertThat(repository.findFirstByTicketAssetIdAndScanResultOrderByScannedAtAsc(
                1001L,
                ScanResult.VALID_CHECKED_IN
        ))
                .isPresent()
                .get()
                .extracting(CheckInLog::getScannedAt)
                .isEqualTo(Instant.parse("2026-05-01T10:01:00Z"));
    }

    @Test
    void findByCheckerIdAndScannedAtBetweenWorks() {
        repository.saveAndFlush(log(1001L, ScanResult.VALID_CHECKED_IN, Instant.parse("2026-05-01T10:01:00Z")));
        repository.saveAndFlush(log(1002L, ScanResult.VALID_CHECKED_IN, Instant.parse("2026-05-01T11:01:00Z")));

        assertThat(repository.findByCheckerIdAndScannedAtBetween(
                7001L,
                Instant.parse("2026-05-01T10:00:00Z"),
                Instant.parse("2026-05-01T10:30:00Z")
        ))
                .extracting(CheckInLog::getTicketAssetId)
                .containsExactly(1001L);
    }

    @Test
    void rawQrTokenFieldDoesNotExist() {
        assertThat(Arrays.stream(CheckInLog.class.getDeclaredFields()).map(Field::getName))
                .isNotEmpty()
                .doesNotContain("qrToken", "rawQrToken", "rawQrPayload");
    }

    private CheckInLog log(Long ticketAssetId, ScanResult result, Instant scannedAt) {
        return CheckInLog.builder()
                .ticketAssetId(ticketAssetId)
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .checkerId(7001L)
                .deviceId("device-abc")
                .scanMode(ScanMode.ONLINE)
                .scanResult(result)
                .qrTokenId("jti-" + ticketAssetId)
                .scannedAt(scannedAt)
                .build();
    }
}
