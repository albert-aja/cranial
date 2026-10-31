package io.cranial.platform.document.extraction;

import io.cranial.platform.document.entity.DocumentPageEntity;
import io.cranial.platform.document.model.ExtractedLineItem;
import io.cranial.platform.document.model.ExtractedTable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TableAndLineItemExtractor {

    // Pattern A: "1. Beras Ramos 5kg | 1 | 72000 | 72000"
    private static final Pattern PIPE_ITEM_PATTERN = Pattern.compile(
            "^\\s*(?:\\d+[.)]\\s*)?([^|]+?)\\s*\\|\\s*([0-9]+(?:[.,][0-9]+)?)\\s*\\|\\s*(?:Rp\\.?\\s*|USD\\s*|\\$\\s*)?([0-9.,]+)\\s*\\|\\s*(?:Rp\\.?\\s*|USD\\s*|\\$\\s*)?([0-9.,]+)\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern B: "2x Kopi Susu @ 18.000 = 36.000" or "2 x Kopi Susu 18000 36000"
    private static final Pattern QTY_X_ITEM_PATTERN = Pattern.compile(
            "^\\s*([0-9]+)\\s*[xX]\\s+(.+?)\\s+(?:@\\s*)?(?:Rp\\.?\\s*|USD\\s*|\\$\\s*)?([0-9.,]+)\\s*(?:=\\s*)?(?:Rp\\.?\\s*|USD\\s*|\\$\\s*)?([0-9.,]+)\\s*$"
    );

    public record TableExtractionOutcome(
            List<ExtractedTable> tables,
            List<ExtractedLineItem> lineItems
    ) {
    }

    public TableExtractionOutcome extract(List<DocumentPageEntity> pages, String defaultCurrency) {
        List<ExtractedTable> tables = new ArrayList<>();
        List<ExtractedLineItem> lineItems = new ArrayList<>();
        int itemIdx = 0;
        int tableIdx = 0;

        for (DocumentPageEntity page : pages) {
            if (!"SUCCESS".equals(page.getStatus()) || page.getExtractedText() == null) {
                continue;
            }
            String text = page.getExtractedText();
            String[] lines = text.split("\\R");
            double pageConf = page.getOcrConfidence() != null ? page.getOcrConfidence() : 0.85;

            List<List<String>> currentTableRows = new ArrayList<>();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                int lineNum = i + 1;
                if (line.isEmpty()) {
                    if (currentTableRows.size() >= 2) {
                        tables.add(buildTable(++tableIdx, page.getPageNumber(), currentTableRows, pageConf));
                    }
                    currentTableRows = new ArrayList<>();
                    continue;
                }

                Matcher mPipe = PIPE_ITEM_PATTERN.matcher(line);
                if (mPipe.matches()) {
                    String desc = mPipe.group(1).trim();
                    Double qty = parseNumber(mPipe.group(2));
                    Double unit = parseNumber(mPipe.group(3));
                    Double amt = parseNumber(mPipe.group(4));
                    lineItems.add(new ExtractedLineItem(
                            ++itemIdx,
                            desc,
                            qty,
                            unit,
                            amt,
                            defaultCurrency,
                            page.getPageNumber(),
                            lineNum,
                            line,
                            roundConf(pageConf),
                            pageConf >= 0.65 ? "EXTRACTED" : "UNCERTAIN"
                    ));
                    currentTableRows.add(List.of(desc, mPipe.group(2).trim(), mPipe.group(3).trim(), mPipe.group(4).trim()));
                    continue;
                }

                Matcher mQty = QTY_X_ITEM_PATTERN.matcher(line);
                if (mQty.matches()) {
                    Double qty = parseNumber(mQty.group(1));
                    String desc = mQty.group(2).trim();
                    Double unit = parseNumber(mQty.group(3));
                    Double amt = parseNumber(mQty.group(4));
                    lineItems.add(new ExtractedLineItem(
                            ++itemIdx,
                            desc,
                            qty,
                            unit,
                            amt,
                            defaultCurrency,
                            page.getPageNumber(),
                            lineNum,
                            line,
                            roundConf(pageConf),
                            pageConf >= 0.65 ? "EXTRACTED" : "UNCERTAIN"
                    ));
                    currentTableRows.add(List.of(desc, mQty.group(1).trim(), mQty.group(3).trim(), mQty.group(4).trim()));
                    continue;
                }

                // Generic pipe or CSV table detection (3+ columns)
                if (line.contains("|") && line.split("\\|").length >= 3) {
                    List<String> cells = Arrays.stream(line.split("\\|")).map(String::trim).toList();
                    if (!cells.stream().allMatch(c -> c.matches("-+"))) {
                        currentTableRows.add(cells);
                    }
                } else if (line.contains(",") && line.split(",").length >= 3 && !line.contains(": ")) {
                    List<String> cells = Arrays.stream(line.split(",")).map(String::trim).toList();
                    currentTableRows.add(cells);
                } else {
                    if (currentTableRows.size() >= 2) {
                        tables.add(buildTable(++tableIdx, page.getPageNumber(), currentTableRows, pageConf));
                    }
                    currentTableRows = new ArrayList<>();
                }
            }

            if (currentTableRows.size() >= 2) {
                tables.add(buildTable(++tableIdx, page.getPageNumber(), currentTableRows, pageConf));
            }
        }

        return new TableExtractionOutcome(tables, lineItems);
    }

    private static ExtractedTable buildTable(int tableIndex, int pageNumber, List<List<String>> rows, double conf) {
        List<String> firstRow = rows.get(0);
        boolean hasHeader = firstRow.stream().noneMatch(c -> c.matches("^[0-9.,]+$"));
        if (hasHeader && rows.size() >= 2) {
            return new ExtractedTable(tableIndex, pageNumber, firstRow, rows.subList(1, rows.size()), roundConf(conf));
        }
        List<String> defaultHeaders = List.of("description", "quantity", "unit_price", "amount");
        return new ExtractedTable(tableIndex, pageNumber, defaultHeaders, List.copyOf(rows), roundConf(conf));
    }

    public static Double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.replaceAll("(?i)(rp\\.?|idr|usd|\\$|eur)", "").trim().replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        // Indonesian thousands separator check: e.g. 140.000 or 1.250.000,50
        if (s.contains(".") && s.contains(",")) {
            int lastDot = s.lastIndexOf('.');
            int lastComma = s.lastIndexOf(',');
            if (lastComma > lastDot) {
                s = s.replace(".", "").replace(",", ".");
            } else {
                s = s.replace(",", "");
            }
        } else if (s.matches("^[0-9]{1,3}(\\.[0-9]{3})+$")) {
            s = s.replace(".", "");
        } else if (s.matches("^[0-9]{1,3}(,[0-9]{3})+$")) {
            s = s.replace(",", "");
        } else if (s.contains(",") && !s.contains(".")) {
            s = s.replace(",", ".");
        }
        try {
            return Double.parseDouble(s.toLowerCase(Locale.ROOT));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static double roundConf(double c) {
        return Math.round(Math.max(0.0, Math.min(1.0, c)) * 1000.0) / 1000.0;
    }
}

