package com.capstone.checkinservice.service;

import com.capstone.checkinservice.config.CheckerDeviceProperties;
import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.crypto.QrTokenVerifier;
import com.capstone.checkinservice.crypto.exception.QrTokenException;
import com.capstone.checkinservice.dto.common.CheckInContextResponse;
import com.capstone.checkinservice.dto.common.ResultMessage;
import com.capstone.checkinservice.dto.common.TicketSummaryResponse;
import com.capstone.checkinservice.dto.event.TicketUsedEvent;
import com.capstone.checkinservice.dto.request.OnlineScanRequest;
import com.capstone.checkinservice.dto.response.ScanResultResponse;
import com.capstone.checkinservice.entity.CheckInLog;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.FailureReason;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.mapper.ScanResultMessageMapper;
import com.capstone.checkinservice.mapper.TimeMapper;
import com.capstone.checkinservice.producer.RedisStreamProducer;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckinScanService {
    private final TicketAccessStateRepository ticketAccessStateRepository;
    private final CheckInLogRepository checkInLogRepository;
    private final QrTokenVerifier qrTokenVerifier;
    private final CheckerAssignmentService checkerAssignmentService;
    private final CheckerDeviceValidationService checkerDeviceValidationService;
    private final CheckerDeviceProperties checkerDeviceProperties;
    private final RedisStreamProducer redisStreamProducer;
    private final Clock clock;
    private final JwtUtil jwtUtil;

    @Transactional
    public ScanResultResponse scanOnline(OnlineScanRequest request) {
        Long checkerId = jwtUtil.getDataFromAuth().userId();
        checkerAssignmentService.assertCheckerAssigned(
                checkerId,
                request.getEventId(),
                request.getShowtimeId(),
                request.getGateId()
        );
        checkerDeviceValidationService.validateForOnlineScan(checkerId, request.getDeviceId());

        Instant serverNow = clock.instant();
        Instant scannedAt = request.getScannedAt() == null ? serverNow : request.getScannedAt().toInstant();
        if (!isWithinClockSkew(scannedAt, serverNow)) {
            return response(ScanResult.DEVICE_TIME_INVALID, request, checkerId, null, null, null);
        }
        QrTokenPayload payload;
        try {
            payload = qrTokenVerifier.verify(request.getQrToken(), serverNow);
        } catch (QrTokenException exception) {
            QrTokenPayload exceptionPayload = exception.getPayload();
            if (exceptionPayload != null) {
                writeLog(request, checkerId, exceptionPayload, null, exception.getResultCode(), scannedAt, exception);
            }
            return response(exception.getResultCode(), request, checkerId, exceptionPayload, null, null);
        }

        Optional<TicketAccessState> ticketCandidate = ticketAccessStateRepository.findByTicketAssetId(payload.ticketAssetId());
        if (ticketCandidate.isEmpty()) {
            writeLog(request, checkerId, payload, null, ScanResult.TICKET_NOT_FOUND, scannedAt, null);
            return response(ScanResult.TICKET_NOT_FOUND, request, checkerId, payload, null, null);
        }

        TicketAccessState ticket = ticketCandidate.get();
        ScanResult deniedResult = validateTicket(request, payload, ticket);
        if (deniedResult != null) {
            writeLog(request, checkerId, payload, ticket, deniedResult, scannedAt, null);
            return response(deniedResult, request, checkerId, payload, ticket, null);
        }

        // Conditional update is the double-scan guard: only one transaction can move VALID to USED.
        int affectedRows = ticketAccessStateRepository.markUsedIfValid(
                payload.ticketAssetId(),
                payload.qrVersion(),
                serverNow,
                checkerId,
                normalize(request.getGateId())
        );

        if (affectedRows == 1) {
            ticket.setAccessStatus(TicketAccessStatus.USED);
            ticket.setUsedAt(serverNow);
            ticket.setUsedByCheckerId(checkerId);
            ticket.setUsedAtGateId(normalize(request.getGateId()));
            writeLog(request, checkerId, payload, ticket, ScanResult.VALID_CHECKED_IN, scannedAt, null);
            
            publishTicketUsedEvent(ticket);
            
            return response(ScanResult.VALID_CHECKED_IN, request, checkerId, payload, ticket, serverNow);
        }

        TicketAccessState currentState = ticketAccessStateRepository.findByTicketAssetId(payload.ticketAssetId())
                .orElse(null);
        ScanResult currentResult = currentState == null
                ? ScanResult.TICKET_NOT_FOUND
                : resultAfterFailedAtomicUpdate(payload, currentState);
        writeLog(request, checkerId, payload, currentState, currentResult, scannedAt, null);
        return response(currentResult, request, checkerId, payload, currentState, null);
    }

    private ScanResult validateTicket(
            OnlineScanRequest request,
            QrTokenPayload payload,
            TicketAccessState ticket
    ) {
        if (!payload.eventId().equals(request.getEventId())) {
            return ScanResult.WRONG_EVENT;
        }
        if (!payload.showtimeId().equals(request.getShowtimeId())) {
            return ScanResult.WRONG_SHOWTIME;
        }
        if (!ticket.getEventId().equals(request.getEventId())) {
            return ScanResult.WRONG_EVENT;
        }
        if (!ticket.getShowtimeId().equals(request.getShowtimeId())) {
            return ScanResult.WRONG_SHOWTIME;
        }
        if (!isGateAllowed(ticket, request.getGateId())) {
            return ScanResult.WRONG_GATE;
        }
        if (!ticket.getQrVersion().equals(payload.qrVersion())) {
            return ScanResult.INVALID_QR_VERSION;
        }
        return resultForAccessStatus(ticket.getAccessStatus());
    }

    private ScanResult resultAfterFailedAtomicUpdate(QrTokenPayload payload, TicketAccessState currentState) {
        if (!currentState.getQrVersion().equals(payload.qrVersion())) {
            return ScanResult.INVALID_QR_VERSION;
        }
        ScanResult statusResult = resultForAccessStatus(currentState.getAccessStatus());
        return statusResult == null ? ScanResult.ALREADY_USED : statusResult;
    }

    private ScanResult resultForAccessStatus(TicketAccessStatus status) {
        if (status == TicketAccessStatus.VALID) {
            return null;
        }
        if (status == TicketAccessStatus.USED) {
            return ScanResult.ALREADY_USED;
        }
        if (status == TicketAccessStatus.LOCKED_RESALE) {
            return ScanResult.LOCKED_RESALE;
        }
        if (status == TicketAccessStatus.CANCELLED) {
            return ScanResult.CANCELLED;
        }
        return ScanResult.INVALID_QR;
    }

    private boolean isGateAllowed(TicketAccessState ticket, String gateId) {
        String rawGatePolicy = hasText(ticket.getAllowedGateIds())
                ? ticket.getAllowedGateIds()
                : ticket.getGatePolicySnapshot();
        Optional<List<String>> allowedGateIds = checkerAssignmentService.parseAllowedGateIds(rawGatePolicy);
        if (allowedGateIds.isEmpty()) {
            log.debug("Invalid allowedGateIds for ticket access state {}", ticket.getTicketAssetId());
            return false;
        }

        List<String> gates = allowedGateIds.get();
        if (gates.isEmpty()) {
            return true;
        }

        String normalizedGateId = normalize(gateId);
        return normalizedGateId != null && gates.stream().anyMatch(normalizedGateId::equals);
    }

    private void publishTicketUsedEvent(TicketAccessState ticket) {
        try {
            TicketUsedEvent event = TicketUsedEvent.builder()
                    .ticketAssetId(ticket.getTicketAssetId())
                    .usedAt(ticket.getUsedAt())
                    .usedByCheckerId(ticket.getUsedByCheckerId())
                    .usedAtGateId(ticket.getUsedAtGateId())
                    .build();
            
            redisStreamProducer.sendMessage("ticket-used", event);
        } catch (Exception e) {
            log.error("Failed to publish ticket-used event for assetId: {}", ticket.getTicketAssetId(), e);
        }
    }

    private void writeLog(
            OnlineScanRequest request,
            Long checkerId,
            QrTokenPayload payload,
            TicketAccessState ticket,
            ScanResult result,
            Instant scannedAt,
            Exception exception
    ) {
        if (payload == null || payload.ticketAssetId() == null) {
            return;
        }

        checkInLogRepository.save(CheckInLog.builder()
                .ticketAssetId(payload.ticketAssetId())
                .eventId(request.getEventId())
                .showtimeId(request.getShowtimeId())
                .gateId(normalize(request.getGateId()))
                .checkerId(checkerId)
                .deviceId(normalize(request.getDeviceId()))
                .scanMode(ScanMode.ONLINE)
                .scanResult(result)
                .qrTokenId(payload.jti())
                .scannedAt(scannedAt)
                .failureReason(result == ScanResult.VALID_CHECKED_IN ? null : failureReason(result))
                .rawErrorCode(exception == null ? null : exception.getClass().getSimpleName())
                .build());
    }

    private FailureReason failureReason(ScanResult result) {
        return switch (result) {
            case QR_EXPIRED -> FailureReason.TOKEN_EXPIRED;
            case INVALID_QR, INVALID_SIGNATURE -> FailureReason.TOKEN_INVALID;
            case WRONG_GATE -> FailureReason.GATE_POLICY_FAILED;
            case UNAUTHORIZED_CHECKER -> FailureReason.ASSIGNMENT_INVALID;
            case DEVICE_TIME_INVALID -> FailureReason.DEVICE_TIME_INVALID;
            default -> FailureReason.TICKET_STATE_INVALID;
        };
    }

    private ScanResultResponse response(
            ScanResult result,
            OnlineScanRequest request,
            Long checkerId,
            QrTokenPayload payload,
            TicketAccessState ticket,
            Instant checkedInAt
    ) {
        ResultMessage message = ScanResultMessageMapper.toMessage(result);
        Long ticketAssetId = ticket != null ? ticket.getTicketAssetId() : payload == null ? null : payload.ticketAssetId();
        String ticketCode = ticket != null ? ticket.getTicketCode() : payload == null ? null : payload.ticketCode();
        Long eventId = ticket != null ? ticket.getEventId() : request.getEventId();
        Long showtimeId = ticket != null ? ticket.getShowtimeId() : request.getShowtimeId();

        return ScanResultResponse.builder()
                .resultCode(result)
                .resultMessage(message)
                .ticketAssetId(ticketAssetId)
                .ticketCode(ticketCode)
                .eventId(eventId)
                .showtimeId(showtimeId)
                .gateId(normalize(request.getGateId()))
                .checkedInAt(TimeMapper.toOffsetDateTime(checkedInAt))
                .checkerId(checkerId)
                .message(message.getMessage())
                .ticketSummary(toTicketSummary(ticket))
                .context(CheckInContextResponse.builder()
                        .gateId(normalize(request.getGateId()))
                        .checkerId(checkerId)
                        .deviceId(normalize(request.getDeviceId()))
                        .scannedAt(request.getScannedAt())
                        .scanMode(ScanMode.ONLINE)
                        .build())
                .firstCheckedInAt(ticket == null ? null : TimeMapper.toOffsetDateTime(ticket.getUsedAt()))
                .firstGateId(ticket == null ? null : ticket.getUsedAtGateId())
                .build();
    }

    private TicketSummaryResponse toTicketSummary(TicketAccessState ticket) {
        if (ticket == null) {
            return null;
        }
        return TicketSummaryResponse.builder()
                .ticketAssetId(ticket.getTicketAssetId())
                .ticketCode(ticket.getTicketCode())
                .eventId(ticket.getEventId())
                .showtimeId(ticket.getShowtimeId())
                .ticketTypeName(ticket.getTicketTypeName())
                .zoneLabel(ticket.getZoneLabel())
                .seatLabel(ticket.getSeatLabel())
                .accessStatus(ticket.getAccessStatus())
                .build();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isWithinClockSkew(Instant scannedAt, Instant serverNow) {
        long skewSeconds = checkerDeviceProperties.getClockSkewSeconds();
        return !scannedAt.isBefore(serverNow.minusSeconds(skewSeconds))
                && !scannedAt.isAfter(serverNow.plusSeconds(skewSeconds));
    }
}
