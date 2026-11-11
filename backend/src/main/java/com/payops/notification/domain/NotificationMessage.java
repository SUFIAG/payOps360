package com.payops.notification.domain;

import com.payops.shared.domain.Severity;

import java.time.Instant;

/**
 * Channel-neutral content of a notification; each transport renders it in
 * its own format (Slack text, Teams card, PagerDuty event, signed JSON, e-mail).
 *
 * @param eventType        operational event type, {@code ESCALATION} or {@code TEST}
 * @param subjectType      {@code INCIDENT}, {@code ALERT}, {@code PROVIDER} or null
 * @param link             deep link into the console (may be null)
 */
public record NotificationMessage(String eventType, Severity severity, String subjectType, String subjectReference,
                                  String title, String body, String link, Instant occurredAt) {
}
