package com.payops.identity.application.port.output;

import java.util.List;

public interface PasswordHistoryRepository {

    List<String> recentHashes(long userId, int depth);

    void add(long userId, String passwordHash);
}
