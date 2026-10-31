package io.cranial.platform.analytics.engine;

import io.cranial.platform.analytics.model.DeterministicProvenance;
import io.cranial.platform.analytics.model.ForecastPointView;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DeterministicAnalyticsEngine {

    public record StatisticalSummary(
            double count,
            double sum,
            double mean,
            double min,
            double max,
            double variance,
            double stdDev,
            double q1,
            double median,
            double q3,
            double iqr
    ) {}

    public record AnomalyCandidate(
            String period,
            double actualValue,
            double expectedBaseline,
            double deviationScore,
            String direction, // "SPIKE" or "DROP"
            String severity,  // "LOW", "MEDIUM", "HIGH", "CRITICAL"
            String algorithm,
            String reason
    ) {}

    public StatisticalSummary computeStatistics(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return new StatisticalSummary(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);

        int n = sorted.size();
        double sum = 0.0;
        double min = sorted.get(0);
        double max = sorted.get(n - 1);

        for (double v : sorted) {
            sum += v;
        }
        double mean = sum / n;

        double sumSqDiff = 0.0;
        for (double v : sorted) {
            sumSqDiff += Math.pow(v - mean, 2);
        }
        double variance = n > 1 ? sumSqDiff / (n - 1) : 0.0;
        double stdDev = Math.sqrt(variance);

        double median = percentile(sorted, 50.0);
        double q1 = percentile(sorted, 25.0);
        double q3 = percentile(sorted, 75.0);
        double iqr = Math.max(0.0, q3 - q1);

        return new StatisticalSummary(n, sum, mean, min, max, variance, stdDev, q1, median, q3, iqr);
    }

    private double percentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0;
        if (sorted.size() == 1) return sorted.get(0);

        double index = (percentile / 100.0) * (sorted.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        double weight = index - lower;

        if (lower == upper) {
            return sorted.get(lower);
        }
        return sorted.get(lower) * (1.0 - weight) + sorted.get(upper) * weight;
    }

    public List<AnomalyCandidate> detectIqrAnomalies(List<String> periods, List<Double> values) {
        List<AnomalyCandidate> anomalies = new ArrayList<>();
        if (values == null || values.size() < 4) {
            return anomalies;
        }

        StatisticalSummary stats = computeStatistics(values);
        double iqr = stats.iqr();
        double lowerBound = stats.q1() - 1.5 * iqr;
        double upperBound = stats.q3() + 1.5 * iqr;
        double extremeUpperBound = stats.q3() + 3.0 * iqr;
        double extremeLowerBound = stats.q1() - 3.0 * iqr;

        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i);
            String period = i < periods.size() ? periods.get(i) : String.valueOf(i);

            if (v > upperBound) {
                double diff = iqr > 0 ? (v - stats.q3()) / iqr : (v - stats.mean());
                String severity = v >= extremeUpperBound ? "CRITICAL" : (diff > 2.0 ? "HIGH" : "MEDIUM");
                String reason = String.format("Value %.2f exceeds upper IQR bound %.2f (Q3=%.2f, IQR=%.2f, diff=%.2fx IQR)",
                        v, upperBound, stats.q3(), iqr, diff);
                anomalies.add(new AnomalyCandidate(period, v, stats.median(), Math.round(diff * 100.0) / 100.0, "SPIKE", severity, "IQR_OUTLIER", reason));
            } else if (v < lowerBound) {
                double diff = iqr > 0 ? (stats.q1() - v) / iqr : (stats.mean() - v);
                String severity = v <= extremeLowerBound ? "CRITICAL" : (diff > 2.0 ? "HIGH" : "MEDIUM");
                String reason = String.format("Value %.2f below lower IQR bound %.2f (Q1=%.2f, IQR=%.2f, diff=%.2fx IQR)",
                        v, lowerBound, stats.q1(), iqr, diff);
                anomalies.add(new AnomalyCandidate(period, v, stats.median(), Math.round(diff * 100.0) / 100.0, "DROP", severity, "IQR_OUTLIER", reason));
            }
        }
        return anomalies;
    }

    public List<AnomalyCandidate> detectZScoreAnomalies(List<String> periods, List<Double> values, double zThreshold) {
        List<AnomalyCandidate> anomalies = new ArrayList<>();
        if (values == null || values.size() < 4) {
            return anomalies;
        }

        StatisticalSummary stats = computeStatistics(values);
        if (stats.stdDev() <= 0.0001) {
            return anomalies;
        }

        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i);
            String period = i < periods.size() ? periods.get(i) : String.valueOf(i);
            double z = (v - stats.mean()) / stats.stdDev();

            if (Math.abs(z) >= zThreshold) {
                String direction = z > 0 ? "SPIKE" : "DROP";
                String severity = Math.abs(z) >= 3.5 ? "CRITICAL" : (Math.abs(z) >= 2.8 ? "HIGH" : "MEDIUM");
                String reason = String.format("Z-score of %.2f exceeds statistical threshold of %.2f (mean=%.2f, stdDev=%.2f)",
                        z, zThreshold, stats.mean(), stats.stdDev());
                anomalies.add(new AnomalyCandidate(period, v, stats.mean(), Math.round(Math.abs(z) * 100.0) / 100.0, direction, severity, "Z_SCORE", reason));
            }
        }
        return anomalies;
    }

    public List<ForecastPointView> computeForecast(List<Double> historicalValues, int horizon, double alpha) {
        List<ForecastPointView> forecast = new ArrayList<>();
        if (historicalValues == null || historicalValues.size() < 3) {
            return List.of(new ForecastPointView(
                    "t+1",
                    null,
                    null,
                    null,
                    0.0,
                    "EXPONENTIAL_SMOOTHING",
                    false
            ));
        }

        StatisticalSummary stats = computeStatistics(historicalValues);

        // Exponential smoothing: S_t = alpha * Y_t + (1 - alpha) * S_{t-1}
        double s = historicalValues.get(0);
        for (int i = 1; i < historicalValues.size(); i++) {
            s = alpha * historicalValues.get(i) + (1.0 - alpha) * s;
        }

        double marginOfError = 1.96 * (stats.stdDev() / Math.sqrt(historicalValues.size()));

        for (int h = 1; h <= Math.max(1, horizon); h++) {
            double predicted = Math.round(s * 100.0) / 100.0;
            double lower = Math.max(0.0, Math.round((s - marginOfError * Math.sqrt(h)) * 100.0) / 100.0);
            double upper = Math.round((s + marginOfError * Math.sqrt(h)) * 100.0) / 100.0;

            forecast.add(new ForecastPointView(
                    "t+" + h,
                    predicted,
                    lower,
                    upper,
                    0.95,
                    "EXPONENTIAL_SMOOTHING",
                    true
            ));
        }

        return forecast;
    }

    public DeterministicProvenance createProvenance(String formula, List<Object> operands, Map<String, Object> params) {
        return new DeterministicProvenance(
                formula,
                "CRANIAL_DETERMINISTIC_STATISTICAL_ENGINE_V1",
                operands != null ? operands : List.of(),
                params != null ? params : Map.of(),
                Instant.now()
        );
    }
}

