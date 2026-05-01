package com.capstone.checkinservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "checkin.checker")
public class CheckerDeviceProperties {
    private final Device device = new Device();
    private long clockSkewSeconds = 300;

    @Getter
    @Setter
    public static class Device {
        private boolean requiredForOnlineScan = false;
    }
}
