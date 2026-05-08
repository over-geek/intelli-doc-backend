package com.intellidoc.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByEntraObjectId(String entraObjectId);

    Optional<AppUserEntity> findByEmailIgnoreCase(String email);
}
