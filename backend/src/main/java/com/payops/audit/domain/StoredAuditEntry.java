package com.payops.audit.domain;

public record StoredAuditEntry(long id, AuditEntry entry, String previousHash, String entryHash) {

    public boolean isIntact() {
        return entry.chainHash(previousHash).equals(entryHash);
    }
}
