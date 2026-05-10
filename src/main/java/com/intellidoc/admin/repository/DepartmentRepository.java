package com.intellidoc.admin.repository;

import com.intellidoc.admin.model.DepartmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<DepartmentEntity, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    List<DepartmentEntity> findAllByOrderByNameAsc();

    List<DepartmentEntity> findAllByActiveOrderByNameAsc(boolean active);

    Optional<DepartmentEntity> findByCodeIgnoreCase(String code);
}
