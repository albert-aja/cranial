package io.cranial.platform.conversation.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StructuredUiBlock(
        BlockType type,
        String title,
        String content,
        String description,
        String actionLabel,
        String actionPayload,
        List<String> headers,
        List<List<String>> rows,
        List<ClarificationOption> options,
        String metricLabel,
        String metricValue,
        String metricTrend
) {
    public static StructuredUiBlock text(String content) {
        return new StructuredUiBlock(BlockType.TEXT, null, content, null, null, null, null, null, null, null, null, null);
    }

    public static StructuredUiBlock table(String title, List<String> headers, List<List<String>> rows) {
        return new StructuredUiBlock(BlockType.TABLE, title, null, null, null, null, headers, rows, null, null, null, null);
    }

    public static StructuredUiBlock actionCard(String title, String description, String actionLabel, String actionPayload) {
        return new StructuredUiBlock(BlockType.ACTION_CARD, title, null, description, actionLabel, actionPayload, null, null, null, null, null, null);
    }

    public static StructuredUiBlock chartKpi(String title, String metricLabel, String metricValue, String metricTrend) {
        return new StructuredUiBlock(BlockType.CHART_KPI, title, null, null, null, null, null, null, null, metricLabel, metricValue, metricTrend);
    }

    public static StructuredUiBlock clarification(String title, String description, List<ClarificationOption> options) {
        return new StructuredUiBlock(BlockType.CLARIFICATION, title, null, description, null, null, null, null, options, null, null, null);
    }

    public static StructuredUiBlock financialDisclaimer(String title, String content) {
        return new StructuredUiBlock(BlockType.FINANCIAL_DISCLAIMER, title, content, null, null, null, null, null, null, null, null, null);
    }
}

