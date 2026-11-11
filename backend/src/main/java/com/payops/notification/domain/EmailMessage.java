package com.payops.notification.domain;

/**
 * A transactional e-mail ready to be queued. {@code category} is a stable,
 * machine-readable label (e.g. {@code PASSWORD_RESET}) used for filtering and
 * metrics; {@code organizationId} is optional (null for pre-tenant mail).
 */
public record EmailMessage(Long organizationId, String recipient, String subject, String category,
                           EmailContent content) {

    public EmailMessage {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("subject is required");
        }
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
    }
}
