package com.capstone.checkinservice.config;

import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.CheckerAssignment;
import com.capstone.checkinservice.entity.CheckerDevice;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.CheckerAssignmentRepository;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
@Profile({"local", "dev", "test"})
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    private static final String VIP_STANDING = "VIP Standing";
    private static final String ZONE_A = "Zone A";

    private final CheckinSeedProperties seedProperties;
    private final CheckerAssignmentRepository checkerAssignmentRepository;
    private final CheckerDeviceRepository checkerDeviceRepository;
    private final TicketAccessStateRepository ticketAccessStateRepository;
    private final CheckInLogRepository checkInLogRepository;
    private final Clock clock;

    @Override
    @Transactional
    public void run(String... args) {
        seedCheckerAssignment();
        seedCheckerDevice();
        seedTicketAccessStates();
        seedUsedTicketLog();
    }

    private void seedCheckerAssignment() {
        String gateIds = gateJson(seedProperties.getGateId());
        CheckerAssignment assignment = checkerAssignmentRepository
                .findByCheckerIdAndEventIdAndShowtimeId(
                        seedProperties.getCheckerId(),
                        seedProperties.getEventId(),
                        seedProperties.getShowtimeId()
                )
                .stream()
                .filter(candidate -> gateMatches(candidate.getAllowedGateIds(), seedProperties.getGateId()))
                .findFirst()
                .orElseGet(CheckerAssignment::new);

        assignment.setCheckerId(seedProperties.getCheckerId());
        assignment.setEventId(seedProperties.getEventId());
        assignment.setShowtimeId(seedProperties.getShowtimeId());
        assignment.setAllowedGateIds(gateIds);
        assignment.setRoleSnapshot("CHECKER");
        assignment.setValidFrom(Instant.parse("2026-01-01T00:00:00Z"));
        assignment.setValidUntil(Instant.parse("2036-01-01T00:00:00Z"));
        assignment.setActive(true);

        checkerAssignmentRepository.save(assignment);
        log.info("Seeded local checker assignment for checker {} event {} showtime {} gate {}",
                seedProperties.getCheckerId(),
                seedProperties.getEventId(),
                seedProperties.getShowtimeId(),
                seedProperties.getGateId());
    }

    private void seedCheckerDevice() {
        Instant now = clock.instant();
        CheckerDevice device = checkerDeviceRepository.findByDeviceId(seedProperties.getDeviceId())
                .orElseGet(CheckerDevice::new);

        device.setDeviceId(seedProperties.getDeviceId());
        device.setCheckerId(seedProperties.getCheckerId());
        device.setDeviceName("Local Demo Checker Device");
        device.setPlatform("WEB_PWA");
        device.setUserAgent("local-demo");
        device.setAppVersion("local-demo");
        if (device.getRegisteredAt() == null) {
            device.setRegisteredAt(now);
        }
        device.setTrusted(true);
        if (device.getTrustedAt() == null) {
            device.setTrustedAt(now);
        }
        device.setLastSeenAt(now);
        device.setRevokedAt(null);

        checkerDeviceRepository.save(device);
    }

    private void seedTicketAccessStates() {
        upsertTicket(3001L, "TCK-LOCAL-3001", TicketAccessStatus.VALID, gateJson(seedProperties.getGateId()), null);
        upsertTicket(3002L, "TCK-LOCAL-3002", TicketAccessStatus.USED, gateJson(seedProperties.getGateId()), usedAt());
        upsertTicket(3003L, "TCK-LOCAL-3003", TicketAccessStatus.LOCKED_RESALE, gateJson(seedProperties.getGateId()), null);
        upsertTicket(3004L, "TCK-LOCAL-3004", TicketAccessStatus.CANCELLED, gateJson(seedProperties.getGateId()), null);
        upsertTicket(3005L, "TCK-LOCAL-3005", TicketAccessStatus.VALID, gateJson(seedProperties.getWrongGateId()), null);
        upsertTicket(3010L, "TCK-LOCAL-3010", TicketAccessStatus.VALID, gateJson(seedProperties.getGateId()), null);
    }

    private void upsertTicket(
            Long ticketAssetId,
            String ticketCode,
            TicketAccessStatus accessStatus,
            String allowedGateIds,
            Instant usedAt
    ) {
        TicketAccessState ticket = ticketAccessStateRepository.findByTicketAssetId(ticketAssetId)
                .orElseGet(TicketAccessState::new);

        ticket.setTicketAssetId(ticketAssetId);
        ticket.setTicketCode(ticketCode);
        ticket.setEventId(seedProperties.getEventId());
        ticket.setShowtimeId(seedProperties.getShowtimeId());
        ticket.setCurrentOwnerId(seedProperties.getBuyerId());
        ticket.setAccessStatus(accessStatus);
        ticket.setQrVersion(1);
        ticket.setTicketTypeName(VIP_STANDING);
        ticket.setZoneLabel(ZONE_A);
        ticket.setSeatLabel(null);
        ticket.setAllowedGateIds(allowedGateIds);
        ticket.setGatePolicySnapshot(allowedGateIds);
        ticket.setUsedAt(usedAt);
        ticket.setUsedByCheckerId(usedAt == null ? null : seedProperties.getCheckerId());
        ticket.setUsedAtGateId(usedAt == null ? null : seedProperties.getGateId());

        ticketAccessStateRepository.save(ticket);
    }

    private void seedUsedTicketLog() {
        boolean logExists = checkInLogRepository
                .findFirstByTicketAssetIdAndScanResultOrderByScannedAtAsc(3002L, ScanResult.VALID_CHECKED_IN)
                .isPresent();
        if (logExists) {
            return;
        }

        checkInLogRepository.save(CheckInLog.builder()
                .ticketAssetId(3002L)
                .eventId(seedProperties.getEventId())
                .showtimeId(seedProperties.getShowtimeId())
                .gateId(seedProperties.getGateId())
                .checkerId(seedProperties.getCheckerId())
                .deviceId(seedProperties.getDeviceId())
                .scanMode(ScanMode.ONLINE)
                .scanResult(ScanResult.VALID_CHECKED_IN)
                .qrTokenId("local-seed-used-ticket")
                .scannedAt(usedAt())
                .build());
    }

    private String gateJson(String gateId) {
        return "[\"" + gateId + "\"]";
    }

    private boolean gateMatches(String rawGateIds, String gateId) {
        if (rawGateIds == null || rawGateIds.isBlank() || gateId == null || gateId.isBlank()) {
            return false;
        }

        String normalizedGateId = gateId.trim();
        String value = rawGateIds.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.contains("\"" + normalizedGateId + "\"");
        }

        for (String candidate : value.split(",")) {
            if (normalizedGateId.equals(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private Instant usedAt() {
        return Instant.parse("2026-05-01T10:00:00Z");
    }
}
