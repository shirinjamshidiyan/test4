package com.shirin.notificationservice.adapters.outbound.render;

import com.shirin.notificationservice.core.application.ports.outbound.ReceiptRendererPort;
import com.shirin.notificationservice.integration.events.OrderConfirmedEventV1;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
public class ThymeleafReceiptRenderer implements ReceiptRendererPort {

  private final TemplateEngine engine;
  private static final String TEMPLATE_NAME = "receipt.html";

  public ThymeleafReceiptRenderer(TemplateEngine engine) {
    this.engine = Objects.requireNonNull(engine, "templateEngine must not be null");
  }

  @Override
  public String renderReceipt(OrderConfirmedEventV1 event) {

    Objects.requireNonNull(event, "event must not be null");
    try {

      Context ctx = new Context();
      ctx.setVariable("e", event);
      return engine.process(TEMPLATE_NAME, ctx);
    } catch (Exception ex) {
      // Do not log the rendered HTML or customer PII.
      log.error("Template rendering failed for orderId={}", event.orderId(), ex);
      throw ex;
    }
  }
}
