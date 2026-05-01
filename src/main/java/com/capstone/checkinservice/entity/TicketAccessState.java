package com.capstone.checkinservice.entity;

import com.capstone.checkinservice.entity.base.BaseTimeEntity;
import com.capstone.checkinservice.enums.TicketAccessStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "ticket_access_state",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ticket_access_state_ticket_asset_id", columnNames = "ticket_asset_id")
        },
        indexes = {
                @Index(name = "idx_ticket_access_state_owner", columnList = "current_owner_id"),
                @Index(name = "idx_ticket_access_state_showtime", columnList = "event_id, showtime_id"),
                @Index(name = "idx_ticket_access_state_status", columnList = "access_status"),
                @Index(name = "idx_ticket_access_state_showtime_status",
                        columnList = "event_id, showtime_id, access_status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAccessState extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_asset_id", nullable = false)
    private Long ticketAssetId;

    @Column(name = "ticket_code")
    private String ticketCode;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "showtime_id", nullable = false)
    private Long showtimeId;

    @Column(name = "ticket_type_name", nullable = false)
    private String ticketTypeName;

    @Column(name = "zone_label")
    private String zoneLabel;

    @Column(name = "seat_label")
    private String seatLabel;

    @Column(name = "current_owner_id", nullable = false)
    private Long currentOwnerId;

    @Column(name = "qr_version", nullable = false)
    private Integer qrVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false)
    private TicketAccessStatus accessStatus;

    @Column(name = "allowed_gate_ids", columnDefinition = "TEXT")
    private String allowedGateIds;

    @Column(name = "gate_policy_snapshot", columnDefinition = "TEXT")
    private String gatePolicySnapshot;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_checker_id")
    private Long usedByCheckerId;

    @Column(name = "used_at_gate_id")
    private String usedAtGateId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
