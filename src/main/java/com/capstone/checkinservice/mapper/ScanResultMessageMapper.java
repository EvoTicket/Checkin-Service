package com.capstone.checkinservice.mapper;

import com.capstone.checkinservice.dto.common.ResultMessage;
import com.capstone.checkinservice.enums.ScanResult;

public final class ScanResultMessageMapper {
    private static final String SUCCESS = "SUCCESS";
    private static final String INFO = "INFO";
    private static final String WARNING = "WARNING";
    private static final String ERROR = "ERROR";

    private ScanResultMessageMapper() {
    }

    public static ResultMessage toMessage(ScanResult result) {
        if (result == null) {
            return message(
                    null,
                    "Unknown result",
                    "The scan result could not be determined.",
                    ERROR,
                    false,
                    true
            );
        }

        return switch (result) {
            case VALID_CHECKED_IN -> message(
                    result,
                    "Checked in",
                    "Ticket is valid and has been checked in.",
                    SUCCESS,
                    true,
                    false
            );
            case OFFLINE_ACCEPTED_PENDING_SYNC -> message(
                    result,
                    "Accepted offline",
                    "Ticket was accepted on this device and is pending server sync.",
                    SUCCESS,
                    true,
                    false
            );
            case ALREADY_USED -> message(
                    result,
                    "Already used",
                    "This ticket has already been checked in.",
                    WARNING,
                    false,
                    true
            );
            case QR_EXPIRED -> message(
                    result,
                    "QR expired",
                    "The QR code has expired. Ask the buyer to refresh it.",
                    INFO,
                    false,
                    false
            );
            case INVALID_QR -> message(
                    result,
                    "Invalid QR",
                    "The QR code format is invalid.",
                    ERROR,
                    false,
                    false
            );
            case INVALID_SIGNATURE -> message(
                    result,
                    "Invalid signature",
                    "The QR code signature could not be verified.",
                    ERROR,
                    false,
                    false
            );
            case INVALID_QR_VERSION -> message(
                    result,
                    "QR no longer valid",
                    "The QR code is no longer valid for the current ticket owner.",
                    WARNING,
                    false,
                    false
            );
            case WRONG_EVENT -> message(
                    result,
                    "Wrong event",
                    "This ticket does not belong to the selected event.",
                    WARNING,
                    false,
                    false
            );
            case WRONG_SHOWTIME -> message(
                    result,
                    "Wrong showtime",
                    "This ticket does not belong to the selected showtime.",
                    WARNING,
                    false,
                    false
            );
            case WRONG_GATE -> message(
                    result,
                    "Wrong gate",
                    "This ticket is not valid for the selected gate.",
                    WARNING,
                    false,
                    false
            );
            case LOCKED_RESALE -> message(
                    result,
                    "Ticket locked",
                    "This ticket is locked for resale or transfer and cannot be checked in.",
                    WARNING,
                    false,
                    false
            );
            case CANCELLED -> message(
                    result,
                    "Ticket cancelled",
                    "This ticket was cancelled, refunded, or revoked.",
                    ERROR,
                    false,
                    false
            );
            case SYNC_ACCEPTED -> message(
                    result,
                    "Sync accepted",
                    "Offline scan was accepted by the server.",
                    SUCCESS,
                    false,
                    false
            );
            case SYNC_REJECTED -> message(
                    result,
                    "Sync rejected",
                    "Offline scan was rejected by current ticket state.",
                    WARNING,
                    false,
                    false
            );
            case SYNC_FAILED -> message(
                    result,
                    "Sync failed",
                    "Offline scan could not be synced. Retry may be needed.",
                    ERROR,
                    false,
                    false
            );
            case SYNC_CONFLICT -> message(
                    result,
                    "Sync conflict",
                    "Offline scan conflicts with server state and needs support review.",
                    ERROR,
                    false,
                    true
            );
            case OWNERSHIP_MISMATCH -> message(
                    result,
                    "Ticket not available",
                    "This ticket is not owned by the authenticated user.",
                    ERROR,
                    false,
                    false
            );
            case UNAUTHORIZED_CHECKER -> message(
                    result,
                    "Unauthorized checker",
                    "This checker is not assigned to the selected event, showtime, or gate.",
                    ERROR,
                    false,
                    false
            );
            case TICKET_NOT_FOUND -> message(
                    result,
                    "Ticket not found",
                    "Ticket access state was not found.",
                    ERROR,
                    false,
                    true
            );
            case OFFLINE_PACKAGE_EXPIRED -> message(
                    result,
                    "Offline package expired",
                    "The offline package has expired. Reconnect and download a new package.",
                    WARNING,
                    false,
                    false
            );
            case OFFLINE_PACKAGE_NOT_FOUND -> message(
                    result,
                    "Offline package not found",
                    "The offline package was not found for this checker device.",
                    ERROR,
                    false,
                    false
            );
            case DEVICE_TIME_INVALID -> message(
                    result,
                    "Device time invalid",
                    "Device time is outside the accepted range.",
                    WARNING,
                    false,
                    false
            );
        };
    }

    private static ResultMessage message(
            ScanResult resultCode,
            String title,
            String message,
            String severity,
            boolean admitAllowed,
            boolean supportRequired
    ) {
        return ResultMessage.builder()
                .resultCode(resultCode)
                .title(title)
                .message(message)
                .severity(severity)
                .admitAllowed(admitAllowed)
                .supportRequired(supportRequired)
                .build();
    }
}
