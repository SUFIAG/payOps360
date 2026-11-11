package com.payops.notification.adapter.out.mail;

import com.payops.notification.application.port.out.MailTransport;
import com.payops.notification.domain.EmailStatus;
import com.payops.notification.domain.OutboxEmail;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback when no SMTP server is configured: records a log line instead of
 * sending. The body (which may contain one-time links) is only printed when
 * {@code revealContent} is true, i.e. in the dev profile; elsewhere the
 * recipient is masked and the content is never logged.
 */
@Slf4j
public class LoggingMailTransport implements MailTransport {

    private final boolean revealContent;

    public LoggingMailTransport(boolean revealContent) {
        this.revealContent = revealContent;
    }

    @Override
    public EmailStatus deliver(OutboxEmail email) {
        if (revealContent) {
            log.info("[mail:LOGGED] to={} subject=\"{}\" category={}\n{}", email.recipient(), email.subject(),
                    email.category(), email.textBody());
        } else {
            log.info("[mail:LOGGED] to={} category={} id={} (no SMTP server configured; content withheld)",
                    mask(email.recipient()), email.category(), email.publicId());
        }
        return EmailStatus.LOGGED;
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? email.substring(at) : "");
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
