package io.cranial.platform.orchestration.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.orchestration.model.SchemaValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class SchemaValidator {

    private final ObjectMapper objectMapper;

    public SchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SchemaValidationResult validate(String jsonSchemaString, Object data) {
        if (jsonSchemaString == null || jsonSchemaString.isBlank()) {
            return SchemaValidationResult.success();
        }

        try {
            JsonNode schemaNode = objectMapper.readTree(jsonSchemaString);
            JsonNode dataNode = data instanceof JsonNode jn ? jn : objectMapper.valueToTree(data);

            List<String> errors = new ArrayList<>();
            validateNode("", schemaNode, dataNode, errors);

            if (errors.isEmpty()) {
                return SchemaValidationResult.success();
            }
            return SchemaValidationResult.failure(errors);
        } catch (Exception ex) {
            return SchemaValidationResult.failure(List.of("Schema parsing/validation error: " + ex.getMessage()));
        }
    }

    private void validateNode(String path, JsonNode schema, JsonNode data, List<String> errors) {
        if (schema == null || schema.isEmpty()) {
            return;
        }

        // 1. Check type
        if (schema.has("type")) {
            String expectedType = schema.get("type").asText();
            if (!matchesType(expectedType, data)) {
                errors.add("Field '" + (path.isEmpty() ? "root" : path) + "' expected type '" + expectedType + "' but was '" + data.getNodeType().name().toLowerCase() + "'");
                return; // Type mismatch, skip deeper checks for this branch
            }
        }

        // 2. Object validation: required properties
        if (data.isObject()) {
            if (schema.has("required") && schema.get("required").isArray()) {
                for (JsonNode req : schema.get("required")) {
                    String propName = req.asText();
                    if (!data.has(propName) || data.get(propName).isNull()) {
                        String fieldPath = path.isEmpty() ? propName : path + "." + propName;
                        errors.add("Required field missing: '" + fieldPath + "'");
                    }
                }
            }

            // Object validation: properties
            if (schema.has("properties") && schema.get("properties").isObject()) {
                JsonNode propsSchema = schema.get("properties");
                Iterator<String> fieldNames = data.fieldNames();
                while (fieldNames.hasNext()) {
                    String fieldName = fieldNames.next();
                    if (propsSchema.has(fieldName)) {
                        String fieldPath = path.isEmpty() ? fieldName : path + "." + fieldName;
                        validateNode(fieldPath, propsSchema.get(fieldName), data.get(fieldName), errors);
                    }
                }
            }
        }

        // 3. Array validation: items
        if (data.isArray() && schema.has("items")) {
            JsonNode itemsSchema = schema.get("items");
            for (int i = 0; i < data.size(); i++) {
                validateNode(path + "[" + i + "]", itemsSchema, data.get(i), errors);
            }
        }
    }

    private boolean matchesType(String expectedType, JsonNode data) {
        if (data == null || data.isNull()) {
            return false;
        }
        return switch (expectedType.toLowerCase()) {
            case "string" -> data.isTextual();
            case "number" -> data.isNumber();
            case "integer" -> data.isInt() || data.isLong();
            case "boolean" -> data.isBoolean();
            case "object" -> data.isObject();
            case "array" -> data.isArray();
            default -> true;
        };
    }
}

