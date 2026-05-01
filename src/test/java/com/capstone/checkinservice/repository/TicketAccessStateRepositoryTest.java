package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RepositoryTest
class TicketAccessStateRepositoryTest {
    @Autowired
    private TicketAccessStateRepository repository;

    @Test
    void saveAndLoadByTicketAssetId() {
        TicketAccessState saved = repository.saveAndFlush(validTicket(1001L, 10L, 99L, 501L));

        assertThat(repository.findByTicketAssetId(1001L))
                .isPresent()
                .get()
                .extracting(TicketAccessState::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void ticketAssetIdUniquenessIsEnforced() {
        repository.saveAndFlush(validTicket(1001L, 10L, 99L, 501L));

        assertThatThrownBy(() -> repository.saveAndFlush(validTicket(1001L, 11L, 99L, 501L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByCurrentOwnerIdWorks() {
        repository.saveAndFlush(validTicket(1001L, 10L, 99L, 501L));
        repository.saveAndFlush(validTicket(1002L, 10L, 99L, 501L));
        repository.saveAndFlush(validTicket(1003L, 20L, 99L, 501L));

        assertThat(repository.findByCurrentOwnerId(10L))
                .extracting(TicketAccessState::getTicketAssetId)
                .containsExactlyInAnyOrder(1001L, 1002L);
    }

    @Test
    void findByEventIdAndShowtimeIdAndAccessStatusWorks() {
        repository.saveAndFlush(validTicket(1001L, 10L, 99L, 501L));
        repository.saveAndFlush(validTicket(1002L, 20L, 99L, 501L));
        repository.saveAndFlush(validTicket(1003L, 30L, 99L, 501L, TicketAccessStatus.USED));
        repository.saveAndFlush(validTicket(1004L, 40L, 100L, 501L));

        assertThat(repository.findByEventIdAndShowtimeIdAndAccessStatus(99L, 501L, TicketAccessStatus.VALID))
                .extracting(TicketAccessState::getTicketAssetId)
                .containsExactlyInAnyOrder(1001L, 1002L);
    }

    @Test
    void markUsedIfValidSucceedsOnceForValidTicket() {
        repository.saveAndFlush(validTicket(1001L, 10L, 99L, 501L));
        Instant usedAt = Instant.parse("2026-05-01T10:00:00Z");

        int firstUpdate = repository.markUsedIfValid(1001L, 1, usedAt, 7001L, "A1");
        int secondUpdate = repository.markUsedIfValid(1001L, 1, usedAt.plusSeconds(1), 7002L, "A2");

        TicketAccessState updated = repository.findByTicketAssetId(1001L).orElseThrow();
        assertThat(firstUpdate).isEqualTo(1);
        assertThat(secondUpdate).isZero();
        assertThat(updated.getAccessStatus()).isEqualTo(TicketAccessStatus.USED);
        assertThat(updated.getUsedAt()).isNotNull();
        assertThat(updated.getUsedByCheckerId()).isEqualTo(7001L);
        assertThat(updated.getUsedAtGateId()).isEqualTo("A1");
    }

    @Test
    void markUsedIfValidReturnsZeroForQrVersionMismatch() {
        repository.saveAndFlush(validTicket(1001L, 10L, 99L, 501L));

        int updated = repository.markUsedIfValid(1001L, 99, Instant.now(), 7001L, "A1");

        assertThat(updated).isZero();
        assertThat(repository.findByTicketAssetId(1001L).orElseThrow().getAccessStatus())
                .isEqualTo(TicketAccessStatus.VALID);
    }

    @Test
    void markUsedIfValidReturnsZeroForInvalidAccessStatuses() {
        repository.saveAndFlush(validTicket(1001L, 10L, 99L, 501L, TicketAccessStatus.LOCKED_RESALE));
        repository.saveAndFlush(validTicket(1002L, 10L, 99L, 501L, TicketAccessStatus.USED));
        repository.saveAndFlush(validTicket(1003L, 10L, 99L, 501L, TicketAccessStatus.CANCELLED));

        assertThat(repository.markUsedIfValid(1001L, 1, Instant.now(), 7001L, "A1")).isZero();
        assertThat(repository.markUsedIfValid(1002L, 1, Instant.now(), 7001L, "A1")).isZero();
        assertThat(repository.markUsedIfValid(1003L, 1, Instant.now(), 7001L, "A1")).isZero();
    }

    private TicketAccessState validTicket(Long ticketAssetId, Long ownerId, Long eventId, Long showtimeId) {
        return validTicket(ticketAssetId, ownerId, eventId, showtimeId, TicketAccessStatus.VALID);
    }

    private TicketAccessState validTicket(
            Long ticketAssetId,
            Long ownerId,
            Long eventId,
            Long showtimeId,
            TicketAccessStatus accessStatus
    ) {
        return TicketAccessState.builder()
                .ticketAssetId(ticketAssetId)
                .ticketCode("TCK-" + ticketAssetId)
                .orderId(500L)
                .eventId(eventId)
                .showtimeId(showtimeId)
                .ticketTypeName("VIP")
                .currentOwnerId(ownerId)
                .qrVersion(1)
                .accessStatus(accessStatus)
                .allowedGateIds("[\"A1\"]")
                .build();
    }
}
