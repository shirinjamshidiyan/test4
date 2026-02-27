package com.shirin.notificationservice.core.application.ports.outbound;

import java.util.UUID;


// Outbound port: core requests an "inbox" capability without knowing JPA/SQL.
public interface InboxIdempotencyPort {

    // Claim-first decision:
    // - ACQUIRED: caller may process now
    // - ALREADY_DONE: duplicate delivery, skip
    // - RETRY_EXHAUSTED: too many failed attempts, route to DLT or stop retrying
    AcquireResult acquire(UUID eventId, int maxAttempts);

    // Mark DONE only after successful processing (for example email sent).
    void markDone(UUID eventId);

}