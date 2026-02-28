package com.shirin.notificationservice.core.application.usecase;

import com.shirin.notificationservice.core.application.ports.outbound.AcquireResult;
import com.shirin.notificationservice.core.application.ports.outbound.EmailSenderPort;
import com.shirin.notificationservice.core.application.ports.outbound.InboxIdempotencyPort;
import com.shirin.notificationservice.core.application.ports.outbound.ReceiptRendererPort;
import com.shirin.notificationservice.core.domain.model.ReceiptEmail;
import com.shirin.notificationservice.infrastructure.observability.NotificationMetrics;
import com.shirin.notificationservice.integration.events.OrderConfirmedEventV1;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.Set;

// Application-layer use case (orchestration):
// - Decides whether to process (inbox gate).
// - Calls renderer + SMTP sender.
// - Marks DONE only on success.

@Slf4j
public class SendReceiptEmailUseCase {

   // private static final Logger log = LoggerFactory.getLogger(SendReceiptEmailUseCase.class);
    private final EmailSenderPort emailSender;
    private final InboxIdempotencyPort inboxIdempotency;
    private final ReceiptRendererPort renderer;
    private final Validator validator;
    private final NotificationMetrics metrics;
    private final int maxAttempts;

    public SendReceiptEmailUseCase(EmailSenderPort emailSender,
                                   InboxIdempotencyPort idempotencyStore,
                                    ReceiptRendererPort renderer,
                                    Validator validator,
                                    NotificationMetrics metrics,
                                   int maxAttempts) {
        this.emailSender = Objects.requireNonNull(emailSender, "emailSender must not be null");
        this.inboxIdempotency =Objects.requireNonNull(idempotencyStore, "idempotencyStore must not be null");
        this.renderer =Objects.requireNonNull(renderer, "renderer must not be null");
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
        this.metrics = Objects.requireNonNull(metrics,"metrics must not be null");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.maxAttempts = maxAttempts;

    }

    //validate -- try to send email (claim-first-approach) --- render -- send -- mark email
    public ProcessingResult  handle(OrderConfirmedEventV1 event)
    {

        Objects.requireNonNull(event,"event must not be null");
        log.info("validation input data : orderId={} ",event.orderId());  // no PII

        try {
            // Validate payload early; invalid messages should not retry.
            // Permanent validation errors -> should not retry
            validateOrThrow(event);

            // Inbox gate: decide dedupe / retry / DLT.
            AcquireResult acq = inboxIdempotency.acquire(event.eventId(), maxAttempts);

            // Duplicate delivery; skip to avoid duplicate emails.
            if (acq == AcquireResult.ALREADY_DONE) {
                metrics.incEventProcessedCounter("duplicate");
                return ProcessingResult.DUPLICATE;
            }

            if (acq == AcquireResult.RETRY_EXHAUSTED) {
                metrics.incEventProcessedCounter("exhausted");
                throw new RetryExhaustedException("Inbox retry exhausted for eventId=" + event.eventId());
            }


            /// Renderer
            log.debug("Rendering receipt template");
            Timer.Sample renderSample = Timer.start();
            String html =renderer.renderReceipt(event);
            renderSample.stop(metrics.renderTimer(NotificationMetrics.TEMPLATE_RECEIPT));

            // Build email (do not log to/html)
            String subject = "Receipt for order " + event.orderId();
            ReceiptEmail receiptEmail = new ReceiptEmail(event.customerEmail(), subject, html);

            /// Send
            Timer.Sample sendSample = Timer.start();
            try {
                log.info("Sending email....");
                emailSender.send(receiptEmail);
                sendSample.stop(metrics.emailSendTimer(NotificationMetrics.PROVIDER_SMTP, NotificationMetrics.TEMPLATE_RECEIPT, "success"));
                metrics.incEmailSendCounter(NotificationMetrics.PROVIDER_SMTP, NotificationMetrics.TEMPLATE_RECEIPT, "success");
            } catch (Exception ex) {
                sendSample.stop(metrics.emailSendTimer(NotificationMetrics.PROVIDER_SMTP, NotificationMetrics.TEMPLATE_RECEIPT, "failure"));
                metrics.incEmailSendCounter(NotificationMetrics.PROVIDER_SMTP, NotificationMetrics.TEMPLATE_RECEIPT, "failure");
                throw ex;
            }


            // Mark DONE only after successful send
            inboxIdempotency.markDone(event.eventId());
            log.debug("Event marked as processed");
            metrics.incEventProcessedCounter("success");
            return ProcessingResult.SUCCESS;

        }
        catch (InvalidEventException ex) {
            metrics.incEventProcessedCounter("invalid");
            throw ex;
        }
        catch (RetryExhaustedException ex) {
            // already counted as exhausted above, keep a consistent final timer tag
            throw ex;

        }
        catch (Exception ex) {
            metrics.incEventProcessedCounter("failed");
            throw ex;
        }

    }

    private void validateOrThrow(OrderConfirmedEventV1 event) {
        Set<ConstraintViolation<OrderConfirmedEventV1>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .reduce((a,b) -> a + "; " + b).orElse("");
            log.warn("Invalid OrderConfirmedEventV1 payload, will not retry");
            throw new InvalidEventException("OrderConfirmedEventV1 validation failed: " + msg);
        }
    }


    public static class InvalidEventException extends RuntimeException {
        public InvalidEventException(String msg) { super(msg); }
    }

    // Used to stop Kafka retries and force DLT when business-layer maxAttempts is exceeded.
    public static class RetryExhaustedException extends RuntimeException {
        public RetryExhaustedException(String message) {
            super(message);
        }
    }

}
