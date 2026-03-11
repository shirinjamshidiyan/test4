package com.shirin.notificationservice.core.application.ports.outbound;

import com.shirin.notificationservice.integration.events.OrderConfirmedEventV1;

public interface ReceiptRendererPort {
  String renderReceipt(OrderConfirmedEventV1 event);
}
