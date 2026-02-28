package com.shirin.notificationservice.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

// atomic operations (the core of correctness)
// Keep these operations atomic. Avoid read-modify-write outside SQL.
public interface NotificationInboxRepository extends JpaRepository<NotificationInboxEntity, UUID> {

    // Read status to decide "dedupe vs retry".
    @Query(value = "SELECT status FROM notification_inbox WHERE event_id = :eventId", nativeQuery = true)
    Optional<String> findStatusRaw(UUID eventId);

    // First-time seen event: insert PROCESSING with attempts=1.
    // ON CONFLICT ensures concurrency safety: if another instance inserted first, we get 0.

    //اگر insert عادی باشد و conflict نباشد → row درج می‌شود.
    //
    //اگر conflict روی event_id رخ دهد (یعنی همین کلید قبلاً وجود دارد) → هیچ کاری نکن و خطا نده.
    //
    //نتیجه این دستور در repository تو:
    //
    //بار اول: 1 row affected
    //
    //بار دوم: 0 rows affected (بدون exception)
    //..................
    //اگر event_id کلید اصلی باشد و دوبار insert کنیم چه می‌شود؟
    //
    //اگر ON CONFLICT DO NOTHING نداشته باشی:
    //
    //Postgres به خاطر PRIMARY KEY اجازه نمی‌دهد دو row با یک event_id داشته باشی.
    //
    //پس insert دوم باعث خطای SQL می‌شود:
    //
    //duplicate key value violates unique constraint ...
    //
    //در Java معمولاً تبدیل می‌شود به DataIntegrityViolationException یا exception مشابه.
    //
    //پس:
    //
    //PRIMARY KEY جلوی تکرار را می‌گیرد.
    //
    //ON CONFLICT DO NOTHING کاری می‌کند که به جای error، insert دوم “بی‌صدا” رد شود.

    //برای سیستم event-driven، این خیلی مهم است چون duplicate delivery طبیعی است و نمی‌خواهی برنامه با exception دیتابیس spam شود.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
      INSERT INTO notification_inbox(event_id, status, attempts, updated_at)
      VALUES (:eventId, 'PROCESSING', 1, NOW())
      ON CONFLICT (event_id) DO NOTHING
      """, nativeQuery = true)
    int insertProcessingIfAbsent(UUID eventId);

    // Retry path: if still PROCESSING and we have attempts remaining, increment attempts atomically.
    // This is how we cap retries at the business level (maxAttempts).
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    /*در Spring Data JPA، برای native update/insert بهتر است:
یا @Modifying(clearAutomatically = true, flushAutomatically = true) بگذاری
این برای correctness ضروری نیست، ولی رفتار persistence context را تمیزتر می‌کند.*/
    //@Modifying(clearAutomatically = true, flushAutomatically = true) added to keep persistence context consistent.
    @Query(value = """
      UPDATE notification_inbox
      SET attempts = attempts + 1,
          updated_at = NOW()
      WHERE event_id = :eventId
        AND status = 'PROCESSING'
        AND attempts < :maxAttempts
      """, nativeQuery = true)
    int incrementAttemptsIfProcessing(UUID eventId, int maxAttempts);


    // Success path: mark DONE after email was sent successfully.
    // DONE acts as a dedupe gate for any duplicate deliveries.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
      UPDATE notification_inbox
      SET status = 'DONE',
          updated_at = NOW()
      WHERE event_id = :eventId
        AND status <> 'DONE'
      """, nativeQuery = true)
    int markDone(UUID eventId);
}
