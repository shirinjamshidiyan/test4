package com.shirin.notificationservice.adapters.outbound.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notification_inbox")  //inbox table
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class NotificationInboxEntity {

    // Primary key is eventId: guarantees 1 row per incoming event.
    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    // PROCESSING or DONE
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboxStatus status;

    // Counts how many processing attempts this service performed for this event.
    @Column(nullable = false)
    private int attempts;

    // Updated each time we (re)attempt or mark DONE.
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Convenience factory for initial insert (first time we see this event).
//    public static NotificationInboxEntity newProcessing(UUID eventId) {
//        NotificationInboxEntity e = new NotificationInboxEntity();
//        e.eventId = Objects.requireNonNull(eventId,"eventId");
//        e.status = InboxStatus.PROCESSING;
//        e.attempts = 1;
//        e.updatedAt = Instant.now();
//        return e;
//    }
}