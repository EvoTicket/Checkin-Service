package com.capstone.checkinservice;

import com.capstone.checkinservice.repository.CheckerAssignmentRepository;
import com.capstone.checkinservice.repository.CheckerDeviceRepository;
import com.capstone.checkinservice.repository.TicketAccessStateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(properties = {
        "DB_URL=jdbc:postgresql://localhost:5432/evoticket",
        "DB_USERNAME=postgres",
        "DB_PASSWORD=",
        "REDIS_URL=redis://localhost:6379",
        "EUREKA_DEFAULT_ZONE=http://localhost:8761/eureka/",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
})
class CheckinServiceApplicationTests {
    @MockBean
    private TicketAccessStateRepository ticketAccessStateRepository;

    @MockBean
    private CheckerAssignmentRepository checkerAssignmentRepository;

    @MockBean
    private CheckerDeviceRepository checkerDeviceRepository;

    @Test
    void contextLoads() {
    }

}
