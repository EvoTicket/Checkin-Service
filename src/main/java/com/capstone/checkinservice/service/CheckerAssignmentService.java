package com.capstone.checkinservice.service;

import com.capstone.checkinservice.dto.response.CheckerAssignmentResponse;
import com.capstone.checkinservice.entity.CheckerAssignment;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.CheckerAssignmentRepository;
import com.capstone.checkinservice.security.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckerAssignmentService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final CheckerAssignmentRepository checkerAssignmentRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final JwtUtil jwtUtil;

    @Transactional(readOnly = true)
    public CheckerAssignmentResponse getAssignmentsForCurrentChecker() {
        Long checkerId = jwtUtil.getDataFromAuth().userId();
        List<CheckerAssignmentResponse.Assignment> assignments = checkerAssignmentRepository
                .findByCheckerIdAndActiveTrue(checkerId)
                .stream()
                .filter(this::isCurrentlyValid)
                .map(this::toResponse)
                .toList();

        return CheckerAssignmentResponse.builder()
                .assignments(assignments)
                .build();
    }

    @Transactional(readOnly = true)
    public boolean isCheckerAssigned(Long checkerId, Long eventId, Long showtimeId, String gateId) {
        if (checkerId == null || eventId == null || showtimeId == null) {
            return false;
        }

        return checkerAssignmentRepository
                .findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(checkerId, eventId, showtimeId)
                .stream()
                .filter(this::isCurrentlyValid)
                .anyMatch(assignment -> gateMatches(assignment, gateId));
    }

    @Transactional(readOnly = true)
    public boolean isCheckerAssignedToShowtime(Long checkerId, Long eventId, Long showtimeId) {
        if (checkerId == null || eventId == null || showtimeId == null) {
            return false;
        }

        return checkerAssignmentRepository
                .findByCheckerIdAndEventIdAndShowtimeIdAndActiveTrue(checkerId, eventId, showtimeId)
                .stream()
                .anyMatch(this::isCurrentlyValid);
    }

    @Transactional(readOnly = true)
    public void assertCheckerAssigned(Long checkerId, Long eventId, Long showtimeId, String gateId) {
        if (!isCheckerAssigned(checkerId, eventId, showtimeId, gateId)) {
            throw new CheckinBusinessException(
                    ScanResult.UNAUTHORIZED_CHECKER,
                    HttpStatus.FORBIDDEN,
                    "Checker is not authorized for this event, showtime, or gate"
            );
        }
    }

    @Transactional(readOnly = true)
    public void assertCheckerAssignedToShowtime(Long checkerId, Long eventId, Long showtimeId) {
        if (!isCheckerAssignedToShowtime(checkerId, eventId, showtimeId)) {
            throw new CheckinBusinessException(
                    ScanResult.UNAUTHORIZED_CHECKER,
                    HttpStatus.FORBIDDEN,
                    "Checker is not authorized for this event or showtime"
            );
        }
    }

    private CheckerAssignmentResponse.Assignment toResponse(CheckerAssignment assignment) {
        return CheckerAssignmentResponse.Assignment.builder()
                .assignmentId(assignment.getId())
                .eventId(assignment.getEventId())
                .showtimeId(assignment.getShowtimeId())
                .gateIds(parseAllowedGateIds(assignment.getAllowedGateIds()).orElse(List.of()))
                .role(assignment.getRoleSnapshot())
                .validFrom(TimeMapper.toOffsetDateTime(assignment.getValidFrom()))
                .validUntil(TimeMapper.toOffsetDateTime(assignment.getValidUntil()))
                .build();
    }

    private boolean isCurrentlyValid(CheckerAssignment assignment) {
        if (!assignment.isActive()) {
            return false;
        }

        Instant now = clock.instant();
        Instant validFrom = assignment.getValidFrom();
        Instant validUntil = assignment.getValidUntil();

        return (validFrom == null || !now.isBefore(validFrom))
                && (validUntil == null || !now.isAfter(validUntil));
    }

    private boolean gateMatches(CheckerAssignment assignment, String gateId) {
        Optional<List<String>> parsedGateIds = parseAllowedGateIds(assignment.getAllowedGateIds());
        if (parsedGateIds.isEmpty()) {
            log.debug("Invalid allowedGateIds for checker assignment {}", assignment.getId());
            return false;
        }

        List<String> gateIds = parsedGateIds.get();
        if (gateIds.isEmpty()) {
            return true;
        }

        if (gateId == null || gateId.isBlank()) {
            return false;
        }

        String normalizedGateId = gateId.trim();
        return gateIds.stream().anyMatch(allowedGateId -> allowedGateId.equals(normalizedGateId));
    }

    Optional<List<String>> parseAllowedGateIds(String rawAllowedGateIds) {
        if (rawAllowedGateIds == null || rawAllowedGateIds.isBlank()) {
            return Optional.of(List.of());
        }

        String value = rawAllowedGateIds.trim();
        if (value.equals("[]")) {
            return Optional.of(List.of());
        }

        if (value.startsWith("[")) {
            try {
                return Optional.of(objectMapper.readValue(value, STRING_LIST).stream()
                        .filter(gateId -> gateId != null && !gateId.isBlank())
                        .map(String::trim)
                        .toList());
            } catch (JsonProcessingException e) {
                return Optional.empty();
            }
        }

        return Optional.of(Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(gateId -> !gateId.isBlank())
                .toList());
    }
}
