package com.capstone.checkinservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "checkin.offline-package")
public class OfflinePackageProperties {
    private long ttlMinutes = 360;
    private int maxTicketSnapshots = 5000;
}
