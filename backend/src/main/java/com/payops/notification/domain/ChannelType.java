package com.payops.notification.domain;

/**
 * Supported notification channel types and what their (encrypted) target holds.
 */
public enum ChannelType {
    /** Comma-separated list of e-mail addresses, delivered through the e-mail outbox. */
    EMAIL,
    /** Slack incoming-webhook URL. */
    SLACK,
    /** Microsoft Teams incoming-webhook / workflow URL. */
    MS_TEAMS,
    /** Generic HTTPS endpoint; payloads are HMAC-SHA256 signed with the channel's signing secret. */
    WEBHOOK,
    /** PagerDuty Events API v2 integration (routing) key. */
    PAGERDUTY;

    public boolean isUrl() {
        return this == SLACK || this == MS_TEAMS || this == WEBHOOK;
    }
}
