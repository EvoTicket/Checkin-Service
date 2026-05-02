package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.request.CheckerDeviceRegisterRequest;
import com.capstone.checkinservice.dto.response.CheckerDeviceReadinessResponse;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.entity.CheckerDevice;
import com.capstone.checkinservice.enums.CheckerDeviceStatus;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckerDeviceService {
    private final CheckerDeviceRepository checkerDeviceRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Transactional
    public CheckerDeviceResponse registerDevice(CheckerDeviceRegisterRequest request) {
        Long checkerId = currentUserProvider.getCurrentUserId();
        Instant now = clock.instant();
        CheckerDevice device = createPendingDevice(checkerId, request, now);
        CheckerDevice saved = checkerDeviceRepository.save(device);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CheckerDeviceResponse getDevice(String deviceId) {
        Long checkerId = currentUserProvider.getCurrentUserId();
        CheckerDevice device = checkerDeviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> notAllowed("Device is not registered"));
        assertOwnedByChecker(device, checkerId);
        return toResponse(device);
    }

    @Transactional(readOnly = true)
    public CheckerDeviceReadinessResponse getReadiness(String deviceId) {
        Long checkerId = currentUserProvider.getCurrentUserId();
        Instant serverTime = clock.instant();
        return checkerDeviceRepository.findByDeviceId(deviceId)
                .map(device -> readinessForExistingDevice(device, checkerId, serverTime))
                .orElseGet(() -> CheckerDeviceReadinessResponse.builder()
                        .deviceId(deviceId)
                        .checkerId(checkerId)
                        .registered(false)
                        .status(CheckerDeviceStatus.PENDING)
                        .trusted(false)
                        .revoked(false)
                        .serverTime(TimeMapper.toOffsetDateTime(serverTime))
                        .message("Device is not registered.")
                        .build());
    }

    @Transactional(readOnly = true)
    public List<CheckerDeviceResponse> listPendingDevices() {
        return checkerDeviceRepository.findByTrustedFalseAndRevokedAtIsNullOrderByRegisteredAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CheckerDeviceResponse trustDevice(String deviceId) {
        CheckerDevice device = checkerDeviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> notAllowed("Device is not registered"));
        Instant now = clock.instant();
        device.setTrusted(true);
        device.setTrustedAt(now);
        device.setRevokedAt(null);
        return toResponse(checkerDeviceRepository.save(device));
    }

    @Transactional
    public CheckerDeviceResponse revokeDevice(String deviceId) {
        CheckerDevice device = checkerDeviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> notAllowed("Device is not registered"));
        device.setTrusted(false);
        device.setRevokedAt(clock.instant());
        return toResponse(checkerDeviceRepository.save(device));
    }

    private CheckerDevice createPendingDevice(Long checkerId, CheckerDeviceRegisterRequest request, Instant now) {
        return CheckerDevice.builder()
                .deviceId("dev_" + UUID.randomUUID())
                .checkerId(checkerId)
                .deviceName(request.getDeviceName())
                .platform(request.getPlatform())
                .userAgent(request.getUserAgent())
                .appVersion(request.getAppVersion())
                .registeredAt(now)
                .lastSeenAt(now)
                .trusted(false)
                .build();
    }

    private CheckerDeviceReadinessResponse readinessForExistingDevice(
            CheckerDevice device,
            Long checkerId,
            Instant serverTime
    ) {
        assertOwnedByChecker(device, checkerId);

        boolean revoked = device.getRevokedAt() != null;
        return CheckerDeviceReadinessResponse.builder()
                .deviceId(device.getDeviceId())
                .checkerId(device.getCheckerId())
                .registered(true)
                .status(status(device))
                .trusted(device.isTrusted())
                .revoked(revoked)
                .serverTime(TimeMapper.toOffsetDateTime(serverTime))
                .message(deviceMessage(device))
                .build();
    }

    private CheckerDeviceResponse toResponse(CheckerDevice device) {
        return CheckerDeviceResponse.builder()
                .deviceId(device.getDeviceId())
                .checkerId(device.getCheckerId())
                .deviceName(device.getDeviceName())
                .platform(device.getPlatform())
                .userAgent(device.getUserAgent())
                .appVersion(device.getAppVersion())
                .status(status(device))
                .trusted(device.isTrusted())
                .revoked(device.getRevokedAt() != null)
                .registeredAt(TimeMapper.toOffsetDateTime(device.getRegisteredAt()))
                .trustedAt(TimeMapper.toOffsetDateTime(device.getTrustedAt()))
                .revokedAt(TimeMapper.toOffsetDateTime(device.getRevokedAt()))
                .lastSeenAt(TimeMapper.toOffsetDateTime(device.getLastSeenAt()))
                .message(deviceMessage(device))
                .build();
    }

    private CheckerDeviceStatus status(CheckerDevice device) {
        if (device.getRevokedAt() != null) {
            return CheckerDeviceStatus.REVOKED;
        }
        if (device.isTrusted()) {
            return CheckerDeviceStatus.TRUSTED;
        }
        return CheckerDeviceStatus.PENDING;
    }

    private String deviceMessage(CheckerDevice device) {
        return switch (status(device)) {
            case PENDING -> "Thiet bi da duoc ghi nhan. Vui long cho quan ly duyet.";
            case TRUSTED -> "Device is trusted.";
            case REVOKED -> "Device is revoked.";
        };
    }

    private void assertOwnedByChecker(CheckerDevice device, Long checkerId) {
        if (!device.getCheckerId().equals(checkerId)) {
            throw notAllowed("Device is registered to another checker");
        }
    }

    private CheckinBusinessException notAllowed(String message) {
        return new CheckinBusinessException(
                ScanResult.DEVICE_NOT_ALLOWED,
                HttpStatus.FORBIDDEN,
                message
        );
    }
}
