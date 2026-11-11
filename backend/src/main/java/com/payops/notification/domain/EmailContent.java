package com.payops.notification.domain;

/**
 * Multipart body: every message carries a plain-text and an HTML rendition.
 */
public record EmailContent(String text, String html) {
}
