package com.payops.notification.adapter.out.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payops.notification.application.port.out.ChannelTransport;
import com.payops.notification.application.service.ChannelDeliverySettings;
import com.payops.notification.domain.ChannelTarget;
import com.payops.notification.domain.ChannelType;
import com.payops.notification.domain.NotificationMessage;
import com.payops.shared.domain.Severity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers notifications over HTTPS with Spring's {@link RestClient}
 * (connect/read timeouts configured in {@code NotificationConfig}).
 * <ul>
 *     <li>SLACK: incoming-webhook {@code text} payload (mrkdwn)</li>
 *     <li>MS_TEAMS: legacy MessageCard, accepted by connectors and Workflows webhooks</li>
 *     <li>WEBHOOK: JSON body signed with HMAC-SHA256 over {@code "<timestamp>.<body>"};
 *         header {@code X-PayOps-Signature: t=<unix seconds>,v1=<hex>}</li>
 *     <li>PAGERDUTY: Events API v2 trigger (resolve for {@code INCIDENT_RESOLVED}), dedup key = subject reference</li>
 * </ul>
 * Targets resolving to loopback / private / link-local addresses are refused
 * unless insecure targets are explicitly allowed (development).
 */
@Component
class HttpChannelTransport implements ChannelTransport {

    static final String PAGERDUTY_EVENTS_URL = "https://events.pagerduty.com/v2/enqueue";
    static final String SIGNATURE_HEADER = "X-PayOps-Signature";

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final ChannelDeliverySettings settings;
    private final Clock clock;

    HttpChannelTransport(ObjectMapper objectMapper, ChannelDeliverySettings settings, Clock clock,
                         @Value("${payops.notifications.connect-timeout:PT5S}") Duration connectTimeout,
                         @Value("${payops.notifications.read-timeout:PT10S}") Duration readTimeout) {
        // Redirects are not followed for POST, so a target cannot bounce the request elsewhere.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.http = RestClient.builder().requestFactory(requestFactory)
                .defaultHeader("User-Agent", "PayOps360-Notifications/1.0")
                .build();
        this.objectMapper = objectMapper;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public void send(ChannelType type, ChannelTarget target, NotificationMessage message, UUID deliveryId) {
        switch (type) {
            case SLACK -> post(target.target(), json(slack(message)), Map.of());
            case MS_TEAMS -> post(target.target(), json(teams(message)), Map.of());
            case WEBHOOK -> {
                String body = json(webhook(message, deliveryId));
                long timestamp = clock.instant().getEpochSecond();
                post(target.target(), body, Map.of(
                        SIGNATURE_HEADER, "t=" + timestamp + ",v1=" + sign(target.signingSecret(), timestamp + "." + body),
                        "X-PayOps-Timestamp", String.valueOf(timestamp),
                        "X-PayOps-Event", message.eventType(),
                        "X-PayOps-Delivery", deliveryId.toString()));
            }
            case PAGERDUTY -> post(PAGERDUTY_EVENTS_URL, json(pagerDuty(target.target(), message, deliveryId)), Map.of());
            case EMAIL -> throw new IllegalArgumentException("E-mail channels are delivered through the e-mail outbox");
        }
    }

    private void post(String url, String body, Map<String, String> headers) {
        URI uri = URI.create(url);
        guard(uri);
        http.post().uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> headers.forEach(h::set))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** Basic SSRF guard: https only and no internal destinations (unless explicitly allowed). */
    private void guard(URI uri) {
        if (settings.allowInsecureTargets()) {
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only https:// targets are allowed");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Target resolves to a private or local address");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unknown host " + uri.getHost());
        }
    }

    // ------------------------------------------------------------- payloads

    private static Map<String, Object> slack(NotificationMessage m) {
        StringBuilder text = new StringBuilder("*").append(escapeSlack(m.title())).append("*");
        if (!m.body().isBlank()) {
            text.append('\n').append(escapeSlack(m.body()));
        }
        if (m.link() != null) {
            text.append("\n<").append(m.link()).append("|Open in PayOps360>");
        }
        return Map.of("text", text.toString());
    }

    private static Map<String, Object> teams(NotificationMessage m) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("@type", "MessageCard");
        card.put("@context", "https://schema.org/extensions");
        card.put("summary", m.title());
        card.put("themeColor", color(m.severity()));
        card.put("title", m.title());
        card.put("text", m.body().replace("\n", "<br>"));
        if (m.link() != null) {
            card.put("potentialAction", List.of(Map.of("@type", "OpenUri", "name", "Open in PayOps360",
                    "targets", List.of(Map.of("os", "default", "uri", m.link())))));
        }
        return card;
    }

    private static Map<String, Object> webhook(NotificationMessage m, UUID deliveryId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", deliveryId.toString());
        payload.put("event", m.eventType());
        payload.put("severity", m.severity() == null ? null : m.severity().name());
        payload.put("subjectType", m.subjectType());
        payload.put("subjectReference", m.subjectReference());
        payload.put("title", m.title());
        payload.put("text", m.body());
        payload.put("link", m.link());
        payload.put("occurredAt", m.occurredAt() == null ? null : m.occurredAt().toString());
        return payload;
    }

    private static Map<String, Object> pagerDuty(String routingKey, NotificationMessage m, UUID deliveryId) {
        boolean resolve = "INCIDENT_RESOLVED".equals(m.eventType());
        String dedupKey = m.subjectReference() != null ? "payops360-" + m.subjectReference() : "payops360-" + deliveryId;
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("routing_key", routingKey);
        event.put("event_action", resolve ? "resolve" : "trigger");
        event.put("dedup_key", dedupKey);
        if (!resolve) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", m.title().length() > 1024 ? m.title().substring(0, 1024) : m.title());
            payload.put("source", "payops360");
            payload.put("severity", pagerDutySeverity(m.severity()));
            payload.put("component", m.subjectType() == null ? "payops360" : m.subjectType().toLowerCase(Locale.ROOT));
            payload.put("class", m.eventType());
            payload.put("custom_details", Map.of("details", m.body(),
                    "reference", m.subjectReference() == null ? "" : m.subjectReference()));
            event.put("payload", payload);
            if (m.link() != null) {
                event.put("links", List.of(Map.of("href", m.link(), "text", "Open in PayOps360")));
            }
        }
        return event;
    }

    private static String pagerDutySeverity(Severity severity) {
        if (severity == null) {
            return "info";
        }
        return switch (severity) {
            case CRITICAL -> "critical";
            case HIGH -> "error";
            case MEDIUM -> "warning";
            case LOW -> "info";
        };
    }

    private static String color(Severity severity) {
        if (severity == null) {
            return "0E7C5B";
        }
        return switch (severity) {
            case CRITICAL, HIGH -> "D1453B";
            case MEDIUM -> "E0A030";
            case LOW -> "0E7C5B";
        };
    }

    private static String escapeSlack(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise notification", e);
        }
    }
}
