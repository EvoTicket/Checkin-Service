package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.OfflineSyncItem;
import com.capstone.checkinservice.enums.SyncResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfflineSyncItemRepository extends JpaRepository<OfflineSyncItem, Long> {
    Optional<OfflineSyncItem> findByPackageIdAndLocalScanId(String packageId, String localScanId);

    List<OfflineSyncItem> findByTicketAssetId(Long ticketAssetId);

    List<OfflineSyncItem> findByTicketAssetIdOrderBySyncedAtDesc(Long ticketAssetId);

    List<OfflineSyncItem> findBySyncResult(SyncResult syncResult);

    List<OfflineSyncItem> findByCheckerIdAndDeviceId(Long checkerId, String deviceId);
}
