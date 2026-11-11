package com.payops.identity.application.service;

import com.payops.identity.domain.TokenPurpose;
import com.payops.identity.domain.User;
import com.payops.notification.application.port.in.EmailSender;
import com.payops.notification.domain.BrandedEmail;
import com.payops.notification.domain.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Security e-mails of the identity module. Messages are queued in the
 * notification outbox inside the current transaction.
 */
@Component
@RequiredArgsConstructor
class IdentityEmails {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    private final EmailSender emailSender;
    private final OnboardingSettings settings;

    void passwordReset(User user, String token, String ipAddress) {
        send(user.organizationId(), user.email(), "Reset your PayOps360 password", "PASSWORD_RESET",
                BrandedEmail.titled("Reset your password")
                        .paragraph("Hi " + user.fullName() + ",")
                        .paragraph("We received a request to reset the password for your PayOps360 account ("
                                + user.email() + "). The link below works once and expires in "
                                + describe(TokenPurpose.PASSWORD_RESET.ttl()) + ".")
                        .action("Choose a new password", link("/reset-password", token))
                        .footnote("If you did not request this, you can ignore this e-mail; your password stays "
                                + "unchanged. The request came from IP address " + ipAddress + "."));
    }

    void passwordChanged(User user, Instant at, String ipAddress) {
        send(user.organizationId(), user.email(), "Your PayOps360 password was changed", "PASSWORD_CHANGED",
                BrandedEmail.titled("Your password was changed")
                        .paragraph("Hi " + user.fullName() + ",")
                        .paragraph("The password for " + user.email() + " was reset on " + WHEN.format(at)
                                + " from IP address " + ipAddress + ". For your protection, every active session "
                                + "has been signed out.")
                        .action("Sign in", settings.appBaseUrl() + "/login")
                        .footnote("If you did not make this change, contact your PayOps360 administrator "
                                + "immediately."));
    }

    void invitation(User invitee, String organizationName, String inviterName, String token) {
        send(invitee.organizationId(), invitee.email(),
                inviterName + " invited you to " + organizationName + " on PayOps360", "INVITATION",
                BrandedEmail.titled("You're invited to " + organizationName)
                        .paragraph("Hi " + invitee.fullName() + ",")
                        .paragraph(inviterName + " has invited you to join " + organizationName
                                + " on PayOps360, the payment operations command center, with the "
                                + humanize(invitee.role().name()) + " role.")
                        .paragraph("Accept the invitation to choose your password and sign in. The link expires in "
                                + describe(TokenPurpose.INVITATION.ttl()) + ".")
                        .action("Accept invitation", link("/accept-invite", token))
                        .footnote("If you were not expecting this invitation, you can ignore this e-mail."));
    }

    void emailVerification(User user, String organizationName, String token) {
        send(user.organizationId(), user.email(), "Confirm your e-mail address for PayOps360", "EMAIL_VERIFICATION",
                BrandedEmail.titled("Confirm your e-mail address")
                        .paragraph("Hi " + user.fullName() + ",")
                        .paragraph("Thanks for creating " + organizationName + " on PayOps360. Confirm that "
                                + user.email() + " belongs to you to activate your administrator account. The link "
                                + "expires in " + describe(TokenPurpose.EMAIL_VERIFICATION.ttl()) + ".")
                        .action("Verify e-mail address", link("/verify-email", token))
                        .footnote("If you did not sign up for PayOps360, you can ignore this e-mail; the account "
                                + "stays inactive."));
    }

    /** Sent instead of a verification link when someone signs up with an already registered address. */
    void signupForExistingAccount(String email, Long organizationId) {
        send(organizationId, email, "Sign-up attempt with your PayOps360 e-mail", "SIGNUP_EXISTING_ACCOUNT",
                BrandedEmail.titled("You already have an account")
                        .paragraph("Someone, hopefully you, tried to create a new PayOps360 organisation using "
                                + email + ". This address already belongs to a PayOps360 account, so nothing was "
                                + "created.")
                        .paragraph("If you have forgotten your password, you can reset it.")
                        .action("Reset password", settings.appBaseUrl() + "/forgot-password")
                        .footnote("If this was not you, no action is needed."));
    }

    private void send(Long organizationId, String recipient, String subject, String category, BrandedEmail email) {
        emailSender.send(new EmailMessage(organizationId, recipient, subject, category, email.render()));
    }

    private String link(String path, String token) {
        return settings.appBaseUrl() + path + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String describe(Duration ttl) {
        return ttl.toMinutes() < 120 ? ttl.toMinutes() + " minutes" : ttl.toHours() + " hours";
    }

    private static String humanize(String constant) {
        String words = constant.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
