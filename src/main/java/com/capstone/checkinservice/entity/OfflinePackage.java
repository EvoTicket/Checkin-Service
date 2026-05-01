package com.capstone.checkinservice.entity;

import com.capstone.checkinservice.enums.OfflinePackageStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "offline_package",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_offline_package_package_id", columnNames = "package_id")
        },
        indexes = {
                @Index(name = "idx_offline_package_checker_device", columnList = "checker_id, device_id"),
                @Index(name = "idx_offline_package_scope", columnList = "event_id, showtime_id, gate_id"),
                @Index(name = "idx_offline_package_valid_until", columnList = "valid_until")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflinePackage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false)
    private String packageId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "showtime_id", nullable = false)
    private Long showtimeId;

    @Column(name = "gate_id")
    private String gateId;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "ticket_count", nullable = false)
    private Integer ticketCount;

    @Column(name = "checksum", nullable = false)
    private String checksum;

    @Column(name = "package_signature", nullable = false, columnDefinition = "TEXT")
    private String packageSignature;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OfflinePackageStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
