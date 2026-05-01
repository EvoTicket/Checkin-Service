package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.OfflinePackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OfflinePackageRepository extends JpaRepository<OfflinePackage, Long> {
    Optional<OfflinePackage> findByPackageId(String packageId);

    List<OfflinePackage> findByCheckerIdAndDeviceId(Long checkerId, String deviceId);

    List<OfflinePackage> findByEventIdAndShowtimeId(Long eventId, Long showtimeId);
}
