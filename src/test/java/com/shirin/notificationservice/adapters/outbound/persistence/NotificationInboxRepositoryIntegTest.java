package com.shirin.notificationservice.adapters.outbound.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

//هدف این تست://SQLهای native داخل NotificationInboxRepository واقعاً روی Postgres واقعی درست کار می‌کنند

@Testcontainers
//@SpringBootTest(classes = com.shirin.notificationservice.App.class)
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
//Spring Boot در تست‌ها یک رفتار پیش‌فرض دارد:
//اگر دیتابیس واقعی نداده باشی، می‌رود یک دیتابیس in-memory مثل H2 می‌آورد.
//اما ما نمی‌خواهیم H2.
//ما می‌خواهیم Postgres واقعی.
//پس می‌گوییم:
//“هیچ دیتابیس تستی جایگزین نکن”
//“همان datasourceی که من می‌دهم را استفاده کن”
public class NotificationInboxRepositoryIntegTest {


    //Integration Test نیاز به سیستم خارجی دارد
    //Testcontainers آن سیستم خارجی را به صورت موقتی فراهم می‌کند

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");


    @DynamicPropertySource
    //تزریق url/user/pass کانتینر به Spring
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    NotificationInboxRepository inboxRepository;

    @Test
    void should_insert_only_once() {
        UUID id = UUID.randomUUID();
        int first = inboxRepository.insertProcessingIfAbsent(id);
        int second = inboxRepository.insertProcessingIfAbsent(id);
        assertEquals(1, first);
        assertEquals(0, second);
    }

    @Test
    void should_increment_attempts_until_max() {
        UUID id = UUID.randomUUID();

        inboxRepository.insertProcessingIfAbsent(id);

        int r1 = inboxRepository.incrementAttemptsIfProcessing(id, 5);
        int r2 = inboxRepository.incrementAttemptsIfProcessing(id, 5);
        int r3 = inboxRepository.incrementAttemptsIfProcessing(id, 5);
        int r4 = inboxRepository.incrementAttemptsIfProcessing(id, 5);
        int r5 = inboxRepository.incrementAttemptsIfProcessing(id, 5);

        assertEquals(1, r1);
        assertEquals(1, r2);
        assertEquals(1, r3);
        assertEquals(1, r4);
        assertEquals(0, r5);
    }

    @Test
    void should_mark_done() {
        UUID id = UUID.randomUUID();

        inboxRepository.insertProcessingIfAbsent(id);
        inboxRepository.markDone(id);

        Optional<String> status = inboxRepository.findStatusRaw(id);
        assertTrue(status.isPresent());
        assertEquals("DONE", status.get());
    }
}
