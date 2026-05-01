package com.capstone.checkinservice.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class TimeMapper {
    private TimeMapper() {
    }

    static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
