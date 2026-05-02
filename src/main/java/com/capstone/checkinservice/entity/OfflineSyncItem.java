package com.capstone.checkinservice.entity;

import com.capstone.checkinservice.enums.FailureReason;
import com.capstone.checkinservice.enums.ScanResult;
import com.capstone.checkinservice.enums.SyncResult;
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
        name = "offline_sync_item",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_offline_sync_item_package_local_scan",
                        columnNames = {"package_id", "local_scan_id"}
                )
        },
        indexes = {
                @Index(name = "idx_offline_sync_item_ticket_asset", columnList = "ticket_asset_id"),
                @Index(name = "idx_offline_sync_item_qr_token", columnList = "qr_token_id"),
                @Index(name = "idx_offline_sync_item_result_synced", columnList = "sync_result, synced_at"),
                @Index(name = "idx_offline_sync_item_checker_device", columnList = "checker_id, device_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfflineSyncItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false)
    private String packageId;

    @Column(name = "local_scan_id", nullable = false)
    private String localScanId;

    @Column(name = "ticket_asset_id", nullable = false)
    private Long ticketAssetId;

    @Column(name = "qr_token_id")
    private String qrTokenId;

    @Column(name = "checker_id", nullable = false)
    private Long checkerId;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "showtime_id", nullable = false)
    private Long showtimeId;

    @Column(name = "gate_id")
    private String gateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "local_result_code", nullable = false)
    private ScanResult localResultCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_result", nullable = false)
    private SyncResult syncResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "server_scan_result")
    private ScanResult serverScanResult;

    @Column(name = "scanned_at", nullable = false)
    private Instant scannedAt;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    @Column(name = "conflict_details", columnDefinition = "TEXT")
    private String conflictDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason")
    private FailureReason failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
