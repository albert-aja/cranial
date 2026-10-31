package io.cranial.platform.document.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.document.entity.DocumentPageEntity;
import io.cranial.platform.document.model.ExtractedFieldValue;
import io.cranial.platform.document.model.ExtractedLineItem;
import io.cranial.platform.document.model.FieldProvenance;
import io.cranial.platform.orchestration.entity.SchemaEntity;
import io.cranial.platform.orchestration.model.SchemaValidationResult;
import io.cranial.platform.orchestration.schema.SchemaValidator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StructuredFieldExtractor {

    private final SchemaValidator schemaValidator;
    private final ObjectMapper objectMapper;

    public StructuredFieldExtractor(SchemaValidator schemaValidator, ObjectMapper objectMapper) {
        this.schemaValidator = schemaValidator;
        this.objectMapper = objectMapper;
    }

    public record StructuredExtractionOutcome(
            Map<String, Object> extractedFields,
            Map<String, ExtractedFieldValue> fieldDetails,
            String detectedCurrency,
            double overallConfidence,
            boolean schemaValid,
            List<String> validationErrors,
            List<String> warnings,
            String reviewStatus,
            List<String> reviewReasons
    ) {
    }

    private record LineMatch(
            String valueText,
            int pageNumber,
            int lineNumber,
            int charStart,
            int charEnd,
            String regionHint,
            String rawLine,
            String engineUsed,
            double pageConfidence
    ) {
    }

    public StructuredExtractionOutcome extract(
            List<DocumentPageEntity> pages,
            String documentType,
            SchemaEntity schema,
            double confidenceThreshold,
            boolean autoReviewLowConfidence,
            List<ExtractedLineItem> lineItems,
            List<String> initialWarnings
    ) {
        Map<String, Object> flatFields = new LinkedHashMap<>();
        Map<String, ExtractedFieldValue> details = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>(initialWarnings != null ? initialWarnings : List.of());
        List<String> reviewReasons = new ArrayList<>();

        List<PageLine> indexedLines = buildIndexedLines(pages);
        double avgPageConf = computeAveragePageConfidence(pages);

        // Detect currency with provenance
        ExtractedFieldValue currencyVal = detectCurrency(indexedLines);
        String detectedCurrency = currencyVal.value() != null ? currencyVal.value().toString() : "IDR";

        // Determine target field names from schema if present, or defaults per documentType
        Map<String, String> targetProperties = resolveTargetProperties(schema, documentType);

        for (Map.Entry<String, String> prop : targetProperties.entrySet()) {
            String fieldName = prop.getKey();
            String jsonType = prop.getValue();

            ExtractedFieldValue extracted = extractSingleField(fieldName, jsonType, documentType, indexedLines, currencyVal);
            details.put(fieldName, extracted);
            if (extracted.value() != null && !"MISSING".equals(extracted.status())) {
                flatFields.put(fieldName, extracted.value());
            }
            if ("UNCERTAIN".equals(extracted.status())) {
                reviewReasons.add("UNCERTAIN_FIELD:" + fieldName);
            }
        }

        // Deterministic arithmetic cross-check (advisory warning only; never overwrites extracted values!)
        verifyDeterministicArithmetic(flatFields, lineItems, warnings);

        // Validate against JSON Schema
        SchemaValidationResult valRes = schema != null
                ? schemaValidator.validate(schema.getSchemaDefinitionJson(), flatFields)
                : SchemaValidationResult.success();

        if (!valRes.valid()) {
            for (String err : valRes.errors()) {
                reviewReasons.add("SCHEMA_VALIDATION:" + err);
            }
        }

        // Check failed pages
        long failedPageCount = pages.stream().filter(p -> "FAILED".equals(p.getStatus())).count();
        if (failedPageCount > 0) {
            warnings.add("PARTIAL_PAGE_FAILURE: " + failedPageCount + " page(s) failed during extraction.");
            reviewReasons.add("PARTIAL_PAGE_FAILURE");
        }

        double overallConf = computeOverallConfidence(details, avgPageConf, valRes.valid());
        if (autoReviewLowConfidence && overallConf < confidenceThreshold) {
            reviewReasons.add(String.format(Locale.ROOT, "LOW_CONFIDENCE(%.2f < %.2f)", overallConf, confidenceThreshold));
        }

        String reviewStatus = reviewReasons.isEmpty() ? "NOT_REQUIRED" : "PENDING_REVIEW";

        return new StructuredExtractionOutcome(
                flatFields,
                details,
                detectedCurrency,
                overallConf,
                valRes.valid(),
                valRes.errors(),
                warnings,
                reviewStatus,
                reviewReasons
        );
    }

    private ExtractedFieldValue extractSingleField(
            String fieldName,
            String jsonType,
            String documentType,
            List<PageLine> lines,
            ExtractedFieldValue currencyField
    ) {
        if ("currency".equals(fieldName)) {
            return currencyField;
        }

        LineMatch match = switch (fieldName) {
            case "merchant_name" -> findLabelOrTopHeader(lines,
                    List.of("merchant", "store", "toko", "nama toko", "outlet"),
                    List.of("faktur", "invoice", "struk", "receipt", "surat jalan", "tanggal", "date"));
            case "vendor_name" -> findByLabels(lines, List.of("vendor", "supplier", "pemasok", "penjual", "dari"));
            case "receipt_number" -> findByLabels(lines, List.of("no struk", "no. struk", "receipt #", "receipt no", "receipt number", "nomor struk", "no.", "no"));
            case "invoice_number" -> findByLabels(lines, List.of("invoice number", "invoice no", "invoice #", "no faktur", "no. faktur", "nomor faktur", "no.", "no"));
            case "document_number" -> findByLabels(lines, List.of("document number", "no surat jalan", "delivery note no", "document no", "no dokumen", "nomor", "no.", "no"));
            case "reference_number" -> findByLabels(lines, List.of("reference number", "ref no", "nomor referensi", "no referensi", "ref", "no"));
            case "form_title" -> findByLabels(lines, List.of("application form", "formulir", "form title", "nama formulir"));
            case "applicant_name" -> findByLabels(lines, List.of("applicant name", "nama pemohon", "applicant", "pemohon"));
            case "sender_name" -> findByLabels(lines, List.of("sender", "pengirim", "shipped from"));
            case "recipient_name" -> findByLabels(lines, List.of("recipient", "penerima", "deliver to", "kepada"));
            case "transaction_date" -> findDateByLabels(lines, List.of("tanggal", "transaction date", "date", "tgl"));
            case "invoice_date" -> findDateByLabels(lines, List.of("invoice date", "tanggal faktur", "tanggal", "date"));
            case "due_date" -> findDateByLabels(lines, List.of("due date", "jatuh tempo", "tanggal jatuh tempo"));
            case "delivery_date" -> findDateByLabels(lines, List.of("delivery date", "tanggal kirim", "tanggal pengiriman", "tanggal"));
            case "submission_date" -> findDateByLabels(lines, List.of("submission date", "tanggal pengajuan", "date", "tanggal"));
            case "subtotal_amount" -> findByLabels(lines, List.of("subtotal", "sub total", "sub-total", "dpp"));
            case "tax_amount" -> findByLabels(lines, List.of("ppn", "tax", "vat", "pajak"));
            case "total_amount" -> findExactTotal(lines);
            case "total_quantity" -> findByLabels(lines, List.of("total quantity", "total qty", "jumlah barang", "total item", "total items"));
            default -> findByLabels(lines, List.of(fieldName.replace('_', ' '), fieldName));
        };

        if (match == null || match.valueText().isBlank()) {
            return ExtractedFieldValue.missing();
        }

        FieldProvenance prov = new FieldProvenance(
                match.pageNumber(),
                match.lineNumber(),
                match.charStart(),
                match.charEnd(),
                match.regionHint(),
                match.rawLine(),
                match.engineUsed()
        );

        Object typedVal = match.valueText().trim();
        if ("number".equalsIgnoreCase(jsonType) || "integer".equalsIgnoreCase(jsonType)) {
            Double parsed = TableAndLineItemExtractor.parseNumber(match.valueText());
            if (parsed == null) {
                return ExtractedFieldValue.missing();
            }
            if (parsed == Math.rint(parsed) && Math.abs(parsed) < 1e15) {
                typedVal = parsed.longValue();
            } else {
                typedVal = parsed;
            }
        }

        double conf = match.pageConfidence();
        if (conf < 0.60) {
            return ExtractedFieldValue.uncertain(typedVal, conf, prov);
        }
        return ExtractedFieldValue.extracted(typedVal, conf, prov);
    }

    private ExtractedFieldValue detectCurrency(List<PageLine> lines) {
        for (PageLine pl : lines) {
            String t = pl.text();
            if (t.matches("(?i).*\\b(IDR|Rp\\.?\\s*[0-9]).*")) {
                FieldProvenance prov = new FieldProvenance(pl.pageNumber(), pl.lineNumber(), pl.charStart(), pl.charEnd(), pl.region(), t, pl.engine());
                String status = t.toUpperCase(Locale.ROOT).contains("IDR") ? "EXTRACTED" : "INFERRED";
                return new ExtractedFieldValue("IDR", status, pl.confidence(), prov);
            }
            if (t.matches("(?i).*(\\bUSD\\b|\\$\\s*[0-9]).*")) {
                FieldProvenance prov = new FieldProvenance(pl.pageNumber(), pl.lineNumber(), pl.charStart(), pl.charEnd(), pl.region(), t, pl.engine());
                String status = t.toUpperCase(Locale.ROOT).contains("USD") ? "EXTRACTED" : "INFERRED";
                return new ExtractedFieldValue("USD", status, pl.confidence(), prov);
            }
        }
        return ExtractedFieldValue.missing();
    }

    private LineMatch findLabelOrTopHeader(List<PageLine> lines, List<String> labels, List<String> skipHeaders) {
        LineMatch labeled = findByLabels(lines, labels);
        if (labeled != null) {
            return labeled;
        }
        // First non-empty header line on page 1 that is not a label/keyword line
        for (PageLine pl : lines) {
            if (pl.pageNumber() == 1 && pl.lineNumber() <= 3) {
                String candidate = pl.text().trim();
                String low = candidate.toLowerCase(Locale.ROOT);
                boolean skip = candidate.contains(":") || candidate.contains("|")
                        || skipHeaders.stream().anyMatch(low::startsWith);
                if (!skip && candidate.length() >= 3) {
                    return new LineMatch(candidate, pl.pageNumber(), pl.lineNumber(), pl.charStart(), pl.charEnd(), "HEADER", pl.text(), pl.engine(), pl.confidence());
                }
            }
        }
        return null;
    }

    private LineMatch findExactTotal(List<PageLine> lines) {
        // Match "Total:" or "Grand Total:" or "Total Bayar:" WITHOUT matching "Subtotal:" or "Total Quantity:"
        Pattern p = Pattern.compile("(?i)^(?:grand\\s+total|total\\s+bayar|total\\s+tagihan|total\\s+due|amount\\s+due|total\\s+amount|total)\\s*[:=]\\s*(.+)$");
        LineMatch best = null;
        for (PageLine pl : lines) {
            Matcher m = p.matcher(pl.text().trim());
            if (m.matches()) {
                best = new LineMatch(m.group(1).trim(), pl.pageNumber(), pl.lineNumber(), pl.charStart(), pl.charEnd(), pl.region(), pl.text(), pl.engine(), pl.confidence());
            }
        }
        return best;
    }

    private LineMatch findDateByLabels(List<PageLine> lines, List<String> labels) {
        LineMatch byLabel = findByLabels(lines, labels);
        if (byLabel != null) {
            Matcher iso = Pattern.compile("(\\d{4}-\\d{2}-\\d{2}|\\d{2}/\\d{2}/\\d{4}|\\d{2}-\\d{2}-\\d{4})").matcher(byLabel.valueText());
            if (iso.find()) {
                return new LineMatch(iso.group(1), byLabel.pageNumber(), byLabel.lineNumber(), byLabel.charStart(), byLabel.charEnd(), byLabel.regionHint(), byLabel.rawLine(), byLabel.engineUsed(), byLabel.pageConfidence());
            }
            return byLabel;
        }
        return null;
    }

    private LineMatch findByLabels(List<PageLine> lines, List<String> labels) {
        for (String label : labels) {
            Pattern p = Pattern.compile("(?i)^" + Pattern.quote(label) + "\\s*[:=]\\s*(.+)$");
            for (PageLine pl : lines) {
                Matcher m = p.matcher(pl.text().trim());
                if (m.matches()) {
                    return new LineMatch(
                            m.group(1).trim(),
                            pl.pageNumber(),
                            pl.lineNumber(),
                            pl.charStart(),
                            pl.charEnd(),
                            pl.region(),
                            pl.text(),
                            pl.engine(),
                            pl.confidence()
                    );
                }
            }
        }
        return null;
    }

    private Map<String, String> resolveTargetProperties(SchemaEntity schema, String documentType) {
        Map<String, String> props = new LinkedHashMap<>();
        if (schema != null && schema.getSchemaDefinitionJson() != null) {
            try {
                JsonNode root = objectMapper.readTree(schema.getSchemaDefinitionJson());
                JsonNode propertiesNode = root.get("properties");
                if (propertiesNode != null && propertiesNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> it = propertiesNode.fields();
                    while (it.hasNext()) {
                        Map.Entry<String, JsonNode> entry = it.next();
                        String type = entry.getValue().has("type") ? entry.getValue().get("type").asText("string") : "string";
                        props.put(entry.getKey(), type);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (props.isEmpty()) {
            // Sensible fallback fields for generic documents
            props.put("merchant_name", "string");
            props.put("transaction_date", "string");
            props.put("currency", "string");
            props.put("total_amount", "number");
        }
        return props;
    }

    private static void verifyDeterministicArithmetic(
            Map<String, Object> flatFields,
            List<ExtractedLineItem> items,
            List<String> warnings
    ) {
        if (items == null || items.isEmpty() || !flatFields.containsKey("total_amount")) {
            return;
        }
        double itemsSum = 0.0;
        boolean anyPositive = false;
        for (ExtractedLineItem item : items) {
            if (item.amount() != null && item.amount() > 0) {
                itemsSum += item.amount();
                anyPositive = true;
            }
        }
        if (!anyPositive) {
            return;
        }
        double tax = flatFields.containsKey("tax_amount") ? ((Number) flatFields.get("tax_amount")).doubleValue() : 0.0;
        double total = ((Number) flatFields.get("total_amount")).doubleValue();
        double expected = itemsSum + tax;
        if (Math.abs(expected - total) > 0.05) {
            warnings.add(String.format(
                    Locale.ROOT,
                    "ARITHMETIC_DISCREPANCY_WARNING: Line items sum (%.2f) + tax (%.2f) = %.2f differs from extracted total_amount (%.2f). Owning application must verify.",
                    itemsSum, tax, expected, total
            ));
        }
    }

    private static double computeAveragePageConfidence(List<DocumentPageEntity> pages) {
        double sum = 0.0;
        int count = 0;
        for (DocumentPageEntity p : pages) {
            if ("SUCCESS".equals(p.getStatus()) && p.getOcrConfidence() != null) {
                sum += p.getOcrConfidence();
                count++;
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private static double computeOverallConfidence(
            Map<String, ExtractedFieldValue> details,
            double avgPageConf,
            boolean schemaValid
    ) {
        if (details.isEmpty()) {
            return Math.round(avgPageConf * 1000.0) / 1000.0;
        }
        double sum = 0.0;
        int extractedCount = 0;
        for (ExtractedFieldValue fv : details.values()) {
            if (!"MISSING".equals(fv.status())) {
                sum += fv.confidence();
                extractedCount++;
            }
        }
        if (extractedCount == 0) {
            return 0.20;
        }
        double base = sum / extractedCount;
        if (!schemaValid) {
            base *= 0.70;
        }
        return Math.round(Math.max(0.0, Math.min(1.0, base)) * 1000.0) / 1000.0;
    }

    private record PageLine(
            int pageNumber,
            int lineNumber,
            int charStart,
            int charEnd,
            String region,
            String text,
            String engine,
            double confidence
    ) {
    }

    private static List<PageLine> buildIndexedLines(List<DocumentPageEntity> pages) {
        List<PageLine> out = new ArrayList<>();
        for (DocumentPageEntity p : pages) {
            if (!"SUCCESS".equals(p.getStatus()) || p.getExtractedText() == null) {
                continue;
            }
            String raw = p.getExtractedText();
            String[] split = raw.split("\\R");
            int offset = 0;
            double conf = p.getOcrConfidence() != null ? p.getOcrConfidence() : 0.85;
            for (int i = 0; i < split.length; i++) {
                String line = split[i];
                int start = raw.indexOf(line, offset);
                if (start < 0) {
                    start = offset;
                }
                int end = start + line.length();
                offset = end;
                String region = (i < 3) ? "HEADER" : (i >= split.length - 3 ? "FOOTER" : "BODY");
                if (!line.isBlank()) {
                    out.add(new PageLine(
                            p.getPageNumber(),
                            i + 1,
                            start,
                            end,
                            region,
                            line.trim(),
                            p.getEngineUsed(),
                            conf
                    ));
                }
            }
        }
        return out;
    }
}

