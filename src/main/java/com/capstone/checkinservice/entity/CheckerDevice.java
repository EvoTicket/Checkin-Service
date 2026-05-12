package com.capstone.checkinservice.entity;

import com.capstone.checkinservice.entity.base.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "checker_device",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_checker_device_device_id", columnNames = "device_id")
        },
        indexes = {
                @Index(name = "idx_checker_device_checker", columnList = "checker_id"),
                @Index(name = "idx_checker_device_trusted", columnList = "checker_id, trusted")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckerDevice extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "platform")
    private String platform;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "app_version")
    private String appVersion;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "trusted_at")
    private Instant trustedAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "trusted", nullable = false)
    private boolean trusted;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected void ensureRegisteredAt() {
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
    }
}
