package com.shirin.notificationservice.adapters.outbound.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification_inbox")
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
}
