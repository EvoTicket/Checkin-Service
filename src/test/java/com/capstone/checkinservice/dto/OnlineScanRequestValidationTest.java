package com.capstone.checkinservice.dto;

import com.capstone.checkinservice.dto.request.OnlineScanRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OnlineScanRequestValidationTest {

    @Test
    void missingRequiredFieldsFailValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            OnlineScanRequest request = OnlineScanRequest.builder()
                    .gateId("A1")
                    .deviceId("device-abc")
                    .build();

            Set<String> invalidFields = validator.validate(request).stream()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .collect(Collectors.toSet());

            assertThat(invalidFields).contains("qrToken", "eventId", "showtimeId", "scannedAt");
        }
    }
}
