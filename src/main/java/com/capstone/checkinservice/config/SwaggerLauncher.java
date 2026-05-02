package com.capstone.checkinservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.net.URI;

@Component
@Slf4j
public class SwaggerLauncher {

    @Value("${swagger.host-url}")
    private String hostUrl;

    @Value("${spring.profiles.active:default}")
    private String profile;

    @EventListener(ApplicationReadyEvent.class)
    public void launchBrowser() {
        if(!"default".equals(profile)) {
            log.info("⚠️ Not in 'dev' profile (current: '{}'), skipping Swagger UI launch.", profile);
            return;
        }
        try {
            String swaggerUrl = hostUrl + "/swagger-ui/index.html";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(swaggerUrl));
            } else {
                log.warn("⚠️ Desktop not supported. Open manually: {}", swaggerUrl);
            }
        } catch (Exception e) {
            log.warn("❌ Failed to open Swagger UI: {}", e.getMessage());
        }
    }
}