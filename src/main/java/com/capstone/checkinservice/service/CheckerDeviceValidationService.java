package com.capstone.checkinservice.service;

import com.capstone.checkinservice.config.CheckerDeviceProperties;
import com.capstone.checkinservice.entity.CheckerDevice;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class CheckerDeviceValidationService {
    private final CheckerDeviceRepository checkerDeviceRepository;
    private final CheckerDeviceProperties checkerDeviceProperties;
    private final Clock clock;

    @Transactional
    public void validateForOnlineScan(Long checkerId, String deviceId) {
        if (!hasText(deviceId)) {
            if (checkerDeviceProperties.getDevice().isRequiredForOnlineScan()) {
                throw deviceException(ScanResult.DEVICE_NOT_ALLOWED, "Checker device is required for online scan");
            }
            return;
        }

        validateTrustedDevice(checkerId, deviceId);
    }

    @Transactional
    public void validateForOfflinePackage(Long checkerId, String deviceId) {
        if (!hasText(deviceId)) {
            throw deviceException(ScanResult.DEVICE_NOT_ALLOWED, "Checker device is required for offline package");
        }

        validateTrustedDevice(checkerId, deviceId);
    }

    private void validateTrustedDevice(Long checkerId, String deviceId) {
        CheckerDevice device = checkerDeviceRepository.findByDeviceId(deviceId.trim())
                .orElseThrow(() -> deviceException(
                        ScanResult.DEVICE_NOT_ALLOWED,
                        "Checker device is not registered"
                ));

        if (!device.getCheckerId().equals(checkerId)) {
            throw deviceException(ScanResult.DEVICE_NOT_ALLOWED, "Checker device belongs to another checker");
        }

        if (device.getRevokedAt() != null) {
            throw deviceException(ScanResult.DEVICE_REVOKED, "Checker device has been revoked");
        }

        if (!device.isTrusted()) {
            throw deviceException(ScanResult.DEVICE_NOT_TRUSTED, "Checker device is not trusted");
        }

        device.setLastSeenAt(clock.instant());
        checkerDeviceRepository.save(device);
    }

    private CheckinBusinessException deviceException(ScanResult result, String message) {
        return new CheckinBusinessException(result, HttpStatus.FORBIDDEN, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
