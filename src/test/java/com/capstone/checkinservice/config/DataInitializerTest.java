package com.capstone.checkinservice.config;

import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.CheckerAssignment;
import com.capstone.checkinservice.entity.CheckerDevice;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.CheckerAssignmentRepository;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataInitializerTest {

    @Test
    void runSeedsLocalCheckinDataIdempotently() {
        CheckinSeedProperties properties = new CheckinSeedProperties();
        CheckerAssignmentRepository assignmentRepository = mock(CheckerAssignmentRepository.class);
        CheckerDeviceRepository deviceRepository = mock(CheckerDeviceRepository.class);
        TicketAccessStateRepository ticketRepository = mock(TicketAccessStateRepository.class);
        CheckInLogRepository logRepository = mock(CheckInLogRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-05-02T00:00:00Z"), ZoneOffset.UTC);

        List<CheckerAssignment> assignments = new ArrayList<>();
        AtomicLong assignmentIds = new AtomicLong(1);
        when(assignmentRepository.findByCheckerIdAndEventIdAndShowtimeId(any(), any(), any()))
                .thenAnswer(invocation -> assignments.stream()
                        .filter(assignment -> assignment.getCheckerId().equals(invocation.getArgument(0))
                                && assignment.getEventId().equals(invocation.getArgument(1))
                                && assignment.getShowtimeId().equals(invocation.getArgument(2)))
                        .toList());
        when(assignmentRepository.save(any(CheckerAssignment.class))).thenAnswer(invocation -> {
            CheckerAssignment assignment = invocation.getArgument(0);
            if (assignment.getId() == null) {
                assignment.setId(assignmentIds.getAndIncrement());
                assignments.add(assignment);
            }
            return assignment;
        });

        Map<String, CheckerDevice> devices = new LinkedHashMap<>();
        when(deviceRepository.findByDeviceId(any())).thenAnswer(invocation ->
                Optional.ofNullable(devices.get(invocation.getArgument(0, String.class))));
        when(deviceRepository.save(any(CheckerDevice.class))).thenAnswer(invocation -> {
            CheckerDevice device = invocation.getArgument(0);
            devices.put(device.getDeviceId(), device);
            return device;
        });

        Map<Long, TicketAccessState> tickets = new LinkedHashMap<>();
        when(ticketRepository.findByTicketAssetId(any())).thenAnswer(invocation ->
                Optional.ofNullable(tickets.get(invocation.getArgument(0, Long.class))));
        when(ticketRepository.save(any(TicketAccessState.class))).thenAnswer(invocation -> {
            TicketAccessState ticket = invocation.getArgument(0);
            tickets.put(ticket.getTicketAssetId(), ticket);
            return ticket;
        });

        List<CheckInLog> logs = new ArrayList<>();
        when(logRepository.findFirstByTicketAssetIdAndScanResultOrderByScannedAtAsc(3002L, ScanResult.VALID_CHECKED_IN))
                .thenAnswer(invocation -> logs.stream().findFirst());
        when(logRepository.save(any(CheckInLog.class))).thenAnswer(invocation -> {
            CheckInLog log = invocation.getArgument(0);
            logs.add(log);
            return log;
        });

        DataInitializer initializer = new DataInitializer(
                properties,
                assignmentRepository,
                deviceRepository,
                ticketRepository,
                logRepository,
                clock
        );

        initializer.run();
        initializer.run();

        assertThat(assignments).hasSize(1);
        assertThat(devices).containsOnlyKeys("checker-device-b-02");
        assertThat(tickets).hasSize(6);
        assertThat(tickets.get(3001L).getAccessStatus()).isEqualTo(TicketAccessStatus.VALID);
        assertThat(tickets.get(3002L).getAccessStatus()).isEqualTo(TicketAccessStatus.USED);
        assertThat(tickets.get(3003L).getAccessStatus()).isEqualTo(TicketAccessStatus.LOCKED_RESALE);
        assertThat(tickets.get(3004L).getAccessStatus()).isEqualTo(TicketAccessStatus.CANCELLED);
        assertThat(tickets.get(3005L).getAllowedGateIds()).isEqualTo("[\"gate-a\"]");
        assertThat(tickets.get(3010L).getAccessStatus()).isEqualTo(TicketAccessStatus.VALID);
        assertThat(logs).hasSize(1);
    }
}
