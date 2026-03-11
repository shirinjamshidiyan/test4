package com.shirin.notificationservice.core.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.shirin.notificationservice.core.application.ports.outbound.AcquireResult;
import com.shirin.notificationservice.core.application.ports.outbound.EmailSenderPort;
import com.shirin.notificationservice.core.application.ports.outbound.InboxIdempotencyPort;
import com.shirin.notificationservice.core.application.ports.outbound.ReceiptRendererPort;
import com.shirin.notificationservice.infrastructure.observability.NotificationMetrics;
import com.shirin.notificationservice.integration.events.OrderConfirmedEventV1;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// assertEquals از JUnit: خروجی متد درست است؟
// verify از Mockito: رفتار داخلی درست بوده؟
@ExtendWith(MockitoExtension.class)
public class SendReceiptEmailUseCaseTest {

  // اینها mock هستند: یعنی واقعی نیستند، فقط callها را ثبت می‌کنند
  @Mock EmailSenderPort emailSender;
  @Mock InboxIdempotencyPort inboxIdempotency;
  @Mock ReceiptRendererPort renderer;
  @Mock Validator validator;
  @Mock NotificationMetrics metrics;

  SendReceiptEmailUseCase useCase;

  @BeforeEach
  void setup() {
    this.useCase =
        new SendReceiptEmailUseCase(emailSender, inboxIdempotency, renderer, validator, metrics, 5);
  }

  @Test
  void should_skip_duplicate_and_not_send_email() {

    ///////////////////////// Arrange: آماده سازی
    OrderConfirmedEventV1 event = validEvent();
    when(validator.validate(event)).thenReturn(Set.of());
    // inbox: pretend it's already processed
    when(inboxIdempotency.acquire(event.eventId(), 5)).thenReturn(AcquireResult.ALREADY_DONE);

    //////////////////// ACT
    ProcessingResult result = useCase.handle(event);

    /////////////////////// Assert: بررسی
    assertEquals(ProcessingResult.DUPLICATE, result);

    verify(emailSender, never()).send(any());
    verify(inboxIdempotency, never()).markDone(any());
    verify(inboxIdempotency).acquire(event.eventId(), 5);
  }

  @Test
  void should_send_email_and_mark_done_on_success() {
    //////////////////////////  Arrange
    OrderConfirmedEventV1 event = validEvent();
    when(validator.validate(event)).thenReturn(Set.of());
    when(inboxIdempotency.acquire(event.eventId(), 5)).thenReturn(AcquireResult.ACQUIRED);
    when(renderer.renderReceipt(event)).thenReturn("<html>receipt is ready</html>");
    Timer renderTimer = mock(Timer.class);
    Timer sendTimer = mock(Timer.class);
    when(metrics.renderTimer(anyString())).thenReturn(renderTimer);
    when(metrics.emailSendTimer(anyString(), anyString(), anyString())).thenReturn(sendTimer);
    //////////////////////// Act
    ProcessingResult result = useCase.handle(event);

    ////////////////// Assert
    assertEquals(ProcessingResult.SUCCESS, result);
    verify(emailSender, times(1)).send(any());
    verify(inboxIdempotency, times(1)).markDone(event.eventId());
    verify(inboxIdempotency, times(1)).acquire(event.eventId(), 5);
    verify(renderer, times(1)).renderReceipt(event);
  }

  @Test
  void should_throw_when_invalid_event() {

    //////////////////////////  Arrange
    OrderConfirmedEventV1 event = validEvent();

    ConstraintViolation<OrderConfirmedEventV1> violation = mock(ConstraintViolation.class);

    Path path = mock(Path.class);
    when(path.toString()).thenReturn("customerEmail");
    when(violation.getPropertyPath()).thenReturn(path);
    when(violation.getMessage()).thenReturn("must not be blank");
    when(validator.validate(event)).thenReturn(Set.of(violation));

    //////////////// Act and Assert
    assertThrows(SendReceiptEmailUseCase.InvalidEventException.class, () -> useCase.handle(event));

    verify(inboxIdempotency, never()).acquire(any(), anyInt());
  }

  @Test
  void should_throw_when_retry_exhausted() {
    ///////////////// َ Arrange
    OrderConfirmedEventV1 event = validEvent();
    when(validator.validate(event)).thenReturn(Set.of());
    when(inboxIdempotency.acquire(event.eventId(), 5)).thenReturn(AcquireResult.RETRY_EXHAUSTED);

    /////// Act and Assert
    assertThrows(
        SendReceiptEmailUseCase.RetryExhaustedException.class, () -> useCase.handle(event));
    verify(emailSender, never()).send(any());
  }

  @Test
  void should_not_mark_done_if_email_fails() {

    ////////////// Arrange
    OrderConfirmedEventV1 event = validEvent();

    when(validator.validate(event)).thenReturn(Set.of());
    when(inboxIdempotency.acquire(event.eventId(), 5)).thenReturn(AcquireResult.ACQUIRED);
    when(renderer.renderReceipt(event)).thenReturn("<html>Receipt is Ready</html>");
    doThrow(new RuntimeException("smtp down")).when(emailSender).send(any());
    when(metrics.renderTimer(anyString())).thenReturn(mock(Timer.class));
    when(metrics.emailSendTimer(anyString(), anyString(), anyString()))
        .thenReturn(mock(Timer.class));

    //////// Act and Assert
    assertThrows(RuntimeException.class, () -> useCase.handle(event));

    verify(inboxIdempotency, never()).markDone(any());
  }

  private static OrderConfirmedEventV1 validEvent() {
    UUID eventId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    return new OrderConfirmedEventV1(
        eventId,
        orderId,
        Instant.now(),
        1,
        "test@customer.com",
        "Shirin",
        new BigDecimal("123.45"),
        "USD",
        List.of(
            new OrderConfirmedEventV1.OrderItem(
                "SKU-1", "Item 1", 1, new BigDecimal("123.45"), new BigDecimal("123.45"))),
        "Add-1",
        "Add-2");
  }
}
