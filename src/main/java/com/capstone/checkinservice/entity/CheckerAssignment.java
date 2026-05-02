package com.capstone.checkinservice.entity;

import com.capstone.checkinservice.entity.base.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "checker_assignment",
        indexes = {
                @Index(name = "idx_checker_assignment_checker", columnList = "checker_id"),
                @Index(name = "idx_checker_assignment_showtime", columnList = "event_id, showtime_id"),
                @Index(name = "idx_checker_assignment_active_scope",
                        columnList = "checker_id, event_id, showtime_id, active")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckerAssignment extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "showtime_id", nullable = false)
    private Long showtimeId;

    @Column(name = "allowed_gate_ids", columnDefinition = "TEXT")
    private String allowedGateIds;

    @Column(name = "role_snapshot")
    private String roleSnapshot;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "active", nullable = false)
    private boolean active;
}
