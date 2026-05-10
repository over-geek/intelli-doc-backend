package com.intellidoc.admin.controller;

import com.intellidoc.admin.service.ReferenceDataAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final ReferenceDataAdminService referenceDataAdminService;

    public AdminCategoryController(ReferenceDataAdminService referenceDataAdminService) {
        this.referenceDataAdminService = referenceDataAdminService;
    }

    @GetMapping
    public ResponseEntity<List<ReferenceDataAdminService.CategoryView>> listCategories(
            @RequestParam(name = "active", required = false) Boolean active) {
        return ResponseEntity.ok(referenceDataAdminService.listCategories(active));
    }

    @GetMapping("/{categoryId}")
    public ResponseEntity<ReferenceDataAdminService.CategoryView> getCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(referenceDataAdminService.getCategory(categoryId));
    }

    @PostMapping
    public ResponseEntity<ReferenceDataAdminService.CategoryView> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        ReferenceDataAdminService.CategoryView created = referenceDataAdminService.createCategory(request.toCommand());
        return ResponseEntity.created(URI.create("/api/admin/categories/" + created.id())).body(created);
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<ReferenceDataAdminService.CategoryView> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(referenceDataAdminService.updateCategory(categoryId, request.toCommand()));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> deleteCategory(@PathVariable UUID categoryId) {
        referenceDataAdminService.deleteCategory(categoryId);
        return ResponseEntity.noContent().build();
    }

    public record CategoryRequest(
            @NotBlank String name,
            String description,
            String slug,
            @Min(0) int displayOrder,
            Boolean active) {
        ReferenceDataAdminService.CategoryCommand toCommand() {
            return new ReferenceDataAdminService.CategoryCommand(
                    name, description, slug, displayOrder, active == null || active);
        }
    }
}
