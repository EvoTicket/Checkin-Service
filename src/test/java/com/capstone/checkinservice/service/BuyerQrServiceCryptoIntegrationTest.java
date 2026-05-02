package com.capstone.checkinservice.service;

import com.capstone.checkinservice.crypto.QrTokenPayload;
import com.capstone.checkinservice.crypto.QrTokenProperties;
import com.capstone.checkinservice.crypto.QrTokenSigner;
import com.capstone.checkinservice.crypto.QrTokenVerifier;
import com.capstone.checkinservice.crypto.key.TestQrKeyProvider;
import com.capstone.checkinservice.dto.response.QrTokenResponse;
import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BuyerQrServiceCryptoIntegrationTest {
    @Test
    void returnedTokenCanBeVerifiedByQrTokenVerifier() {
        Instant now = Instant.parse("2026-05-01T10:00:00Z");
        QrTokenProperties properties = new QrTokenProperties();
        TestQrKeyProvider keyProvider = TestQrKeyProvider.single("kid-main");
        TicketAccessStateRepository repository = mock(TicketAccessStateRepository.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);

        when(currentUserProvider.getCurrentUserId()).thenReturn(10L);
        when(repository.findByTicketAssetId(12345L)).thenReturn(Optional.of(ticket()));

        BuyerQrService service = new BuyerQrService(
                repository,
                new QrTokenSigner(keyProvider, properties),
                properties,
                currentUserProvider,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        QrTokenResponse response = service.issueQrToken(12345L);
        QrTokenPayload verified = new QrTokenVerifier(keyProvider, properties)
                .verify(response.getQrToken(), now.plusSeconds(1));

        assertThat(verified.ticketAssetId()).isEqualTo(12345L);
        assertThat(verified.ticketCode()).isEqualTo("TCK-12345");
        assertThat(verified.eventId()).isEqualTo(99L);
        assertThat(verified.showtimeId()).isEqualTo(501L);
        assertThat(verified.qrVersion()).isEqualTo(3);
        assertThat(verified.issuedAt()).isEqualTo(now);
        assertThat(verified.expiresAt()).isEqualTo(now.plusSeconds(properties.getTtlSeconds()));
        assertThat(verified.jti()).isNotBlank();
    }

    private TicketAccessState ticket() {
        return TicketAccessState.builder()
                .ticketAssetId(12345L)
                .ticketCode("TCK-12345")
                .eventId(99L)
                .showtimeId(501L)
                .ticketTypeName("VIP")
                .currentOwnerId(10L)
                .qrVersion(3)
                .accessStatus(TicketAccessStatus.VALID)
                .build();
    }
}
