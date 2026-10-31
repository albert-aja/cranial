package io.cranial.platform.analytics.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AnalyticsReportResult(
        @JsonProperty("report_id")
        String reportId,

        @JsonProperty("dataset_code")
        String datasetCode,

        @JsonProperty("title")
        String title,

        @JsonProperty("time_range")
        String timeRange,

        @JsonProperty("timezone")
        String timezone,

        @JsonProperty("source_freshness_at")
        Instant sourceFreshnessAt,

        @JsonProperty("data_completeness_ratio")
        double dataCompletenessRatio,

        @JsonProperty("cached")
        boolean cached,

        @JsonProperty("kpis")
        List<KpiCardView> kpis,

        @JsonProperty("time_series")
        List<TimeSeriesPointView> timeSeries,

        @JsonProperty("breakdown")
        List<BreakdownItemView> breakdown,

        @JsonProperty("tabular_rows")
        List<Map<String, Object>> tabularRows,

        @JsonProperty("anomalies")
        List<AnomalyItemView> anomalies,

        @JsonProperty("forecast")
        List<ForecastPointView> forecast,

        @JsonProperty("provenance")
        DeterministicProvenance provenance,

        @JsonProperty("natural_language_summary")
        String naturalLanguageSummary,

        @JsonProperty("clarification")
        ClarificationPrompt clarification,

        @JsonProperty("data_quality_warnings")
        List<String> dataQualityWarnings,

        @JsonProperty("duration_ms")
        long durationMs
) {
}

