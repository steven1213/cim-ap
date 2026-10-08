package com.cim.iam.server.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountLockRepository extends JpaRepository<AccountLock, String> {

    Optional<AccountLock> findByUsername(String username);

    void deleteByUsername(String username);
}
