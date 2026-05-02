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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuyerQrServiceTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-05-01T10:00:00Z");

    @Mock
    private TicketAccessStateRepository ticketAccessStateRepository;

    @Mock
    private QrTokenSigner qrTokenSigner;

    @Mock
    private CurrentUserProvider currentUserProvider;

    private BuyerQrService buyerQrService;

    @BeforeEach
    void setUp() {
        QrTokenProperties properties = new QrTokenProperties(
                "evoticket-qr",
                60,
                20,
                0,
                "SHA256withECDSA"
        );
        buyerQrService = new BuyerQrService(
                ticketAccessStateRepository,
                qrTokenSigner,
                properties,
                currentUserProvider,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void issueQrToken_successForValidCurrentOwner() {
        TicketAccessState ticket = ticket(TicketAccessStatus.VALID);
        when(currentUserProvider.getCurrentUserId()).thenReturn(10L);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket));
        when(qrTokenSigner.sign(any(QrTokenPayload.class))).thenReturn("signed-qr-token");

        QrTokenResponse response = buyerQrService.issueQrToken(12345L);

        ArgumentCaptor<QrTokenPayload> payloadCaptor = ArgumentCaptor.forClass(QrTokenPayload.class);
        verify(qrTokenSigner).sign(payloadCaptor.capture());
        QrTokenPayload payload = payloadCaptor.getValue();

        assertThat(response.getTicketAssetId()).isEqualTo(12345L);
        assertThat(response.getTicketCode()).isEqualTo("TCK-12345");
        assertThat(response.getEventId()).isEqualTo(99L);
        assertThat(response.getShowtimeId()).isEqualTo(501L);
        assertThat(response.getQrVersion()).isEqualTo(3);
        assertThat(response.getQrToken()).isEqualTo("signed-qr-token");
        assertThat(response.getRefreshAfterSeconds()).isEqualTo(20);

        assertThat(payload.ticketAssetId()).isEqualTo(12345L);
        assertThat(payload.ticketCode()).isEqualTo("TCK-12345");
        assertThat(payload.eventId()).isEqualTo(99L);
        assertThat(payload.showtimeId()).isEqualTo(501L);
        assertThat(payload.qrVersion()).isEqualTo(3);
        assertThat(payload.issuedAt()).isEqualTo(FIXED_NOW);
        assertThat(payload.expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(60));
        assertThat(payload.jti()).isNotBlank();
    }

    @Test
    void issueQrToken_rejectsNonOwner() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(99L);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.VALID)));

        assertBusinessException(
                () -> buyerQrService.issueQrToken(12345L),
                ScanResult.OWNERSHIP_MISMATCH,
                HttpStatus.FORBIDDEN
        );
        verify(qrTokenSigner, never()).sign(any());
    }

    @Test
    void issueQrToken_rejectsTicketNotFound() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(10L);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.empty());

        assertBusinessException(
                () -> buyerQrService.issueQrToken(12345L),
                ScanResult.TICKET_NOT_FOUND,
                HttpStatus.NOT_FOUND
        );
        verify(qrTokenSigner, never()).sign(any());
    }

    @Test
    void issueQrToken_rejectsUsedTicket() {
        assertStatusRejected(TicketAccessStatus.USED, ScanResult.ALREADY_USED);
    }

    @Test
    void issueQrToken_rejectsLockedResaleTicket() {
        assertStatusRejected(TicketAccessStatus.LOCKED_RESALE, ScanResult.LOCKED_RESALE);
    }

    @Test
    void issueQrToken_rejectsCancelledTicket() {
        assertStatusRejected(TicketAccessStatus.CANCELLED, ScanResult.CANCELLED);
    }

    @Test
    void issueQrToken_doesNotPersistRawToken() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(10L);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.VALID)));
        when(qrTokenSigner.sign(any(QrTokenPayload.class))).thenReturn("signed-qr-token");

        buyerQrService.issueQrToken(12345L);

        verify(ticketAccessStateRepository).findByTicketAssetId(12345L);
        verifyNoMoreInteractions(ticketAccessStateRepository);
    }

    @Test
    void issueQrToken_payloadExpiresAtUsesConfiguredTtl() {
        when(currentUserProvider.getCurrentUserId()).thenReturn(10L);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(TicketAccessStatus.VALID)));
        when(qrTokenSigner.sign(any(QrTokenPayload.class))).thenReturn("signed-qr-token");

        buyerQrService.issueQrToken(12345L);

        ArgumentCaptor<QrTokenPayload> payloadCaptor = ArgumentCaptor.forClass(QrTokenPayload.class);
        verify(qrTokenSigner).sign(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().expiresAt()).isEqualTo(FIXED_NOW.plusSeconds(60));
    }

    private void assertStatusRejected(TicketAccessStatus status, ScanResult result) {
        when(currentUserProvider.getCurrentUserId()).thenReturn(10L);
        when(ticketAccessStateRepository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket(status)));

        assertBusinessException(
                () -> buyerQrService.issueQrToken(12345L),
                result,
                HttpStatus.CONFLICT
        );
        verify(qrTokenSigner, never()).sign(any());
    }

    private TicketAccessState ticket(TicketAccessStatus status) {
        return TicketAccessState.builder()
                .ticketAssetId(12345L)
                .ticketCode("TCK-12345")
                .eventId(99L)
                .showtimeId(501L)
                .ticketTypeName("VIP")
                .currentOwnerId(10L)
                .qrVersion(3)
                .accessStatus(status)
                .build();
    }

    private void assertBusinessException(
            Runnable action,
            ScanResult result,
            HttpStatus status
    ) {
        assertThatExceptionOfType(CheckinBusinessException.class)
                .isThrownBy(action::run)
                .satisfies(exception -> {
                    assertThat(exception.getResultCode()).isEqualTo(result);
                    assertThat(exception.getStatus()).isEqualTo(status);
                });
    }
}
