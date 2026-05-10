package com.intellidoc.admin.repository;

import com.intellidoc.admin.model.CategoryEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);

    List<CategoryEntity> findAllByOrderByDisplayOrderAscNameAsc();

    List<CategoryEntity> findAllByActiveOrderByDisplayOrderAscNameAsc(boolean active);

    Optional<CategoryEntity> findBySlugIgnoreCase(String slug);
}
