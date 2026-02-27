package com.shirin.notificationservice.core.application.ports.outbound;

import com.shirin.notificationservice.core.domain.model.ReceiptEmail;

public interface EmailSenderPort {

   void send(ReceiptEmail email);
}
