package com.capstone.checkinservice.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupportMaskingServiceTest {
    private final SupportMaskingService maskingService = new SupportMaskingService();

    @Test
    void masksEmailWithoutExposingFullLocalPart() {
        assertThat(maskingService.maskEmail("han.tran@gmail.com")).isEqualTo("han.***@gmail.com");
        assertThat(maskingService.maskEmail("ha@gmail.com")).isEqualTo("h***@gmail.com");
    }

    @Test
    void masksPhoneWithoutExposingMiddleDigits() {
        assertThat(maskingService.maskPhone("0987654328")).isEqualTo("09******28");
    }

    @Test
    void masksOwnerReference() {
        assertThat(maskingService.maskOwnerRef(123456789L)).isEqualTo("usr_****6789");
    }

    @Test
    void masksShortOwnerReferenceWithFourDigitPadding() {
        assertThat(maskingService.maskOwnerRef(10L)).isEqualTo("usr_****0010");
    }

    @Test
    void masksDisplayName() {
        assertThat(maskingService.maskDisplayName("Tran Gia Han")).isEqualTo("T*** G*** H***");
    }
}