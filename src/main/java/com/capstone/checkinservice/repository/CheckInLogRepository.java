package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.enums.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface CheckInLogRepository extends JpaRepository<CheckInLog, Long> {
    List<CheckInLog> findByTicketAssetIdOrderByScannedAtDesc(Long ticketAssetId);

    List<CheckInLog> findTop10ByTicketAssetIdOrderByScannedAtDesc(Long ticketAssetId);

    Optional<CheckInLog> findFirstByTicketAssetIdAndScanResultOrderByScannedAtAsc(
            Long ticketAssetId,
            ScanResult scanResult
    );

    List<CheckInLog> findByCheckerIdAndScannedAtBetween(Long checkerId, Instant from, Instant to);
}
