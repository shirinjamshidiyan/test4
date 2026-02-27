package com.shirin.notificationservice.adapters.outbound.persistence;

import com.shirin.notificationservice.core.application.ports.outbound.AcquireResult;
import com.shirin.notificationservice.core.application.ports.outbound.InboxIdempotencyPort;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@Slf4j
public class JpaInboxIdempotencyAdapter implements InboxIdempotencyPort {

    private final NotificationInboxRepository repository;

    @Override
    @Transactional
    public AcquireResult acquire(UUID eventId, int maxAttempts) {

        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }

        ///////////////////   claim first approach ///////////////////
        // Path 1: first time. If insert succeeds, we "own" processing.
        // First time seen. Claim processing by inserting PROCESSING.
        int inserted = repository.insertProcessingIfAbsent(eventId);
        if (inserted == 1) {
            log.debug("InboxIdempotency acquire: inserted PROCESSING, eventId={}", eventId);
            return AcquireResult.ACQUIRED;
        }

        // Path 2: event exists. If DONE => dedupe.
        //Already exists. If DONE, dedupe and skip.
        // Path 2: Already exists. If DONE, dedupe and skip.
        InboxStatus status = readStatus(eventId);
        if (status == InboxStatus.DONE) {
            log.debug("InboxIdempotency acquire: already DONE, eventId={}", eventId);
            return AcquireResult.ALREADY_DONE;
        }


        // Path 3: PROCESSING => this is a retry scenario. Increment attempts if allowed.

        int updated = repository.incrementAttemptsIfProcessing(eventId, maxAttempts);
        if (updated == 1) {
            log.debug("InboxIdempotency acquire: incremented attempts (<= {}), eventId={}", maxAttempts, eventId);
            return AcquireResult.ACQUIRED;
        }


        // attempts >= maxAttempts or status changed concurrently.
        // attempts >= maxAttempts => stop retrying at business layer; route to DLT.
        log.warn("Inbox acquire: retry exhausted or not eligible, maxAttempts={}, eventId={}", maxAttempts, eventId);
        return AcquireResult.RETRY_EXHAUSTED;

    }
    @Override
    @Transactional
    public void markDone(UUID eventId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        int updated = repository.markDone(eventId);
        if (updated == 1) {
            log.debug("Inbox markDone: set DONE, eventId={}", eventId);
        } else {
            // Could be already DONE, or row missing due to unexpected flow.
            // This log helps diagnose anomalies without breaking processing.
            log.debug("InboxIdempotency markDone: no update (already DONE or missing row), eventId={}", eventId);
        }
    }

    private InboxStatus readStatus(UUID eventId) {
        Optional<String> raw = repository.findStatusRaw(eventId);

        if (raw.isEmpty()) {
            // This should be rare because insert returned 0, meaning row likely exists.
            // If it happens, treat as "not safe to process" and escalate.
            log.warn("InboxIdempotency acquire: status missing after insert conflict, eventId={}", eventId);
            return InboxStatus.PROCESSING;
        }

        String v = raw.get().trim().toUpperCase(Locale.ROOT);
        try {
            return InboxStatus.valueOf(v);
        } catch (Exception ex) {
            // Unknown status in DB, escalate to avoid accidental duplicates.
            log.error("InboxIdempotency acquire: unknown status='{}', eventId={}", raw.get(), eventId);
            return InboxStatus.PROCESSING;
        }
    }

}
