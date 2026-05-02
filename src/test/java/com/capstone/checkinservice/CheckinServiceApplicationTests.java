package com.capstone.checkinservice;

import com.capstone.checkinservice.repository.CheckerAssignmentRepository;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import com.capstone.checkinservice.repository.CheckInLogRepository;
import com.capstone.checkinservice.repository.OfflinePackageRepository;
import com.capstone.checkinservice.repository.OfflineSyncItemRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import com.capstone.checkinservice.crypto.key.TestQrKeyMaterialFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/evoticket",
        "DB_USERNAME=postgres",
        "DB_PASSWORD=",
        "REDIS_URL=redis://localhost:6379",
        "EUREKA_DEFAULT_ZONE=http://localhost:8761/eureka/",
        "spring.security.jwt.secret-key=bG9jYWwtZGV2LWp3dC1zZWNyZXQta2V5LWZvci1ldm90aWNrZXQtbWljcm9zZXJ2aWNlcy0xMjM0NTY3ODkw",
        "app.seed.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class CheckinServiceApplicationTests {
    private static final TestQrKeyMaterialFactory.QrKeyMaterial QR_KEY_MATERIAL =
            TestQrKeyMaterialFactory.generate("kid-main");

    @MockBean
    private TicketAccessStateRepository ticketAccessStateRepository;

    @MockBean
    private CheckerAssignmentRepository checkerAssignmentRepository;

    @MockBean
    private CheckerDeviceRepository checkerDeviceRepository;

    @MockBean
    private CheckInLogRepository checkInLogRepository;

    @MockBean
    private OfflinePackageRepository offlinePackageRepository;

    @MockBean
    private OfflineSyncItemRepository offlineSyncItemRepository;

    @DynamicPropertySource
    static void qrKeyProperties(DynamicPropertyRegistry registry) {
        registry.add("app.qr.key-id", QR_KEY_MATERIAL::kid);
        registry.add("app.qr.private-key-base64", QR_KEY_MATERIAL::privateKeyBase64);
        registry.add("app.qr.public-key-base64", QR_KEY_MATERIAL::publicKeyBase64);
    }

    @Test
    void contextLoads() {
    }

}
