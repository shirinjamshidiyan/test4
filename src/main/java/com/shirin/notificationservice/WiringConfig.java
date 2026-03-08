package com.shirin.notificationservice;

import com.shirin.notificationservice.adapters.inbound.kafka.OrderConfirmedKafkaListener;
import com.shirin.notificationservice.adapters.outbound.email.SmtpEmailSenderAdapter;
import com.shirin.notificationservice.adapters.outbound.persistence.JpaInboxIdempotencyAdapter;
import com.shirin.notificationservice.adapters.outbound.persistence.NotificationInboxRepository;
import com.shirin.notificationservice.adapters.outbound.render.ThymeleafReceiptRenderer;
import com.shirin.notificationservice.core.application.ports.outbound.EmailSenderPort;
import com.shirin.notificationservice.core.application.ports.outbound.InboxIdempotencyPort;
import com.shirin.notificationservice.core.application.ports.outbound.ReceiptRendererPort;
import com.shirin.notificationservice.core.application.usecase.SendReceiptEmailUseCase;
import com.shirin.notificationservice.infrastructure.observability.NotificationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;

@Configuration
public class WiringConfig {

  @Bean
  public InboxIdempotencyPort inboxIdempotency(NotificationInboxRepository repo) {
    return new JpaInboxIdempotencyAdapter(repo);
  }

  @Bean
  public EmailSenderPort emailSender(
      JavaMailSender sender, @Value("${app.mail.from}") String from) {
    return new SmtpEmailSenderAdapter(sender, from);
  }

  @Bean
  public ReceiptRendererPort renderer(TemplateEngine engine) {
    return new ThymeleafReceiptRenderer(engine);
  }

  @Bean
  public SendReceiptEmailUseCase sendReceiptEmailUseCase(
      EmailSenderPort emailSender,
      InboxIdempotencyPort inboxIdempotency,
      ReceiptRendererPort renderer,
      Validator validator,
      NotificationMetrics metrics,
      @Value("${app.index.maxAttempts}") int maxAttempts) {
    return new SendReceiptEmailUseCase(
        emailSender, inboxIdempotency, renderer, validator, metrics, maxAttempts);
  }

  @Bean
  public OrderConfirmedKafkaListener orderConfirmedKafkaListener(
      SendReceiptEmailUseCase useCase, NotificationMetrics metrics) {
    return new OrderConfirmedKafkaListener(useCase, metrics);
  }

  @Bean
  public NotificationMetrics notificationMetrics(MeterRegistry registry) {
    return new NotificationMetrics(registry);
  }
}
