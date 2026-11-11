package com.payops.notification.adapter.config;

import com.payops.notification.adapter.out.mail.LoggingMailTransport;
import com.payops.notification.adapter.out.mail.SmtpMailTransport;
import com.payops.notification.application.port.out.MailTransport;
import com.payops.notification.application.service.ChannelDeliverySettings;
import com.payops.notification.application.service.NotificationSettings;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

/**
 * Chooses the mail transport: SMTP when {@code spring.mail.host} is set
 * (e.g. via the {@code SPRING_MAIL_HOST} environment variable), otherwise a
 * log-only fallback so every flow still works without a mail server.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(NotificationConfig.MailSettingsProperties.class)
class NotificationConfig {

    @Bean
    MailTransport mailTransport(Environment environment, ObjectProvider<JavaMailSender> mailSender,
                                MailSettingsProperties properties) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        JavaMailSender sender = mailSender.getIfAvailable();
        if (StringUtils.hasText(environment.getProperty("spring.mail.host")) && sender != null) {
            log.info("E-mail delivery via SMTP host {}", environment.getProperty("spring.mail.host"));
            return new SmtpMailTransport(sender, properties.from());
        }
        log.info("spring.mail.host not set: e-mail is recorded in the outbox and logged{}",
                dev ? " (dev mailbox at /api/v1/dev/mailbox)" : " without content");
        return new LoggingMailTransport(dev);
    }

    @Bean
    NotificationSettings notificationSettings(Environment environment, MailSettingsProperties properties) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        return new NotificationSettings(!dev, Math.max(1, properties.batchSize()));
    }

    /**
     * Channel delivery settings. Plain-http and private-network webhook targets
     * are only accepted in the dev profile (or when explicitly allowed).
     */
    @Bean
    ChannelDeliverySettings channelDeliverySettings(Environment environment,
                                                    @Value("${payops.app.base-url:http://localhost:3417}") String baseUrl,
                                                    @Value("${payops.notifications.batch-size:50}") int batchSize,
                                                    @Value("${payops.notifications.allow-insecure-targets:#{null}}") Boolean allowInsecure) {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        boolean insecure = allowInsecure != null ? allowInsecure : dev;
        if (insecure) {
            log.warn("Notification channels accept plain-http and private-network targets (development setting)");
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new ChannelDeliverySettings(Math.max(1, batchSize), insecure, base);
    }

    /**
     * @param from      sender address, e.g. {@code PayOps360 <no-reply@payops360.io>}
     * @param batchSize messages delivered per scheduler run
     */
    @ConfigurationProperties(prefix = "payops.mail")
    record MailSettingsProperties(String from, int batchSize) {
    }
}
