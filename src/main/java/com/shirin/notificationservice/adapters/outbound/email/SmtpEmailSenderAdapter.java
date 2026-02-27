package com.shirin.notificationservice.adapters.outbound.email;

import com.shirin.notificationservice.core.application.ports.outbound.EmailSenderPort;
import com.shirin.notificationservice.core.domain.model.ReceiptEmail;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Objects;

@Slf4j
public class SmtpEmailSenderAdapter implements EmailSenderPort {

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpEmailSenderAdapter(JavaMailSender mailSender, String from) {
        this.mailSender = Objects.requireNonNull(mailSender, "mailSender must not be null"); //fast-fail
        this.from = Objects.requireNonNull(from, "from must not be null");
    }

    @Override
    public void send(ReceiptEmail email) {
        // Never log recipient address or html body (PII).
        // IDs are already in MDC (correlationId/eventId/orderId).
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            // If you later add attachments, use: new MimeMessageHelper(msg, true, "UTF-8")
            MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.htmlBody(), true);
            mailSender.send(msg);
            log.info("SMTP send completed for subject={}", email.subject());
        } catch (Exception e) {
            // Let the exception bubble to Kafka error handler for retry/DLT.
            log.error("SMTP send failed", e);
            throw new RuntimeException("Email send failed", e);
        }
    }

}
