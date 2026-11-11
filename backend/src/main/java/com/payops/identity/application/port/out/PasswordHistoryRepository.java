package com.payops.identity.application.port.out;

import java.util.List;

public interface PasswordHistoryRepository {

    List<String> recentHashes(long userId, int depth);

    void add(long userId, String passwordHash);
}
