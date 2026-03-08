package com.shirin.notificationservice.adapters.inbound.kafka;

import com.shirin.notificationservice.core.application.usecase.ProcessingResult;
import com.shirin.notificationservice.core.application.usecase.SendReceiptEmailUseCase;
import com.shirin.notificationservice.infrastructure.observability.LogContext;
import com.shirin.notificationservice.infrastructure.observability.NotificationMetrics;
import com.shirin.notificationservice.integration.events.OrderConfirmedEventV1;
import io.micrometer.core.instrument.Timer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;

// تصویر ذهنی درست
//
// اگر فقط publish می‌کنی:
// → producer retry کافی است.
//
// اگر publish می‌کنی و می‌ترسی duplicate داخل topic بیاید:
// → idempotent producer.
//
// اگر consume و دوباره publish می‌کنی:
// → transactional producer.
//
// اگر side effect بیرونی داری:
// → idempotency در business layer.
// ..................
// in producer:

// durability می‌گوید چه سطحی از persistence لازم است
//
// retry می‌گوید اگر fail شد، دوباره تلاش کن
//
// idempotence می‌گوید اگر retry duplicate ساخت، حذفش کن
//
// این سه با هم یک سیستم producer مقاوم می‌سازند
@AllArgsConstructor
@Slf4j
public class OrderConfirmedKafkaListener {

  private final SendReceiptEmailUseCase sendEmailUseCase;
  private final NotificationMetrics metrics;

  // اگر exception بدهد، error handler تصمیم می‌گیرد retry یا DLT
  // We let Spring Kafka error handler do retries and DLT. If handle() throws, it retries, then DLT
  // ConsumerFactory / ContainerFactory
  // زیرساختی است که consumer را می‌سازد و تنظیمات می‌دهد:
  // bootstrap servers
  // groupId
  // deserializer
  // concurrency
  // ack mode
  // max poll records
  // این‌ها infra/config هستند.

  // TODO1
  @KafkaListener(
      topics = "${app.kafka.topic}", // topics = "order-confirmed.v1" or
      containerFactory = "kafkaListenerContainerFactory"
      /*
      Spring می‌گوید:
      factory با نام kafkaListenerContainerFactory را پیدا کن
      با آن یک container بساز
      container را subscribe کن به topic
      شروع به poll کن
      هر پیام که رسید، deserialize کن و onMessage را صدا بزن
       */

      //            Producer
      //   ↓
      // Serializer
      //   ↓
      // Kafka (bytes)
      //   ↓
      // ErrorHandlingDeserializer
      //        ↓
      //    JsonDeserializer
      //        ↓
      //    SUCCESS → object → listener
      //    FAIL    → header + null → error handler
      )
  public void onMessage(
      OrderConfirmedEventV1 event,
      @Header(name = "correlationId", required = false) String correlationId,
      @Header(name = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
      @Header(name = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
      @Header(name = KafkaHeaders.OFFSET, required = false) Long offset) {

    // Count every consumed message (even if invalid).
    metrics.incEventConsumedCounter(topic, NotificationMetrics.EVENT_ORDER_CONFIRMED_V1_TYPE);

    // End-to-end latency: from message delivery to completion (single timer)
    // End-to-end timer: from message receipt to final outcome
    // (success/duplicate/invalid/failed/exhausted)
    Timer.Sample sample = Timer.start();

    try (var ctx = LogContext.fillMDC(correlationId, event, topic, partition, offset)) {

      //  // No PII here. Only IDs come via MDC
      // No PII here. IDs only.
      if (event != null) {
        log.info(
            "OrderConfirmedEventV1 received orderId={} eventId={}",
            event.orderId(),
            event.eventId());
      } else {
        log.warn("OrderConfirmedEventV1 received event=null");
      }

      // Call the application use case (orchestration).
      ProcessingResult result = sendEmailUseCase.handle(event);

      // Stop timer + increment outcome metrics.
      // UseCase already increments some metrics; here we ensure Kafka-level outcome is recorded
      // consistently.
      if (result == ProcessingResult.SUCCESS) {
        sample.stop(metrics.eventProcessingTimer("success"));
        log.debug("Kafka message processed outcome=success");
        return;
      }

      if (result == ProcessingResult.DUPLICATE) {
        sample.stop(metrics.eventProcessingTimer("duplicate"));
        log.debug("Kafka message processed outcome=duplicate");
        return;
      }

      // Should not happen, but keep safe.
      sample.stop(metrics.eventProcessingTimer("failed"));
      log.warn("Kafka message processed outcome=unknown");
    } catch (SendReceiptEmailUseCase.InvalidEventException ex) {
      // Permanent error: do not retry (configure as not-retryable in error handler).
      sample.stop(metrics.eventProcessingTimer("invalid"));
      log.warn("Invalid event, will not retry. {}", ex.getMessage());
      throw ex;

    } catch (SendReceiptEmailUseCase.RetryExhaustedException ex) {
      // Business-layer exhausted: route to DLT (configure as not-retryable in error handler).
      // Business-layer exhausted: configure as NOT retryable -> DLT.
      sample.stop(metrics.eventProcessingTimer("exhausted")); // ///////pk
      log.warn("Retry exhausted, route to DLT. {}", ex.getMessage());
      throw ex;

    } catch (Exception ex) {
      // Retryable: SMTP, transient DB, template errors, etc.
      // Retryable failures: SMTP, transient DB, template rendering, etc.
      sample.stop(metrics.eventProcessingTimer("failed"));
      log.error("Processing failed, Kafka will retry or DLT based on policy", ex);
      throw ex;
    }
  }
}
