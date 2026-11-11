package com.payops.identity.application.port.out;

import java.util.List;

public interface RecoveryCodeRepository {

    void replaceAll(long userId, List<String> codeHashes);

    /** Marks the code as used. Returns false when absent or already used. */
    boolean consume(long userId, String codeHash);

    int remaining(long userId);

    void deleteAll(long userId);
}
