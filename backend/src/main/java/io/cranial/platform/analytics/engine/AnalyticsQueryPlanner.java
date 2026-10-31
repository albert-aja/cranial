package io.cranial.platform.analytics.engine;

import io.cranial.platform.analytics.model.AnalyticsQueryRequest;
import io.cranial.platform.analytics.model.ClarificationPrompt;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class AnalyticsQueryPlanner {

    public record PlannedQuery(
            String targetDatasetCode,
            String timeRange,
            String startDate,
            String endDate,
            String granularity,
            List<String> metrics,
            String groupBy,
            boolean detectAnomalies,
            boolean includeForecast,
            ClarificationPrompt clarification
    ) {}

    public PlannedQuery plan(AnalyticsQueryRequest req, int datasetMaxWindowDays) {
        String nl = req != null && req.naturalLanguageQuery() != null ? req.naturalLanguageQuery().trim().toLowerCase(Locale.ROOT) : "";
        String datasetCode = req != null && req.datasetCode() != null && !req.datasetCode().isBlank() ? req.datasetCode().trim() : null;

        boolean isId = req != null && "id".equalsIgnoreCase(req.locale());
        if (nl.contains("berapa") || nl.contains("pengeluaran") || nl.contains("anggaran") || nl.contains("anomali") || nl.contains("arus kas")) {
            isId = true;
        }

        // 1. Check for vague or missing queries needing clarification
        if ((datasetCode == null || datasetCode.isBlank()) && (nl.isBlank() || isVagueQuery(nl))) {
            List<String> missingFields = new ArrayList<>();
            if (datasetCode == null) missingFields.add("dataset_code");
            missingFields.add("time_range");

            String msg = isId
                    ? "Kueri analitika membutuhkan klarifikasi: Harap tentukan dataset target (mis. 'peggy_budget_monthly_2026' atau 'peggy_cashflow_daily_q4') dan rentang waktu yang ingin dianalisis."
                    : "Analytics query requires clarification: Please specify the target dataset (e.g., 'peggy_budget_monthly_2026' or 'peggy_cashflow_daily_q4') and the time window to analyze.";

            List<String> suggestions = isId
                    ? List.of(
                    "Bandingkan realisasi pengeluaran vs anggaran Peggy bulan Oktober 2026",
                    "Deteksi anomali lonjakan pengeluaran pada arus kas harian Q4",
                    "Tampilkan tren tabungan dan dana darurat 3 bulan terakhir"
            )
                    : List.of(
                    "Compare Peggy actual spending vs monthly budget targets for October 2026",
                    "Detect spending spikes or outflow anomalies in daily cash flow for Q4",
                    "Show 3-month savings rate and category breakdown"
            );

            return new PlannedQuery(
                    datasetCode,
                    null,
                    null,
                    null,
                    "MONTHLY",
                    List.of(),
                    null,
                    false,
                    false,
                    new ClarificationPrompt(true, missingFields, msg, suggestions)
            );
        }

        // 2. Infer dataset if not explicitly supplied
        if (datasetCode == null || datasetCode.isBlank()) {
            if (nl.contains("arus kas") || nl.contains("cash flow") || nl.contains("cashflow") || nl.contains("harian") || nl.contains("daily") || nl.contains("lonjakan")) {
                datasetCode = "peggy_cashflow_daily_q4";
            } else {
                datasetCode = "peggy_budget_monthly_2026";
            }
        }

        // 3. Infer metrics and groupings
        List<String> metrics = new ArrayList<>();
        if (req != null && req.metrics() != null && !req.metrics().isEmpty()) {
            metrics.addAll(req.metrics());
        } else {
            if ("peggy_cashflow_daily_q4".equalsIgnoreCase(datasetCode)) {
                metrics.add("outflow");
                metrics.add("inflow");
                metrics.add("net_flow");
            } else {
                metrics.add("actual_spent");
                metrics.add("budget_limit");
                metrics.add("variance_amount");
            }
        }

        String groupBy = req != null && req.groupBy() != null ? req.groupBy() : null;
        if (groupBy == null && (nl.contains("kategori") || nl.contains("category") || nl.contains("breakdown") || nl.contains("50-30-20"))) {
            groupBy = "category";
        }

        boolean detectAnomalies = (req != null && Boolean.TRUE.equals(req.detectAnomalies()))
                || nl.contains("anomali") || nl.contains("anomaly") || nl.contains("tidak wajar") || nl.contains("lonjakan") || nl.contains("spike");

        boolean includeForecast = (req != null && Boolean.TRUE.equals(req.includeForecast()))
                || nl.contains("proyeksi") || nl.contains("forecast") || nl.contains("prediksi") || nl.contains("bulan depan") || nl.contains("next month");

        String granularity = req != null && req.granularity() != null ? req.granularity() : ("peggy_cashflow_daily_q4".equalsIgnoreCase(datasetCode) ? "DAILY" : "MONTHLY");

        // 4. Validate query window protection
        if (req != null && req.startDate() != null && req.endDate() != null) {
            try {
                LocalDate start = LocalDate.parse(req.startDate());
                LocalDate end = LocalDate.parse(req.endDate());
                long days = ChronoUnit.DAYS.between(start, end);
                if (days > datasetMaxWindowDays) {
                    throw new CranialException(ErrorCode.VALIDATION_ERROR,
                            String.format("Requested time range of %d days exceeds maximum allowed query window of %d days for dataset '%s'",
                                    days, datasetMaxWindowDays, datasetCode));
                }
            } catch (Exception e) {
                if (e instanceof CranialException ce) throw ce;
                // ignore parse failure, let engine handle or use defaults
            }
        }

        return new PlannedQuery(
                datasetCode,
                req != null && req.timeRange() != null ? req.timeRange() : "CURRENT_PERIOD",
                req != null ? req.startDate() : null,
                req != null ? req.endDate() : null,
                granularity,
                metrics,
                groupBy,
                detectAnomalies,
                includeForecast,
                null
        );
    }

    private boolean isVagueQuery(String nl) {
        if (nl.length() < 6) return true;
        return nl.matches("^(hitung|analyze|cek|check|summary|ringkas|data|report|angka|laporan|show me).*$")
                && !nl.contains("peggy")
                && !nl.contains("budget")
                && !nl.contains("anggaran")
                && !nl.contains("pengeluaran")
                && !nl.contains("cashflow")
                && !nl.contains("arus kas");
    }
}

