package com.capstone.checkinservice.mapper;

import com.capstone.checkinservice.dto.common.ResultMessage;
import com.capstone.checkinservice.enums.ScanResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScanResultMessageMapperTest {

    @Test
    void validCheckedInAllowsAdmissionWithoutSupport() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.VALID_CHECKED_IN);

        assertThat(message.getResultCode()).isEqualTo(ScanResult.VALID_CHECKED_IN);
        assertThat(message.isAdmitAllowed()).isTrue();
        assertThat(message.isSupportRequired()).isFalse();
    }

    @Test
    void offlineAcceptedAllowsAdmissionAndStatesPendingSync() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.OFFLINE_ACCEPTED_PENDING_SYNC);

        assertThat(message.isAdmitAllowed()).isTrue();
        assertThat(message.isSupportRequired()).isFalse();
        assertThat(message.getMessage()).containsIgnoringCase("pending server sync");
    }

    @Test
    void alreadyUsedBlocksAdmissionAndRequiresSupport() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.ALREADY_USED);

        assertThat(message.isAdmitAllowed()).isFalse();
        assertThat(message.isSupportRequired()).isTrue();
    }

    @Test
    void qrExpiredBlocksAdmissionWithoutSupport() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.QR_EXPIRED);

        assertThat(message.isAdmitAllowed()).isFalse();
        assertThat(message.isSupportRequired()).isFalse();
    }

    @Test
    void wrongGateBlocksAdmissionWithoutSupport() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.WRONG_GATE);

        assertThat(message.isAdmitAllowed()).isFalse();
        assertThat(message.isSupportRequired()).isFalse();
    }

    @Test
    void lockedResaleBlocksAdmission() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.LOCKED_RESALE);

        assertThat(message.isAdmitAllowed()).isFalse();
    }

    @Test
    void cancelledBlocksAdmission() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.CANCELLED);

        assertThat(message.isAdmitAllowed()).isFalse();
    }

    @Test
    void syncConflictBlocksAdmissionAndRequiresSupport() {
        ResultMessage message = ScanResultMessageMapper.toMessage(ScanResult.SYNC_CONFLICT);

        assertThat(message.isAdmitAllowed()).isFalse();
        assertThat(message.isSupportRequired()).isTrue();
    }
}
