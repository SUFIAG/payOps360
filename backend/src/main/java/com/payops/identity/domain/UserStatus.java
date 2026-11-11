package com.payops.identity.domain;

public enum UserStatus {
    ACTIVE,
    DISABLED,
    /** Invited by an administrator; no usable password until the invitation is accepted. */
    INVITED,
    /** Self-registered organisation owner who has not confirmed their e-mail address yet. */
    PENDING_VERIFICATION
}
