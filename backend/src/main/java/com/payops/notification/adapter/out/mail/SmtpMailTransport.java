package com.payops.notification.adapter.out.mail;

import com.payops.notification.application.port.out.MailTransport;
import com.payops.notification.domain.EmailStatus;
import com.payops.notification.domain.OutboxEmail;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;

/**
 * Delivers multipart (text + HTML) messages through the configured SMTP server.
 */
@RequiredArgsConstructor
public class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final String from;

    @Override
    public EmailStatus deliver(OutboxEmail email) {
        if (email.textBody() == null) {
            throw new IllegalStateException("Message body is no longer available");
        }
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(email.recipient());
            helper.setSubject(email.subject());
            if (email.htmlBody() != null) {
                helper.setText(email.textBody(), email.htmlBody());
            } else {
                helper.setText(email.textBody(), false);
            }
            message.setHeader("X-PayOps-Message-Id", email.publicId().toString());
        } catch (MessagingException e) {
            throw new MailPreparationException("Could not build message", e);
        }
        mailSender.send(message);
        return EmailStatus.SENT;
    }
}
