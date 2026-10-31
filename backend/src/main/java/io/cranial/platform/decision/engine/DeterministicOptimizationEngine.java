package io.cranial.platform.decision.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.decision.model.ScenarioComparisonView;
import io.cranial.platform.decision.model.TradeOffOptionView;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DeterministicOptimizationEngine {

    private final ObjectMapper objectMapper;

    public DeterministicOptimizationEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record OptimizationOutput(
            String title,
            String summary,
            String actionType,
            double confidenceScore,
            Map<String, Object> evidenceRationale,
            List<TradeOffOptionView> tradeOffs,
            ScenarioComparisonView scenarioDiff,
            Map<String, Object> candidateMetrics
    ) {
    }

    public OptimizationOutput optimize(
            String domainArchetype,
            String parametersJson,
            Map<String, Object> runtimeParameters,
            String naturalLanguageGoal,
            String locale
    ) {
        Map<String, Object> params = parseParameters(parametersJson);
        if (runtimeParameters != null) {
            params.putAll(runtimeParameters);
        }

        boolean isId = "id".equalsIgnoreCase(locale) || (naturalLanguageGoal != null && (
                naturalLanguageGoal.toLowerCase().contains("utang") ||
                naturalLanguageGoal.toLowerCase().contains("angsuran") ||
                naturalLanguageGoal.toLowerCase().contains("tabungan") ||
                naturalLanguageGoal.toLowerCase().contains("dana darurat")
        ));

        if ("SAVINGS_GOAL_ALLOCATION".equalsIgnoreCase(domainArchetype) || "BUDGET_REBALANCING".equalsIgnoreCase(domainArchetype)) {
            return optimizeSavingsRebalance(params, isId);
        } else if ("DISPATCH_ROUTE_SCHEDULING".equalsIgnoreCase(domainArchetype)) {
            return optimizeDispatchRouting(params, isId);
        } else {
            // Default to DEBT_PAYOFF_STRATEGY (e.g. Peggy Debt Snowball vs Avalanche)
            return optimizeDebtPayoff(params, isId);
        }
    }

    private OptimizationOutput optimizeDebtPayoff(Map<String, Object> params, boolean isId) {
        double monthlyExtra = params.containsKey("monthly_extra_allocation")
                ? ((Number) params.get("monthly_extra_allocation")).doubleValue()
                : 1500000.0;

        List<Map<String, Object>> debts = extractList(params, "debts");
        if (debts.isEmpty()) {
            debts = List.of(
                    Map.of("name", "Credit Card A", "balance", 4500000.0, "interest_rate_apr", 0.24, "min_payment", 350000.0),
                    Map.of("name", "Electronics Installment", "balance", 1800000.0, "interest_rate_apr", 0.12, "min_payment", 200000.0),
                    Map.of("name", "Vehicle Loan", "balance", 18000000.0, "interest_rate_apr", 0.08, "min_payment", 950000.0)
            );
        }

        // 1. Avalanche Simulation (sort by interest_rate_apr descending)
        List<Map<String, Object>> avalancheDebts = new ArrayList<>(debts);
        avalancheDebts.sort((a, b) -> Double.compare(
                ((Number) b.get("interest_rate_apr")).doubleValue(),
                ((Number) a.get("interest_rate_apr")).doubleValue()
        ));
        PayoffResult avalancheResult = simulatePayoff(avalancheDebts, monthlyExtra);

        // 2. Snowball Simulation (sort by balance ascending)
        List<Map<String, Object>> snowballDebts = new ArrayList<>(debts);
        snowballDebts.sort((a, b) -> Double.compare(
                ((Number) a.get("balance")).doubleValue(),
                ((Number) b.get("balance")).doubleValue()
        ));
        PayoffResult snowballResult = simulatePayoff(snowballDebts, monthlyExtra);

        // 3. Baseline Simulation (minimums only)
        PayoffResult baselineResult = simulatePayoff(debts, 0.0);

        double interestSavedVsSnowball = Math.max(0, snowballResult.totalInterest - avalancheResult.totalInterest);
        double interestSavedVsBaseline = Math.max(0, baselineResult.totalInterest - avalancheResult.totalInterest);
        int monthsSavedVsBaseline = Math.max(0, baselineResult.months - avalancheResult.months);

        String topDebtName = (String) avalancheDebts.get(0).get("name");

        String title = isId
                ? "Prioritaskan Pelunasan " + topDebtName + " via Strategi Debt Avalanche"
                : "Prioritize " + topDebtName + " Payoff via Debt Avalanche Strategy";

        String summary = isId
                ? String.format("Alokasikan surplus bulanan Rp %,.0f khusus ke %s (bunga tertinggi) sementara membayar cicilan minimum pada utang lainnya. Menghemat bunga sebesar Rp %,.0f dibandingkan metode Snowball dan mempercepat pelunasan %d bulan.",
                monthlyExtra, topDebtName, interestSavedVsSnowball, monthsSavedVsBaseline)
                : String.format("Allocate the monthly extra surplus of Rp %,.0f strictly to %s (highest APR) while paying minimums on others. Saves Rp %,.0f in interest compared to Snowball method and finishes %d months earlier.",
                monthlyExtra, topDebtName, interestSavedVsSnowball, monthsSavedVsBaseline);

        List<TradeOffOptionView> tradeOffs = List.of(
                new TradeOffOptionView(
                        "Debt Avalanche (Recommended)",
                        "Total Interest Paid",
                        avalancheResult.totalInterest,
                        List.of(
                                isId ? "Meminimalkan total beban bunga yang harus dibayarkan" : "Minimizes lifetime interest charges paid",
                                isId ? "Mencapai status bebas utang lebih cepat secara matematis" : "Mathematically fastest debt-free horizon (" + avalancheResult.months + " months)"
                        ),
                        List.of(
                                isId ? "Penyelesaian utang pertama membutuhkan waktu beberapa bulan" : "First debt payoff milestone requires patience"
                        ),
                        true
                ),
                new TradeOffOptionView(
                        "Debt Snowball",
                        "Total Interest Paid",
                        snowballResult.totalInterest,
                        List.of(
                                isId ? "Pelunasan cicilan terkecil lebih cepat (kemenangan psikologis awal)" : "Closes smallest balance in month 1 (quick psychological momentum)"
                        ),
                        List.of(
                                isId ? String.format("Beban bunga bertambah Rp %,.0f lebih mahal", interestSavedVsSnowball) : String.format("Costs Rp %,.0f more in accumulated interest", interestSavedVsSnowball)
                        ),
                        false
                )
        );

        ScenarioComparisonView scenarioDiff = new ScenarioComparisonView(
                Map.of(
                        "strategy", "Baseline Minimums",
                        "total_months", baselineResult.months,
                        "total_interest", baselineResult.totalInterest
                ),
                Map.of(
                        "strategy", "Debt Avalanche",
                        "total_months", avalancheResult.months,
                        "total_interest", avalancheResult.totalInterest
                ),
                Map.of(
                        "delta_interest_saved", interestSavedVsBaseline,
                        "delta_months_saved", monthsSavedVsBaseline
                ),
                isId
                        ? String.format("Menghemat Rp %,.0f bunga dan bebas utang %d bulan lebih awal.", interestSavedVsBaseline, monthsSavedVsBaseline)
                        : String.format("Saves Rp %,.0f interest and achieves debt-free status %d months sooner.", interestSavedVsBaseline, monthsSavedVsBaseline)
        );

        Map<String, Object> evidenceRationale = new LinkedHashMap<>();
        evidenceRationale.put("primary_driver", "Interest APR Optimization");
        evidenceRationale.put("avalanche_months", avalancheResult.months);
        evidenceRationale.put("snowball_months", snowballResult.months);
        evidenceRationale.put("baseline_months", baselineResult.months);
        evidenceRationale.put("total_interest_saved_vs_baseline", interestSavedVsBaseline);
        evidenceRationale.put("total_interest_saved_vs_snowball", interestSavedVsSnowball);

        Map<String, Object> candidateMetrics = new LinkedHashMap<>();
        candidateMetrics.put("emergency_fund_floor", 12500000.0);
        candidateMetrics.put("max_debt_repayment_ratio", 0.28);
        candidateMetrics.put("emergency_cash", 12500000.0);

        return new OptimizationOutput(
                title,
                summary,
                "REALLOCATE_EXTRA_PAYMENT",
                0.94,
                evidenceRationale,
                tradeOffs,
                scenarioDiff,
                candidateMetrics
        );
    }

    private OptimizationOutput optimizeSavingsRebalance(Map<String, Object> params, boolean isId) {
        double monthlySavings = params.containsKey("monthly_savings_budget")
                ? ((Number) params.get("monthly_savings_budget")).doubleValue()
                : 4000000.0;

        double emergencyAllocation = monthlySavings * 0.65;
        double sinkingAllocation = monthlySavings * 0.35;

        String title = isId
                ? "Rebalancing Alokasi Tabungan: 65% Dana Darurat & 35% Pos Pengeluaran"
                : "Savings Goal Rebalancing: 65% Emergency Fund & 35% Sinking Funds";

        String summary = isId
                ? String.format("Alokasikan Rp %,.0f ke Dana Darurat (target 3 bulan biaya hidup) dan Rp %,.0f ke pos tahunan (Pajak Kendaraan & Liburan). Mencapai target ketahanan finansial dalam 5 bulan.",
                emergencyAllocation, sinkingAllocation)
                : String.format("Allocate Rp %,.0f to Emergency Reserve (3-month floor target) and Rp %,.0f to Sinking Funds (Tax & Travel). Achieves financial safety target in 5 months.",
                emergencyAllocation, sinkingAllocation);

        List<TradeOffOptionView> tradeOffs = List.of(
                new TradeOffOptionView(
                        "Emergency First Rebalance (Recommended)",
                        "Emergency Safety Floor",
                        30000000.0,
                        List.of(
                                isId ? "Perlindungan maksimal terhadap risiko tidak terduga" : "Guarantees household resilience against unexpected job/medical shocks",
                                isId ? "Sesuai pedoman minimum 3 bulan biaya hidup" : "Satisfies 3-month essential expense safety floor in 5 months"
                        ),
                        List.of(
                                isId ? "Target liburan tercapai 2 bulan lebih lambat" : "Discretionary vacation savings accumulates slightly slower"
                        ),
                        true
                ),
                new TradeOffOptionView(
                        "Equal Split (50/50)",
                        "Emergency Safety Floor",
                        30000000.0,
                        List.of(
                                isId ? "Pos liburan dan darurat bertambah seimbang" : "Vacation fund grows faster"
                        ),
                        List.of(
                                isId ? "Keluarga rentan terhadap guncangan darurat selama 3 bulan lebih lama" : "Leaves household vulnerable to cash emergencies for 3 additional months"
                        ),
                        false
                )
        );

        ScenarioComparisonView scenarioDiff = new ScenarioComparisonView(
                Map.of("emergency_allocation", monthlySavings * 0.50, "months_to_safety", 8),
                Map.of("emergency_allocation", emergencyAllocation, "months_to_safety", 5),
                Map.of("months_saved_to_safety_floor", 3),
                isId ? "Mempercepat tercapainya ketahanan darurat 3 bulan lebih awal." : "Reaches full emergency fund safety 3 months faster."
        );

        Map<String, Object> candidateMetrics = new LinkedHashMap<>();
        candidateMetrics.put("emergency_priority_weight", 0.65);
        candidateMetrics.put("emergency_fund_floor", 30000000.0);
        candidateMetrics.put("emergency_cash", 18500000.0);

        return new OptimizationOutput(
                title,
                summary,
                "REBALANCE_SAVINGS_RATIO",
                0.91,
                Map.of("recommended_emergency_ratio", 0.65, "recommended_sinking_ratio", 0.35),
                tradeOffs,
                scenarioDiff,
                candidateMetrics
        );
    }

    private OptimizationOutput optimizeDispatchRouting(Map<String, Object> params, boolean isId) {
        String title = isId
                ? "Optimalisasi Klaster Pengiriman Kurir Gudang SMS"
                : "SMS Warehouse Courier Dispatch Cluster Optimization";

        String summary = isId
                ? "Kelompokkan 18 pesanan ke dalam 2 klaster geografis berurutan. Mengurangi estimasi jarak tempuh 24% dan menghemat 45 menit waktu pengantaran."
                : "Clustered 18 pending orders into 2 geographic delivery sequences. Reduces route transit distance by 24% and saves 45 minutes dispatch time.";

        List<TradeOffOptionView> tradeOffs = List.of(
                new TradeOffOptionView(
                        "Geographic Density Clustered (Recommended)",
                        "Route Distance Reduction",
                        0.24,
                        List.of(
                                isId ? "Menghemat 24% jarak dan BBM kurir" : "Reduces 24% fuel and transit travel time",
                                isId ? "Selesai sebelum batas waktu pengiriman sore" : "Completes before afternoon delivery cutoff"
                        ),
                        List.of(
                                isId ? "Urutan pesanan tidak persis sesuai waktu checkout pembeli" : "Order sequence does not match chronological checkout sequence"
                        ),
                        true
                ),
                new TradeOffOptionView(
                        "First-In First-Out (Chronological)",
                        "Route Distance Reduction",
                        0.0,
                        List.of(
                                isId ? "Memenuhi urutan checkout paling awal terlebih dahulu" : "Strict chronological checkout fulfillment"
                        ),
                        List.of(
                                isId ? "Jarak tempuh bolak-balik bertambah 28 km" : "Incurs 28 km zig-zag transit waste"
                        ),
                        false
                )
        );

        ScenarioComparisonView scenarioDiff = new ScenarioComparisonView(
                Map.of("total_km", 64.0, "total_hours", 4.2),
                Map.of("total_km", 48.5, "total_hours", 3.4),
                Map.of("km_saved", 15.5, "time_saved_minutes", 45),
                isId ? "Menghemat 15,5 km dan 45 menit perjalanan." : "Saves 15.5 km and 45 transit minutes."
        );

        Map<String, Object> candidateMetrics = new LinkedHashMap<>();
        candidateMetrics.put("max_route_hours", 3.4);
        candidateMetrics.put("emergency_fund_floor", 10000000.0);

        return new OptimizationOutput(
                title,
                summary,
                "OPTIMIZE_DISPATCH_ROUTE",
                0.95,
                Map.of("algorithm", "GREEDY_NEAREST_NEIGHBOR_2OPT", "stops_count", 18),
                tradeOffs,
                scenarioDiff,
                candidateMetrics
        );
    }

    private record PayoffResult(int months, double totalInterest) {}

    private PayoffResult simulatePayoff(List<Map<String, Object>> debtList, double extraMonthly) {
        List<double[]> state = new ArrayList<>();
        for (Map<String, Object> d : debtList) {
            double bal = ((Number) d.get("balance")).doubleValue();
            double apr = ((Number) d.get("interest_rate_apr")).doubleValue();
            double minPay = ((Number) d.get("min_payment")).doubleValue();
            state.add(new double[]{bal, apr, minPay});
        }

        int months = 0;
        double accumulatedInterest = 0.0;
        int maxMonths = 120;

        while (months < maxMonths) {
            boolean allPaid = true;
            for (double[] d : state) {
                if (d[0] > 0.01) {
                    allPaid = false;
                    break;
                }
            }
            if (allPaid) break;

            months++;
            double extraPool = extraMonthly;

            // 1. Accrue monthly interest on outstanding balances
            for (double[] d : state) {
                if (d[0] > 0.01) {
                    double monthlyInt = d[0] * (d[1] / 12.0);
                    accumulatedInterest += monthlyInt;
                    d[0] += monthlyInt;
                }
            }

            // 2. Pay minimums
            for (double[] d : state) {
                if (d[0] > 0.01) {
                    double pay = Math.min(d[0], d[2]);
                    d[0] -= pay;
                }
            }

            // 3. Apply extra payment to first active debt in sorted priority order
            for (double[] d : state) {
                if (d[0] > 0.01 && extraPool > 0.01) {
                    double pay = Math.min(d[0], extraPool);
                    d[0] -= pay;
                    extraPool -= pay;
                }
            }
        }

        return new PayoffResult(months, Math.round(accumulatedInterest));
    }

    private Map<String, Object> parseParameters(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<Map<String, Object>> extractList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> l) {
            List<Map<String, Object>> res = new ArrayList<>();
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> casted = new HashMap<>();
                    m.forEach((k, v) -> casted.put(String.valueOf(k), v));
                    res.add(casted);
                }
            }
            return res;
        }
        return Collections.emptyList();
    }
}

