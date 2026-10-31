package io.cranial.platform.provider.model;

import java.util.UUID;

public record SimulationResult(
        boolean allowed,
        String applicationCode,
        String capabilityCode,
        String taskCode,
        String dataSensitivity,
        String policyName,
        UUID preferredModelId,
        String preferredModelIdentifier,
        boolean preferredModelLocal,
        UUID fallbackModelId,
        String fallbackModelIdentifier,
        boolean fallbackModelLocal,
        boolean paidFallbackAllowed,
        String decisionReason,
        String blockedReason
) {
    public static SimulationResult success(
            String applicationCode,
            String capabilityCode,
            String taskCode,
            String dataSensitivity,
            String policyName,
            UUID preferredModelId,
            String preferredModelIdentifier,
            boolean preferredModelLocal,
            UUID fallbackModelId,
            String fallbackModelIdentifier,
            boolean fallbackModelLocal,
            boolean paidFallbackAllowed,
            String decisionReason
    ) {
        return new SimulationResult(
                true,
                applicationCode,
                capabilityCode,
                taskCode,
                dataSensitivity,
                policyName,
                preferredModelId,
                preferredModelIdentifier,
                preferredModelLocal,
                fallbackModelId,
                fallbackModelIdentifier,
                fallbackModelLocal,
                paidFallbackAllowed,
                decisionReason,
                null
        );
    }

    public static SimulationResult blocked(
            String applicationCode,
            String capabilityCode,
            String taskCode,
            String dataSensitivity,
            String policyName,
            String blockedReason
    ) {
        return new SimulationResult(
                false,
                applicationCode,
                capabilityCode,
                taskCode,
                dataSensitivity,
                policyName,
                null,
                null,
                false,
                null,
                null,
                false,
                false,
                "BLOCKED",
                blockedReason
        );
    }
}

