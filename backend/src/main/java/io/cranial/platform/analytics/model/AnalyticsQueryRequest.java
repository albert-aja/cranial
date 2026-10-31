package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AnalyticsQueryRequest(
        @JsonProperty("dataset_code")
        String datasetCode,

        @JsonProperty("natural_language_query")
        String naturalLanguageQuery,

        @JsonProperty("time_range")
        String timeRange, // e.g. "CURRENT_MONTH", "LAST_3_MONTHS", "CUSTOM"

        @JsonProperty("start_date")
        String startDate,

        @JsonProperty("end_date")
        String endDate,

        @JsonProperty("timezone")
        String timezone, // e.g. "Asia/Jakarta", "UTC"

        @JsonProperty("granularity")
        String granularity, // "DAILY", "WEEKLY", "MONTHLY"

        @JsonProperty("metrics")
        List<String> metrics, // e.g. ["actual_spent", "budget_limit"]

        @JsonProperty("group_by")
        String groupBy, // e.g. "category"

        @JsonProperty("filters")
        Map<String, Object> filters,

        @JsonProperty("include_forecast")
        Boolean includeForecast,

        @JsonProperty("forecast_horizon")
        Integer forecastHorizon, // number of future periods

        @JsonProperty("detect_anomalies")
        Boolean detectAnomalies,

        @JsonProperty("bypass_cache")
        Boolean bypassCache,

        @JsonProperty("locale")
        String locale // "en" or "id"
) {
}

