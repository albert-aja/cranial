package io.cranial.platform.customer.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ProductDiscoveryResult(
        @JsonProperty("customer_goal") String customerGoal,
        @JsonProperty("recommended_features") List<RecommendedFeatureView> recommendedFeatures,
        @JsonProperty("money_management_boundary_disclaimer") String moneyManagementBoundaryDisclaimer
) {}

