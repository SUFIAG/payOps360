package com.payops.identity.application.port.output;

import com.payops.identity.domain.User;
import com.payops.shared.domain.PageQuery;
import com.payops.shared.domain.PageResult;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(long id);

    Optional<User> findByPublicId(long organizationId, UUID publicId);

    PageResult<User> findByOrganization(long organizationId, PageQuery page);

    boolean existsByEmail(String email);

    User save(User user);

    void delete(User user);
}
