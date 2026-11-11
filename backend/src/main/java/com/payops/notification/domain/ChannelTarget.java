package com.payops.notification.domain;

import com.payops.shared.domain.DomainException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The sensitive part of a channel: where to deliver (URL, routing key or
 * address list) and, for generic webhooks, the HMAC signing secret. Stored
 * encrypted; only {@link #display(ChannelType)} is ever returned by the API.
 */
public record ChannelTarget(String target, String signingSecret) {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,255}\\.[^@\\s]{2,}$");
    private static final Pattern ROUTING_KEY = Pattern.compile("^[A-Za-z0-9]{20,64}$");
    private static final int MAX_RECIPIENTS = 20;
    private static final int MIN_SECRET = 16;

    @Override
    public String toString() {
        return "ChannelTarget[redacted]";
    }

    /** Validates and normalises a target for the given type. */
    public static ChannelTarget of(ChannelType type, String target, String signingSecret) {
        if (target == null || target.isBlank()) {
            throw invalid("A destination is required");
        }
        String value = target.trim();
        switch (type) {
            case EMAIL -> value = String.join(",", emailAddresses(value));
            case SLACK, MS_TEAMS, WEBHOOK -> requireUrl(value);
            case PAGERDUTY -> {
                if (!ROUTING_KEY.matcher(value).matches()) {
                    throw invalid("PagerDuty routing keys are 20-64 letters and digits");
                }
            }
        }
        String secret = null;
        if (type == ChannelType.WEBHOOK) {
            if (signingSecret == null || signingSecret.trim().length() < MIN_SECRET) {
                throw invalid("Webhook signing secrets must be at least " + MIN_SECRET + " characters");
            }
            secret = signingSecret.trim();
        }
        return new ChannelTarget(value, secret);
    }

    public List<String> emailAddresses() {
        return Arrays.asList(target.split(","));
    }

    /** Safe rendition for API responses: addresses as-is, URLs and keys masked. */
    public String display(ChannelType type) {
        return switch (type) {
            case EMAIL -> target.replace(",", ", ");
            case PAGERDUTY -> "••••" + target.substring(target.length() - 4);
            case SLACK, MS_TEAMS, WEBHOOK -> maskUrl(target);
        };
    }

    static String maskUrl(String url) {
        URI uri = URI.create(url);
        String tail = url.length() > 4 ? url.substring(url.length() - 4) : "";
        return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + "/••••" + tail;
    }

    private static List<String> emailAddresses(String value) {
        List<String> addresses = Arrays.stream(value.split("[,;\\s]+"))
                .map(String::trim).filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT)).distinct().toList();
        if (addresses.isEmpty() || addresses.size() > MAX_RECIPIENTS) {
            throw invalid("Provide between 1 and " + MAX_RECIPIENTS + " e-mail addresses");
        }
        addresses.stream().filter(a -> !EMAIL.matcher(a).matches()).findFirst().ifPresent(a -> {
            throw invalid("Invalid e-mail address: " + a);
        });
        return addresses;
    }

    private static void requireUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw invalid("Enter an absolute https:// URL");
            }
        } catch (URISyntaxException e) {
            throw invalid("Enter a valid URL");
        }
        if (value.length() > 2000) {
            throw invalid("URL is too long");
        }
    }

    private static DomainException invalid(String message) {
        return new DomainException("INVALID_CHANNEL", message);
    }
}
