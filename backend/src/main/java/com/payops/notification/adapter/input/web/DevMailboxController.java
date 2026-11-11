package com.payops.notification.adapter.input.web;

import com.payops.notification.application.port.input.MailboxQueryUseCase;
import com.payops.notification.domain.EmailStatus;
import com.payops.notification.domain.OutboxEmail;
import com.payops.shared.adapter.web.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Development-only mailbox: lists recent outbox messages so reset, invitation
 * and verification links can be followed without an SMTP server. The bean
 * does not exist outside the dev profile, so the route answers 404 there.
 * Messages of every tenant are listed (sign-up mail belongs to a brand-new
 * tenant), which is why this must never be enabled in production.
 */
@Tag(name = "Development")
@Profile("dev")
@RestController
@RequestMapping("/api/v1/dev/mailbox")
@PreAuthorize("hasAuthority('USER_MANAGE')")
@RequiredArgsConstructor
class DevMailboxController {

    private final MailboxQueryUseCase mailbox;

    @GetMapping
    ApiResponse<List<MailboxMessage>> recent(@RequestParam(defaultValue = "50") int limit) {
        return ApiResponse.ok(mailbox.recent(limit).stream().map(MailboxMessage::of).toList());
    }

    record MailboxMessage(UUID id, String recipient, String subject, String category, EmailStatus status,
                          int attempts, String lastError, String textBody, String htmlBody, Instant createdAt,
                          Instant sentAt) {

        static MailboxMessage of(OutboxEmail e) {
            return new MailboxMessage(e.publicId(), e.recipient(), e.subject(), e.category(), e.status(),
                    e.attempts(), e.lastError(), e.textBody(), e.htmlBody(), e.createdAt(), e.sentAt());
        }
    }
}
