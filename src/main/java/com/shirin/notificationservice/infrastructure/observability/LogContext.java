package com.shirin.notificationservice.infrastructure.observability;

import com.shirin.notificationservice.integration.events.OrderConfirmedEventV1;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;

public final class LogContext implements AutoCloseable {

  private final List<String> keys = new ArrayList<>();

  private LogContext() {}

  public static LogContext fillMDC(
      String correlationId,
      OrderConfirmedEventV1 event,
      String topic,
      Integer partition,
      Long offset) {
    String cid = getOrCreateCorrelationId(correlationId);
    MDC.put("correlationId", cid);

    if (event != null) {
      if (event.eventId() != null) MDC.put("eventId", event.eventId().toString());
      if (event.orderId() != null) MDC.put("orderId", event.orderId().toString());
      MDC.put("eventVersion", String.valueOf(event.version()));
    }

    if (topic != null) MDC.put("topic", topic);
    if (partition != null) MDC.put("partition", String.valueOf(partition));
    if (offset != null) MDC.put("offset", String.valueOf(offset));

    return new LogContext();
  }

  public static String getOrCreateCorrelationId(String corId) {
    return (corId == null || corId.isBlank()) ? UUID.randomUUID().toString() : corId.trim();
  }

  @Override
  public void close() {
    MDC.remove("correlationId");
    MDC.remove("eventId");
    MDC.remove("orderId");
    MDC.remove("eventVersion");
    MDC.remove("topic");
    MDC.remove("partition");
    MDC.remove("offset");
    //        MDC.clear();
  }
}
