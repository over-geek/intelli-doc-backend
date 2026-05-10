package com.intellidoc.security.repository;

import com.intellidoc.security.model.AppUserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

    Optional<AppUserEntity> findByEntraObjectId(String entraObjectId);

    Optional<AppUserEntity> findByEmailIgnoreCase(String email);

    @Query(
            value = """
                    SELECT * FROM app_user
                    WHERE roles ?| ARRAY['ROLE_ADMIN', 'ROLE_SUPER_ADMIN']
                    """,
            nativeQuery = true)
    java.util.List<AppUserEntity> findAdminUsers();
}
