package io.cranial.platform.admin;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.cranial.platform.orchestration.model.SchemaValidationResult;
import io.cranial.platform.orchestration.model.SchemaView;
import io.cranial.platform.orchestration.service.SchemaRegistryService;
import io.cranial.platform.platform.common.ApiResponse;
import io.cranial.platform.platform.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/v1/schemas")
@Tag(name = "Admin Schema Registry", description = "Manage JSON schemas for request, response, and tool validation")
@SecurityRequirement(name = OpenApiConfig.SCHEME_ADMIN_JWT)
public class AdminSchemaController {

    private final SchemaRegistryService schemaService;

    public AdminSchemaController(SchemaRegistryService schemaService) {
        this.schemaService = schemaService;
    }

    @GetMapping
    @Operation(summary = "List all schemas in registry")
    public ApiResponse<List<SchemaView>> listSchemas() {
        return ApiResponse.ok(schemaService.listSchemas());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get schema by id")
    public ApiResponse<SchemaView> getSchema(@PathVariable UUID id) {
        return ApiResponse.ok(schemaService.getSchema(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'PLATFORM_OPERATOR')")
    @Operation(summary = "Create or update schema definition")
    public ApiResponse<SchemaView> saveSchema(@Valid @RequestBody SaveSchemaRequest req) {
        SchemaView view = schemaService.saveSchema(
                req.id(),
                req.schemaCode(),
                req.name(),
                req.description(),
                req.schemaType(),
                req.version(),
                req.schemaDefinitionJson(),
                req.isStrict() == null || req.isStrict(),
                req.status()
        );
        return ApiResponse.ok(view);
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Test validate arbitrary payload against registered schema")
    public ApiResponse<SchemaValidationResult> validatePayload(
            @PathVariable UUID id,
            @RequestBody ValidatePayloadRequest req
    ) {
        return ApiResponse.ok(schemaService.validateData(id, req.data()));
    }

    public record SaveSchemaRequest(
            UUID id,
            @NotBlank @JsonAlias({"schemaCode", "schema_code"}) String schemaCode,
            @NotBlank String name,
            String description,
            @NotBlank @JsonAlias({"schemaType", "schema_type"}) String schemaType,
            Integer version,
            @NotBlank @JsonAlias({"schemaDefinitionJson", "schema_definition_json"}) String schemaDefinitionJson,
            @JsonAlias({"isStrict", "is_strict"}) Boolean isStrict,
            String status
    ) {
    }

    public record ValidatePayloadRequest(
            Object data
    ) {
    }
}

