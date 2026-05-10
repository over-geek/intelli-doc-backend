package com.intellidoc.admin.controller;

import com.intellidoc.admin.service.ReferenceDataAdminService;
import jakarta.validation.Valid;
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
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {

    private final ReferenceDataAdminService referenceDataAdminService;

    public AdminDepartmentController(ReferenceDataAdminService referenceDataAdminService) {
        this.referenceDataAdminService = referenceDataAdminService;
    }

    @GetMapping
    public ResponseEntity<List<ReferenceDataAdminService.DepartmentView>> listDepartments(
            @RequestParam(name = "active", required = false) Boolean active) {
        return ResponseEntity.ok(referenceDataAdminService.listDepartments(active));
    }

    @GetMapping("/{departmentId}")
    public ResponseEntity<ReferenceDataAdminService.DepartmentView> getDepartment(@PathVariable UUID departmentId) {
        return ResponseEntity.ok(referenceDataAdminService.getDepartment(departmentId));
    }

    @PostMapping
    public ResponseEntity<ReferenceDataAdminService.DepartmentView> createDepartment(
            @Valid @RequestBody DepartmentRequest request) {
        ReferenceDataAdminService.DepartmentView created =
                referenceDataAdminService.createDepartment(request.toCommand());
        return ResponseEntity.created(URI.create("/api/admin/departments/" + created.id())).body(created);
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<ReferenceDataAdminService.DepartmentView> updateDepartment(
            @PathVariable UUID departmentId,
            @Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(referenceDataAdminService.updateDepartment(departmentId, request.toCommand()));
    }

    @DeleteMapping("/{departmentId}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable UUID departmentId) {
        referenceDataAdminService.deleteDepartment(departmentId);
        return ResponseEntity.noContent().build();
    }

    public record DepartmentRequest(@NotBlank String name, @NotBlank String code, Boolean active) {
        ReferenceDataAdminService.DepartmentCommand toCommand() {
            return new ReferenceDataAdminService.DepartmentCommand(name, code, active == null || active);
        }
    }
}
