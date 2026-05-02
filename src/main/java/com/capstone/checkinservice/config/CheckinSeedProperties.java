package com.capstone.checkinservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.seed")
public class CheckinSeedProperties {
    private Long buyerId = 2L;
    private Long checkerId = 3L;
    private Long eventId = 1001L;
    private Long showtimeId = 2001L;
    private String gateId = "gate-b";
    private String wrongGateId = "gate-a";
    private String deviceId = "checker-device-b-02";
}
