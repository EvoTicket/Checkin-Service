package com.capstone.checkinservice.repository;

import com.capstone.checkinservice.entity.TicketAccessState;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TicketAccessStateRepository extends JpaRepository<TicketAccessState, Long> {
    Optional<TicketAccessState> findByTicketAssetId(Long ticketAssetId);

    List<TicketAccessState> findByCurrentOwnerId(Long currentOwnerId);

    List<TicketAccessState> findByEventIdAndShowtimeIdAndAccessStatus(
            Long eventId,
            Long showtimeId,
            TicketAccessStatus accessStatus
    );

    List<TicketAccessState> findByEventIdAndShowtimeId(Long eventId, Long showtimeId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TicketAccessState t
            SET t.accessStatus = com.capstone.checkinservice.enums.TicketAccessStatus.USED,
                t.usedAt = :usedAt,
                t.usedByCheckerId = :usedByCheckerId,
                t.usedAtGateId = :usedAtGateId,
                t.updatedAt = :usedAt,
                t.version = t.version + 1
            WHERE t.ticketAssetId = :ticketAssetId
              AND t.qrVersion = :qrVersion
              AND t.accessStatus = com.capstone.checkinservice.enums.TicketAccessStatus.VALID
            """)
    int markUsedIfValid(
            @Param("ticketAssetId") Long ticketAssetId,
            @Param("qrVersion") Integer qrVersion,
            @Param("usedAt") Instant usedAt,
            @Param("usedByCheckerId") Long usedByCheckerId,
            @Param("usedAtGateId") String usedAtGateId
    );
}
