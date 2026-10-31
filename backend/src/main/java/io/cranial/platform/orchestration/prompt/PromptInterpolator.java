package io.cranial.platform.orchestration.prompt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PromptInterpolator {

    private static final Pattern DOUBLE_CURLY = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*\\}\\}");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public PromptInterpolator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<String> parseRequiredVariables(String inputVariablesJson) {
        if (inputVariablesJson == null || inputVariablesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(inputVariablesJson, STRING_LIST_TYPE);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    public void validateRequiredVariables(List<String> requiredVariables, Map<String, Object> inputs) {
        if (requiredVariables == null || requiredVariables.isEmpty()) {
            return;
        }

        List<String> missing = new ArrayList<>();
        for (String req : requiredVariables) {
            if (inputs == null || !inputs.containsKey(req) || inputs.get(req) == null) {
                missing.add(req);
            }
        }

        if (!missing.isEmpty()) {
            throw new CranialException(
                    ErrorCode.VALIDATION_ERROR,
                    "Missing required prompt template variables: " + String.join(", ", missing)
            );
        }
    }

    public String interpolate(String template, Map<String, Object> inputs) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (inputs == null || inputs.isEmpty()) {
            return template;
        }

        Matcher matcher = DOUBLE_CURLY.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object val = inputs.get(varName);
            String replacement = val != null ? val.toString() : "";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}

