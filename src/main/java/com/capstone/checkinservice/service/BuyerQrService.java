package com.capstone.checkinservice.service;

import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.crypto.QrTokenProperties;
import com.capstone.checkinservice.crypto.QrTokenSigner;
import com.capstone.checkinservice.dto.response.QrTokenResponse;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.exception.CheckinBusinessException;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BuyerQrService {
    private final TicketAccessStateRepository ticketAccessStateRepository;
    private final QrTokenSigner qrTokenSigner;
    private final QrTokenProperties qrTokenProperties;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    @Transactional(readOnly = true)
    public QrTokenResponse issueQrToken(Long ticketAssetId) {
        if (ticketAssetId == null) {
            throw new CheckinBusinessException(
                    ScanResult.TICKET_NOT_FOUND,
                    HttpStatus.NOT_FOUND,
                    "Ticket access state was not found"
            );
        }

        Long currentUserId = currentUserProvider.getCurrentUserId();
        TicketAccessState ticket = ticketAccessStateRepository.findByTicketAssetId(ticketAssetId)
                .orElseThrow(() -> new CheckinBusinessException(
                        ScanResult.TICKET_NOT_FOUND,
                        HttpStatus.NOT_FOUND,
                        "Ticket access state was not found"
                ));

        validateOwner(ticket, currentUserId);
        validateAccessStatus(ticket.getAccessStatus());

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(qrTokenProperties.getTtlSeconds());
        QrTokenPayload payload = new QrTokenPayload(
                ticket.getTicketAssetId(),
                ticket.getTicketCode(),
                ticket.getEventId(),
                ticket.getShowtimeId(),
                ticket.getQrVersion(),
                issuedAt,
                expiresAt,
                UUID.randomUUID().toString()
        );
        String qrToken = qrTokenSigner.sign(payload);

        return QrTokenResponse.builder()
                .ticketAssetId(ticket.getTicketAssetId())
                .ticketCode(ticket.getTicketCode())
                .eventId(ticket.getEventId())
                .showtimeId(ticket.getShowtimeId())
                .qrVersion(ticket.getQrVersion())
                .qrToken(qrToken)
                .issuedAt(toOffsetDateTime(issuedAt))
                .expiresAt(toOffsetDateTime(expiresAt))
                .refreshAfterSeconds(Math.toIntExact(qrTokenProperties.getRefreshAfterSeconds()))
                .build();
    }

    private void validateOwner(TicketAccessState ticket, Long currentUserId) {
        if (!ticket.getCurrentOwnerId().equals(currentUserId)) {
            throw new CheckinBusinessException(
                    ScanResult.OWNERSHIP_MISMATCH,
                    HttpStatus.FORBIDDEN,
                    "Authenticated user does not own this ticket"
            );
        }
    }

    private void validateAccessStatus(TicketAccessStatus accessStatus) {
        if (accessStatus == TicketAccessStatus.VALID) {
            return;
        }
        if (accessStatus == TicketAccessStatus.USED) {
            throw new CheckinBusinessException(
                    ScanResult.ALREADY_USED,
                    HttpStatus.CONFLICT,
                    "Ticket has already been checked in"
            );
        }
        if (accessStatus == TicketAccessStatus.LOCKED_RESALE) {
            throw new CheckinBusinessException(
                    ScanResult.LOCKED_RESALE,
                    HttpStatus.CONFLICT,
                    "Ticket is locked for resale"
            );
        }
        if (accessStatus == TicketAccessStatus.CANCELLED) {
            throw new CheckinBusinessException(
                    ScanResult.CANCELLED,
                    HttpStatus.CONFLICT,
                    "Ticket was cancelled, refunded, or revoked"
            );
        }

        throw new CheckinBusinessException(
                ScanResult.INVALID_QR,
                HttpStatus.BAD_REQUEST,
                "Ticket access status is invalid"
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
