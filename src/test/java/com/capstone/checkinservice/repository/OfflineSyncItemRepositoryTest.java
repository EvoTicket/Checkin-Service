package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.OfflineSyncItem;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class OfflineSyncItemRepositoryTest {
    @Autowired
    private OfflineSyncItemRepository repository;

    @Test
    void uniquePackageIdAndLocalScanIdIsEnforced() {
        repository.saveAndFlush(syncItem("pkg-1", "local-1", 1001L, 7001L, "device-abc", SyncResult.SYNC_ACCEPTED));

        assertThatThrownBy(() -> repository.saveAndFlush(
                syncItem("pkg-1", "local-1", 1002L, 7001L, "device-abc", SyncResult.SYNC_ACCEPTED)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByPackageIdAndLocalScanIdWorks() {
        repository.saveAndFlush(syncItem("pkg-1", "local-1", 1001L, 7001L, "device-abc", SyncResult.SYNC_ACCEPTED));

        assertThat(repository.findByPackageIdAndLocalScanId("pkg-1", "local-1"))
                .isPresent()
                .get()
                .extracting(OfflineSyncItem::getTicketAssetId)
                .isEqualTo(1001L);
    }

    @Test
    void findBySyncResultWorks() {
        repository.saveAndFlush(syncItem("pkg-1", "local-1", 1001L, 7001L, "device-abc", SyncResult.SYNC_ACCEPTED));
        repository.saveAndFlush(syncItem("pkg-1", "local-2", 1002L, 7001L, "device-abc", SyncResult.SYNC_CONFLICT));

        assertThat(repository.findBySyncResult(SyncResult.SYNC_CONFLICT))
                .extracting(OfflineSyncItem::getTicketAssetId)
                .containsExactly(1002L);
    }

    @Test
    void findByTicketAssetIdWorks() {
        repository.saveAndFlush(syncItem("pkg-1", "local-1", 1001L, 7001L, "device-abc", SyncResult.SYNC_ACCEPTED));
        repository.saveAndFlush(syncItem("pkg-1", "local-2", 1002L, 7001L, "device-abc", SyncResult.SYNC_CONFLICT));

        assertThat(repository.findByTicketAssetId(1001L))
                .extracting(OfflineSyncItem::getLocalScanId)
                .containsExactly("local-1");
    }

    @Test
    void findByCheckerIdAndDeviceIdWorks() {
        repository.saveAndFlush(syncItem("pkg-1", "local-1", 1001L, 7001L, "device-abc", SyncResult.SYNC_ACCEPTED));
        repository.saveAndFlush(syncItem("pkg-1", "local-2", 1002L, 7001L, "device-def", SyncResult.SYNC_CONFLICT));

        assertThat(repository.findByCheckerIdAndDeviceId(7001L, "device-abc"))
                .extracting(OfflineSyncItem::getTicketAssetId)
                .containsExactly(1001L);
    }

    private OfflineSyncItem syncItem(
            String packageId,
            String localScanId,
            Long ticketAssetId,
            Long checkerId,
            String deviceId,
            SyncResult syncResult
    ) {
        return OfflineSyncItem.builder()
                .packageId(packageId)
                .localScanId(localScanId)
                .ticketAssetId(ticketAssetId)
                .qrTokenId("jti-" + ticketAssetId)
                .checkerId(checkerId)
                .deviceId(deviceId)
                .eventId(99L)
                .showtimeId(501L)
                .gateId("A1")
                .localResultCode(ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC)
                .syncResult(syncResult)
                .serverScanResult(syncResult == SyncResult.SYNC_ACCEPTED
                        ? ScanResult.VALID_CHECKED_IN
                        : ScanResult.ALREADY_USED)
                .scannedAt(Instant.parse("2026-05-01T09:00:00Z"))
                .syncedAt(Instant.parse("2026-05-01T10:00:00Z"))
                .conflictDetails(syncResult == SyncResult.SYNC_CONFLICT ? "{\"serverResult\":\"ALREADY_USED\"}" : null)
                .build();
    }
}
