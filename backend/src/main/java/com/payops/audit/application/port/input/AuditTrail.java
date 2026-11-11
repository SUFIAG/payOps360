package com.payops.audit.application.port.input;

import com.payops.audit.domain.AuditEntry;

/**
 * Input port every module uses to record security-relevant events.
 * Recording never fails the calling business operation.
 */
public interface AuditTrail {

    void record(AuditEntry entry);
}
