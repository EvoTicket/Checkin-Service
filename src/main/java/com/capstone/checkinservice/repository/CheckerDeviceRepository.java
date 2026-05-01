package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.CheckerDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CheckerDeviceRepository extends JpaRepository<CheckerDevice, Long> {
    Optional<CheckerDevice> findByDeviceId(String deviceId);

    Optional<CheckerDevice> findByDeviceIdAndCheckerId(String deviceId, Long checkerId);

    List<CheckerDevice> findByCheckerId(Long checkerId);

    List<CheckerDevice> findByCheckerIdAndTrustedTrue(Long checkerId);
}
