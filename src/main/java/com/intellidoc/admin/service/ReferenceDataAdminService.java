package com.intellidoc.admin.service;

import com.intellidoc.admin.model.CategoryEntity;
import com.intellidoc.admin.model.DepartmentEntity;
import com.intellidoc.admin.repository.CategoryRepository;
import com.intellidoc.admin.repository.DepartmentRepository;
import com.intellidoc.shared.error.BadRequestException;
import com.intellidoc.shared.error.ConflictException;
import com.intellidoc.shared.error.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReferenceDataAdminService {

    private final CategoryRepository categoryRepository;
    private final DepartmentRepository departmentRepository;
    private final SlugService slugService;

    public ReferenceDataAdminService(
            CategoryRepository categoryRepository,
            DepartmentRepository departmentRepository,
            SlugService slugService) {
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.slugService = slugService;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> listCategories(Boolean active) {
        List<CategoryEntity> categories = active == null
                ? categoryRepository.findAllByOrderByDisplayOrderAscNameAsc()
                : categoryRepository.findAllByActiveOrderByDisplayOrderAscNameAsc(active);
        return categories.stream().map(this::toCategoryView).toList();
    }

    @Transactional(readOnly = true)
    public CategoryView getCategory(UUID categoryId) {
        return toCategoryView(getCategoryEntity(categoryId));
    }

    @Transactional
    public CategoryView createCategory(CategoryCommand command) {
        String name = required(command.name(), "Category name");
        String slug = slugService.toSlug(command.slug() == null ? name : command.slug());

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("category_name_conflict", "A category with that name already exists.");
        }
        if (categoryRepository.existsBySlugIgnoreCase(slug)) {
            throw new ConflictException("category_slug_conflict", "A category with that slug already exists.");
        }

        CategoryEntity entity = new CategoryEntity();
        entity.setName(name);
        entity.setDescription(trimToNull(command.description()));
        entity.setSlug(slug);
        entity.setDisplayOrder(command.displayOrder());
        entity.setActive(command.active());
        return toCategoryView(categoryRepository.save(entity));
    }

    @Transactional
    public CategoryView updateCategory(UUID categoryId, CategoryCommand command) {
        CategoryEntity entity = getCategoryEntity(categoryId);
        String name = required(command.name(), "Category name");
        String slug = slugService.toSlug(command.slug() == null ? name : command.slug());

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, categoryId)) {
            throw new ConflictException("category_name_conflict", "A category with that name already exists.");
        }
        if (categoryRepository.existsBySlugIgnoreCaseAndIdNot(slug, categoryId)) {
            throw new ConflictException("category_slug_conflict", "A category with that slug already exists.");
        }

        entity.setName(name);
        entity.setDescription(trimToNull(command.description()));
        entity.setSlug(slug);
        entity.setDisplayOrder(command.displayOrder());
        entity.setActive(command.active());
        return toCategoryView(categoryRepository.save(entity));
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        CategoryEntity entity = getCategoryEntity(categoryId);
        try {
            categoryRepository.delete(entity);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "category_in_use",
                    "Category cannot be deleted because it is referenced by one or more documents.");
        }
    }

    @Transactional(readOnly = true)
    public List<DepartmentView> listDepartments(Boolean active) {
        List<DepartmentEntity> departments = active == null
                ? departmentRepository.findAllByOrderByNameAsc()
                : departmentRepository.findAllByActiveOrderByNameAsc(active);
        return departments.stream().map(this::toDepartmentView).toList();
    }

    @Transactional(readOnly = true)
    public DepartmentView getDepartment(UUID departmentId) {
        return toDepartmentView(getDepartmentEntity(departmentId));
    }

    @Transactional
    public DepartmentView createDepartment(DepartmentCommand command) {
        String name = required(command.name(), "Department name");
        String code = normalizeCode(required(command.code(), "Department code"));

        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("department_name_conflict", "A department with that name already exists.");
        }
        if (departmentRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException("department_code_conflict", "A department with that code already exists.");
        }

        DepartmentEntity entity = new DepartmentEntity();
        entity.setName(name);
        entity.setCode(code);
        entity.setActive(command.active());
        return toDepartmentView(departmentRepository.save(entity));
    }

    @Transactional
    public DepartmentView updateDepartment(UUID departmentId, DepartmentCommand command) {
        DepartmentEntity entity = getDepartmentEntity(departmentId);
        String name = required(command.name(), "Department name");
        String code = normalizeCode(required(command.code(), "Department code"));

        if (departmentRepository.existsByNameIgnoreCaseAndIdNot(name, departmentId)) {
            throw new ConflictException("department_name_conflict", "A department with that name already exists.");
        }
        if (departmentRepository.existsByCodeIgnoreCaseAndIdNot(code, departmentId)) {
            throw new ConflictException("department_code_conflict", "A department with that code already exists.");
        }

        entity.setName(name);
        entity.setCode(code);
        entity.setActive(command.active());
        return toDepartmentView(departmentRepository.save(entity));
    }

    @Transactional
    public void deleteDepartment(UUID departmentId) {
        DepartmentEntity entity = getDepartmentEntity(departmentId);
        try {
            departmentRepository.delete(entity);
            departmentRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "department_in_use",
                    "Department cannot be deleted because it is referenced by one or more documents.");
        }
    }

    CategoryEntity getCategoryEntity(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException(
                        "category_not_found",
                        "Category %s was not found.".formatted(categoryId)));
    }

    DepartmentEntity getDepartmentEntity(UUID departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new NotFoundException(
                        "department_not_found",
                        "Department %s was not found.".formatted(departmentId)));
    }

    private CategoryView toCategoryView(CategoryEntity entity) {
        return new CategoryView(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getSlug(),
                entity.getDisplayOrder(),
                entity.isActive());
    }

    private DepartmentView toDepartmentView(DepartmentEntity entity) {
        return new DepartmentView(entity.getId(), entity.getName(), entity.getCode(), entity.isActive());
    }

    private String required(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("invalid_reference_data_request", label + " is required.");
        }
        return value.trim();
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record CategoryCommand(String name, String description, String slug, int displayOrder, boolean active) {
    }

    public record CategoryView(
            UUID id, String name, String description, String slug, int displayOrder, boolean active) {
    }

    public record DepartmentCommand(String name, String code, boolean active) {
    }

    public record DepartmentView(UUID id, String name, String code, boolean active) {
    }
}
