package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.request.CheckerDeviceRegisterRequest;
import com.capstone.checkinservice.dto.response.CheckerDeviceReadinessResponse;
import com.capstone.checkinservice.dto.response.CheckerDeviceResponse;
import com.capstone.checkinservice.entity.CheckerDevice;
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
        CheckerDevice device = checkerDeviceRepository.findByDeviceId(request.getDeviceId())
                .map(existing -> updateExistingDevice(existing, checkerId, request, now))
                .orElseGet(() -> createDevice(checkerId, request, now));

        CheckerDevice saved = checkerDeviceRepository.save(device);
        return toResponse(saved);
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
                        .trusted(false)
                        .revoked(false)
                        .serverTime(TimeMapper.toOffsetDateTime(serverTime))
                        .message("Device is not registered.")
                        .build());
    }

    private CheckerDevice updateExistingDevice(
            CheckerDevice device,
            Long checkerId,
            CheckerDeviceRegisterRequest request,
            Instant now
    ) {
        device.setCheckerId(checkerId);
        if (request.getDeviceName() != null) {
            device.setDeviceName(request.getDeviceName());
        }
        if (request.getPlatform() != null) {
            device.setPlatform(request.getPlatform());
        }
        device.setLastSeenAt(now);
        return device;
    }

    private CheckerDevice createDevice(Long checkerId, CheckerDeviceRegisterRequest request, Instant now) {
        return CheckerDevice.builder()
                .deviceId(request.getDeviceId())
                .checkerId(checkerId)
                .deviceName(request.getDeviceName())
                .platform(request.getPlatform())
                .lastSeenAt(now)
                .trusted(true)
                .build();
    }

    private CheckerDeviceReadinessResponse readinessForExistingDevice(
            CheckerDevice device,
            Long checkerId,
            Instant serverTime
    ) {
        if (!device.getCheckerId().equals(checkerId)) {
            throw new CheckinBusinessException(
                    ScanResult.UNAUTHORIZED_CHECKER,
                    HttpStatus.FORBIDDEN,
                    "Device is registered to another checker"
            );
        }

        boolean revoked = device.getRevokedAt() != null;
        return CheckerDeviceReadinessResponse.builder()
                .deviceId(device.getDeviceId())
                .checkerId(device.getCheckerId())
                .registered(true)
                .trusted(device.isTrusted())
                .revoked(revoked)
                .serverTime(TimeMapper.toOffsetDateTime(serverTime))
                .message(revoked ? "Device is revoked." : "Device is ready.")
                .build();
    }

    private CheckerDeviceResponse toResponse(CheckerDevice device) {
        return CheckerDeviceResponse.builder()
                .deviceId(device.getDeviceId())
                .checkerId(device.getCheckerId())
                .deviceName(device.getDeviceName())
                .platform(device.getPlatform())
                .trusted(device.isTrusted())
                .revokedAt(TimeMapper.toOffsetDateTime(device.getRevokedAt()))
                .lastSeenAt(TimeMapper.toOffsetDateTime(device.getLastSeenAt()))
                .build();
    }
}
