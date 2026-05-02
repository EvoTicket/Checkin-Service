package com.capstone.checkinservice.entity;

import com.capstone.checkinservice.enums.ConflictStatus;
import com.capstone.checkinservice.enums.FailureReason;
import com.capstone.checkinservice.enums.ScanMode;
import com.capstone.checkinservice.enums.ScanResult;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "check_in_log",
        indexes = {
                @Index(name = "idx_check_in_log_ticket_asset", columnList = "ticket_asset_id"),
                @Index(name = "idx_check_in_log_showtime", columnList = "event_id, showtime_id"),
                @Index(name = "idx_check_in_log_checker", columnList = "checker_id"),
                @Index(name = "idx_check_in_log_device", columnList = "device_id"),
                @Index(name = "idx_check_in_log_qr_token", columnList = "qr_token_id"),
                @Index(name = "idx_check_in_log_scanned_at", columnList = "scanned_at"),
                @Index(name = "idx_check_in_log_scan_result", columnList = "scan_result")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckInLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_asset_id", nullable = false)
    private Long ticketAssetId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "showtime_id", nullable = false)
    private Long showtimeId;

    @Column(name = "gate_id")
    private String gateId;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "device_id")
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_mode", nullable = false)
    private ScanMode scanMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "scan_result", nullable = false)
    private ScanResult scanResult;

    @Column(name = "qr_token_id")
    private String qrTokenId;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_status")
    private ConflictStatus conflictStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private FailureReason failureReason;

    @Column(name = "raw_error_code")
    private String rawErrorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
