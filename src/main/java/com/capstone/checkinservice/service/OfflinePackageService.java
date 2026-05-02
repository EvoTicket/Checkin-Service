package com.capstone.checkinservice.service;

import com.capstone.checkinservice.config.OfflinePackageProperties;
import com.capstone.checkinservice.crypto.key.QrKeyProvider;
import com.capstone.checkinservice.crypto.key.QrSigningKey;
import com.capstone.checkinservice.dto.request.OfflinePackageRequest;
import com.capstone.checkinservice.dto.response.OfflinePackageResponse;
import com.capstone.checkinservice.entity.OfflinePackage;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.OfflinePackageStatus;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.OfflinePackageRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfflinePackageService {
    private final TicketAccessStateRepository ticketAccessStateRepository;
    private final OfflinePackageRepository offlinePackageRepository;
    private final CheckerAssignmentService checkerAssignmentService;
    private final CheckerDeviceValidationService checkerDeviceValidationService;
    private final CurrentUserProvider currentUserProvider;
    private final QrKeyProvider qrKeyProvider;
    private final OfflinePackageProperties offlinePackageProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public OfflinePackageResponse generateOfflinePackage(OfflinePackageRequest request) {
        Long checkerId = currentUserProvider.getCurrentUserId();
        String gateId = normalize(request.getGateId());
        String deviceId = normalize(request.getDeviceId());

        checkerAssignmentService.assertCheckerAssigned(checkerId, request.getEventId(), request.getShowtimeId(), gateId);
        checkerDeviceValidationService.validateForOfflinePackage(checkerId, deviceId);

        Instant issuedAt = clock.instant();
        Instant validUntil = issuedAt.plusSeconds(resolveTtlMinutes(request) * 60L);
        QrSigningKey currentKey = qrKeyProvider.getCurrentSigningKey();
        List<OfflinePackageResponse.TicketSnapshot> snapshots = ticketAccessStateRepository
                .findByEventIdAndShowtimeId(request.getEventId(), request.getShowtimeId())
                .stream()
                .sorted(Comparator.comparing(TicketAccessState::getTicketAssetId))
                .map(this::toSnapshot)
                .toList();

        enforceMaxSnapshotCount(snapshots.size());

        String packageId = "pkg-" + UUID.randomUUID();
        String publicVerificationKey = Base64.getEncoder().encodeToString(currentKey.publicKey().getEncoded());
        String checksum = checksum(packageId, request, checkerId, deviceId, issuedAt, validUntil, currentKey.kid(), snapshots);

        offlinePackageRepository.save(OfflinePackage.builder()
                .packageId(packageId)
                .eventId(request.getEventId())
                .showtimeId(request.getShowtimeId())
                .gateId(gateId)
                .checkerId(checkerId)
                .deviceId(deviceId)
                .issuedAt(issuedAt)
                .validUntil(validUntil)
                .keyId(currentKey.kid())
                .ticketCount(snapshots.size())
                .checksum(checksum)
                .packageSignature(null)
                .status(OfflinePackageStatus.ACTIVE)
                .build());

        return OfflinePackageResponse.builder()
                .packageId(packageId)
                .eventId(request.getEventId())
                .showtimeId(request.getShowtimeId())
                .gateId(gateId)
                .checkerId(checkerId)
                .deviceId(deviceId)
                .issuedAt(TimeMapper.toOffsetDateTime(issuedAt))
                .validUntil(TimeMapper.toOffsetDateTime(validUntil))
                .keyId(currentKey.kid())
                .publicVerificationKey(publicVerificationKey)
                .keyVersion(currentKey.kid())
                .keyAlgorithm(currentKey.publicKey().getAlgorithm())
                .snapshotCount(snapshots.size())
                .ticketSnapshots(snapshots)
                .checksum(checksum)
                .packageSignature(null)
                .build();
    }

    private long resolveTtlMinutes(OfflinePackageRequest request) {
        if (request.getRequestedValidityMinutes() == null) {
            return offlinePackageProperties.getTtlMinutes();
        }
        return Math.min(request.getRequestedValidityMinutes(), offlinePackageProperties.getTtlMinutes());
    }

    private void enforceMaxSnapshotCount(int snapshotCount) {
        if (snapshotCount > offlinePackageProperties.getMaxTicketSnapshots()) {
            throw new CheckinBusinessException(
                    ScanResult.OFFLINE_PACKAGE_TOO_LARGE,
                    HttpStatus.BAD_REQUEST,
                    "Requested offline package exceeds the maximum ticket snapshot limit"
            );
        }
    }

    private OfflinePackageResponse.TicketSnapshot toSnapshot(TicketAccessState ticket) {
        return OfflinePackageResponse.TicketSnapshot.builder()
                .ticketAssetId(ticket.getTicketAssetId())
                .ticketCode(ticket.getTicketCode())
                .eventId(ticket.getEventId())
                .showtimeId(ticket.getShowtimeId())
                .ticketTypeName(ticket.getTicketTypeName())
                .zoneLabel(ticket.getZoneLabel())
                .seatLabel(ticket.getSeatLabel())
                .qrVersion(ticket.getQrVersion())
                .accessStatus(ticket.getAccessStatus())
                .usedAt(TimeMapper.toOffsetDateTime(ticket.getUsedAt()))
                .usedAtGateId(ticket.getUsedAtGateId())
                .allowedGateIds(parseGateIds(rawGatePolicy(ticket)))
                .build();
    }

    private String rawGatePolicy(TicketAccessState ticket) {
        if (hasText(ticket.getAllowedGateIds())) {
            return ticket.getAllowedGateIds();
        }
        return ticket.getGatePolicySnapshot();
    }

    private List<String> parseGateIds(String allowedGateIds) {
        Optional<List<String>> parsed = checkerAssignmentService.parseAllowedGateIds(allowedGateIds);
        return parsed.orElseThrow(() -> new CheckinBusinessException(
                ScanResult.WRONG_GATE,
                HttpStatus.BAD_REQUEST,
                "Ticket gate policy is malformed"
        ));
    }

    private String checksum(
            String packageId,
            OfflinePackageRequest request,
            Long checkerId,
            String deviceId,
            Instant issuedAt,
            Instant validUntil,
            String keyId,
            List<OfflinePackageResponse.TicketSnapshot> snapshots
    ) {
        Map<String, Object> checksumPayload = new LinkedHashMap<>();
        checksumPayload.put("packageId", packageId);
        checksumPayload.put("eventId", request.getEventId());
        checksumPayload.put("showtimeId", request.getShowtimeId());
        checksumPayload.put("gateId", normalize(request.getGateId()));
        checksumPayload.put("checkerId", checkerId);
        checksumPayload.put("deviceId", deviceId);
        checksumPayload.put("issuedAt", issuedAt.toString());
        checksumPayload.put("validUntil", validUntil.toString());
        checksumPayload.put("keyId", keyId);
        checksumPayload.put("ticketSnapshots", snapshots);

        try {
            byte[] json = objectMapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValueAsBytes(checksumPayload);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + toHex(digest.digest(json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize offline package checksum payload", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
