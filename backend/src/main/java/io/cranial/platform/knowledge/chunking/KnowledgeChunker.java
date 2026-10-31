package io.cranial.platform.knowledge.chunking;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class KnowledgeChunker {

    private static final int MAX_PARENT_SECTION_CHARS = 3200;
    private static final Pattern PAGE_MARKER = Pattern.compile("(?im)^\\s*(?:\\[\\s*page\\s+(\\d+)\\s*\\]|---\\s*page\\s+(\\d+)\\s*---)\\s*$");
    private static final Pattern HEADING_LINE = Pattern.compile("^(#{1,4})\\s+(.+?)\\s*$");
    private static final Pattern GLOSSARY_HEADER = Pattern.compile("^#{2,4}\\s+([^(\\n]+?)(?:\\s*\\((?:Aliases|Alias|Sinonim)\\s*:\\s*([^)]+)\\))?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern FAQ_START = Pattern.compile("(?im)^(?:Q:|Question:|Pertanyaan:|Tanya:|#{2,3}\\s*(?:Q:|Pertanyaan:)?)\\s*.+$");

    private static final Map<String, String> BILINGUAL_EXPANSIONS = Map.ofEntries(
            Map.entry("dana", "fund"),
            Map.entry("fund", "dana"),
            Map.entry("darurat", "emergency"),
            Map.entry("emergency", "darurat"),
            Map.entry("anggaran", "budget"),
            Map.entry("budget", "anggaran"),
            Map.entry("inflasi", "inflation"),
            Map.entry("inflation", "inflasi"),
            Map.entry("bunga", "interest"),
            Map.entry("majemuk", "compound"),
            Map.entry("gudang", "warehouse"),
            Map.entry("warehouse", "gudang"),
            Map.entry("rusak", "damaged"),
            Map.entry("damaged", "rusak"),
            Map.entry("pengiriman", "delivery"),
            Map.entry("delivery", "pengiriman"),
            Map.entry("suhu", "temperature"),
            Map.entry("temperature", "suhu"),
            Map.entry("laba", "margin"),
            Map.entry("margin", "laba"),
            Map.entry("gaji", "payroll"),
            Map.entry("payroll", "gaji")
    );

    public record ChunkDraft(
            int chunkIndex,
            String content,
            String parentSectionContent,
            String chunkSha256,
            String normalizedTerms,
            int tokenCount,
            Integer pageNumber,
            String sectionTitle,
            int charStart,
            int charEnd
    ) {
    }

    public record GlossaryCandidate(
            String term,
            List<String> aliases,
            String shortDefinition,
            int chunkIndex
    ) {
    }

    public record ChunkingOutput(
            String strategyUsed,
            List<ChunkDraft> chunks,
            List<GlossaryCandidate> glossaryCandidates
    ) {
    }

    public ChunkingOutput chunk(
            String rawContent,
            String contentType,
            String collectionDefaultStrategy,
            String strategyOverride,
            int chunkSizeChars,
            int chunkOverlapChars
    ) {
        String safeText = rawContent == null ? "" : rawContent.replace("\r\n", "\n");
        int maxChars = Math.max(80, Math.min(8000, chunkSizeChars > 0 ? chunkSizeChars : 600));
        int overlap = Math.max(0, Math.min(maxChars / 2, chunkOverlapChars >= 0 ? chunkOverlapChars : 80));

        String strategy = resolveStrategy(contentType, collectionDefaultStrategy, strategyOverride);
        List<GlossaryCandidate> glossaryCandidates = new ArrayList<>();
        List<RawSegment> segments = switch (strategy) {
            case "GLOSSARY_ENTRY" -> segmentGlossary(safeText, glossaryCandidates);
            case "MARKDOWN_SECTION" -> segmentMarkdownSections(safeText);
            case "FAQ_QA" -> segmentFaq(safeText);
            case "FIXED_WINDOW" -> segmentFixedWindow(safeText, maxChars, overlap);
            default -> segmentParagraphs(safeText);
        };

        List<ChunkDraft> drafts = new ArrayList<>();
        int idx = 0;
        for (RawSegment seg : segments) {
            String trimmed = seg.text().trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String parentCtx = seg.parentContext() != null && !seg.parentContext().isBlank()
                    ? capParent(seg.parentContext())
                    : capParent(trimmed);

            if (trimmed.length() <= maxChars || "GLOSSARY_ENTRY".equals(strategy)) {
                drafts.add(buildDraft(idx++, trimmed, parentCtx, seg.pageNumber(), seg.sectionTitle(), seg.charStart(), seg.charEnd()));
            } else {
                List<RawSegment> windows = splitOversizedSegment(seg, maxChars, overlap, parentCtx);
                for (RawSegment win : windows) {
                    if (!win.text().isBlank()) {
                        drafts.add(buildDraft(idx++, win.text().trim(), parentCtx, win.pageNumber(), win.sectionTitle(), win.charStart(), win.charEnd()));
                    }
                }
            }
        }

        return new ChunkingOutput(strategy, drafts, glossaryCandidates);
    }

    public static String normalizeQueryOrChunkTerms(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s-]", " ");
        String[] rawTokens = cleaned.trim().split("\\s+");
        List<String> out = new ArrayList<>(rawTokens.length * 2);
        for (String tok : rawTokens) {
            String t = tok.replace("-", "").trim();
            if (t.length() < 2) {
                continue;
            }
            out.add(t);
            String bi = BILINGUAL_EXPANSIONS.get(t);
            if (bi != null) {
                out.add(bi);
            }
            if (t.length() > 4 && t.endsWith("ing")) {
                out.add(t.substring(0, t.length() - 3));
            } else if (t.length() > 3 && t.endsWith("s")) {
                out.add(t.substring(0, t.length() - 1));
            }
        }
        return String.join(" ", out);
    }

    public static List<String> distinctQueryTerms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String cleaned = query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}\\s-]", " ");
        Set<String> set = new LinkedHashSet<>();
        Set<String> stopWords = Set.of(
                "the", "and", "for", "are", "what", "how", "many", "should", "with", "from", "when", "does",
                "apa", "berapa", "yang", "dan", "untuk", "jika", "saat", "pada", "atau", "dari", "adalah"
        );
        for (String tok : cleaned.trim().split("\\s+")) {
            String t = tok.trim();
            if (t.length() >= 2 && !stopWords.contains(t)) {
                set.add(t);
            }
        }
        return List.copyOf(set);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String resolveStrategy(String contentType, String defaultStrategy, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().toUpperCase(Locale.ROOT);
        }
        if ("GLOSSARY".equalsIgnoreCase(contentType)) {
            return "GLOSSARY_ENTRY";
        }
        if ("FAQ".equalsIgnoreCase(contentType)) {
            return "FAQ_QA";
        }
        if ("MARKDOWN".equalsIgnoreCase(contentType) || "POLICY_SOP".equalsIgnoreCase(contentType)) {
            if (defaultStrategy == null || defaultStrategy.isBlank() || "PARAGRAPH".equalsIgnoreCase(defaultStrategy)) {
                return "MARKDOWN_SECTION";
            }
        }
        return defaultStrategy != null && !defaultStrategy.isBlank()
                ? defaultStrategy.trim().toUpperCase(Locale.ROOT)
                : "PARAGRAPH";
    }

    private List<RawSegment> segmentMarkdownSections(String text) {
        List<IndexedMarkdownBlock> blocks = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        Integer currentPage = null;
        String currentSection = null;
        int currentLevel = 0;
        StringBuilder buf = new StringBuilder();
        int segStart = 0;
        int cursor = 0;
        int h1GroupId = 0;

        for (String line : lines) {
            int lineLen = line.length() + 1;
            Matcher pageMatcher = PAGE_MARKER.matcher(line);
            if (pageMatcher.matches()) {
                if (!buf.toString().isBlank()) {
                    blocks.add(new IndexedMarkdownBlock(h1GroupId, currentLevel, buf.toString(), currentPage, currentSection, segStart, Math.max(segStart, cursor - 1)));
                    buf.setLength(0);
                }
                String pNum = pageMatcher.group(1) != null ? pageMatcher.group(1) : pageMatcher.group(2);
                currentPage = Integer.parseInt(pNum);
                cursor += lineLen;
                segStart = cursor;
                continue;
            }

            Matcher headingMatcher = HEADING_LINE.matcher(line.trim());
            if (headingMatcher.matches()) {
                if (!buf.toString().isBlank()) {
                    blocks.add(new IndexedMarkdownBlock(h1GroupId, currentLevel, buf.toString(), currentPage, currentSection, segStart, Math.max(segStart, cursor - 1)));
                    buf.setLength(0);
                    segStart = cursor;
                }
                currentLevel = headingMatcher.group(1).length();
                if (currentLevel == 1) {
                    h1GroupId++;
                }
                currentSection = headingMatcher.group(2).trim();
            }
            if (buf.isEmpty()) {
                segStart = cursor;
            }
            buf.append(line).append('\n');
            cursor += lineLen;
        }

        if (!buf.toString().isBlank()) {
            blocks.add(new IndexedMarkdownBlock(h1GroupId, currentLevel, buf.toString(), currentPage, currentSection, segStart, Math.min(text.length(), cursor)));
        }

        // Build hierarchical parent context per H1 group so child H2/H3 sections know their enclosing H1 parent!
        Map<Integer, StringBuilder> h1ParentTextByGroup = new java.util.HashMap<>();
        for (IndexedMarkdownBlock b : blocks) {
            if (b.h1GroupId() > 0) {
                h1ParentTextByGroup.computeIfAbsent(b.h1GroupId(), k -> new StringBuilder())
                        .append(b.text().trim()).append("\n\n");
            }
        }

        List<RawSegment> segments = new ArrayList<>(blocks.size());
        for (IndexedMarkdownBlock b : blocks) {
            StringBuilder groupSb = h1ParentTextByGroup.get(b.h1GroupId());
            String parentCtx = (groupSb != null && groupSb.length() > b.text().trim().length())
                    ? capParent(groupSb.toString().trim())
                    : capParent(b.text().trim());
            segments.add(new RawSegment(b.text(), parentCtx, b.pageNumber(), b.sectionTitle(), b.charStart(), b.charEnd()));
        }
        return segments;
    }

    private List<RawSegment> segmentGlossary(String text, List<GlossaryCandidate> glossaryOut) {
        List<RawSegment> segments = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        String currentTerm = null;
        List<String> currentAliases = List.of();
        StringBuilder bodyBuf = new StringBuilder();
        StringBuilder fullBuf = new StringBuilder();
        int segStart = 0;
        int cursor = 0;
        int entryIndex = 0;

        for (String line : lines) {
            int lineLen = line.length() + 1;
            Matcher m = GLOSSARY_HEADER.matcher(line.trim());
            if (m.matches()) {
                if (!fullBuf.toString().isBlank()) {
                    String s = fullBuf.toString();
                    segments.add(new RawSegment(s, capParent(s.trim()), null, currentTerm, segStart, Math.max(segStart, cursor - 1)));
                    if (currentTerm != null && !bodyBuf.toString().isBlank()) {
                        glossaryOut.add(new GlossaryCandidate(currentTerm, currentAliases, bodyBuf.toString().trim(), entryIndex));
                    }
                    entryIndex++;
                    fullBuf.setLength(0);
                    bodyBuf.setLength(0);
                }
                currentTerm = m.group(1).trim();
                String aliasGroup = m.group(2);
                if (aliasGroup != null && !aliasGroup.isBlank()) {
                    currentAliases = Arrays.stream(aliasGroup.split("[,;]"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();
                } else {
                    currentAliases = List.of();
                }
                segStart = cursor;
                fullBuf.append(line).append('\n');
            } else {
                if (fullBuf.isEmpty() && !line.isBlank()) {
                    segStart = cursor;
                }
                if (!fullBuf.isEmpty() || !line.isBlank()) {
                    fullBuf.append(line).append('\n');
                    if (currentTerm != null) {
                        bodyBuf.append(line).append('\n');
                    }
                }
            }
            cursor += lineLen;
        }

        if (!fullBuf.toString().isBlank()) {
            String s = fullBuf.toString();
            segments.add(new RawSegment(s, capParent(s.trim()), null, currentTerm, segStart, Math.min(text.length(), cursor)));
            if (currentTerm != null && !bodyBuf.toString().isBlank()) {
                glossaryOut.add(new GlossaryCandidate(currentTerm, currentAliases, bodyBuf.toString().trim(), entryIndex));
            }
        }
        if (segments.isEmpty() && !text.isBlank()) {
            return segmentParagraphs(text);
        }
        return segments;
    }

    private List<RawSegment> segmentFaq(String text) {
        List<RawSegment> segments = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        String currentQuestion = null;
        StringBuilder buf = new StringBuilder();
        int segStart = 0;
        int cursor = 0;

        for (String line : lines) {
            int lineLen = line.length() + 1;
            if (FAQ_START.matcher(line.trim()).matches()) {
                if (!buf.toString().isBlank()) {
                    String s = buf.toString();
                    segments.add(new RawSegment(s, capParent(s.trim()), null, currentQuestion, segStart, Math.max(segStart, cursor - 1)));
                    buf.setLength(0);
                }
                currentQuestion = line.trim().replaceAll("^#+\\s*", "");
                segStart = cursor;
            }
            if (!buf.isEmpty() || !line.isBlank()) {
                if (buf.isEmpty()) {
                    segStart = cursor;
                }
                buf.append(line).append('\n');
            }
            cursor += lineLen;
        }
        if (!buf.toString().isBlank()) {
            String s = buf.toString();
            segments.add(new RawSegment(s, capParent(s.trim()), null, currentQuestion, segStart, Math.min(text.length(), cursor)));
        }
        return segments.isEmpty() ? segmentParagraphs(text) : segments;
    }

    private List<RawSegment> segmentParagraphs(String text) {
        List<RawSegment> segments = new ArrayList<>();
        Integer currentPage = null;
        String currentSection = null;
        String[] blocks = text.split("\\n\\s*\\n");
        int searchFrom = 0;

        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int idx = text.indexOf(trimmed, searchFrom);
            int start = idx >= 0 ? idx : searchFrom;
            int end = start + trimmed.length();
            searchFrom = Math.min(text.length(), end);

            Matcher pm = PAGE_MARKER.matcher(trimmed);
            if (pm.matches()) {
                String pNum = pm.group(1) != null ? pm.group(1) : pm.group(2);
                currentPage = Integer.parseInt(pNum);
                continue;
            }
            String firstLine = trimmed.split("\n", 2)[0].trim();
            Matcher hm = HEADING_LINE.matcher(firstLine);
            if (hm.matches()) {
                currentSection = hm.group(2).trim();
            }
            segments.add(new RawSegment(trimmed, capParent(trimmed), currentPage, currentSection, start, end));
        }
        return segments;
    }

    private List<RawSegment> segmentFixedWindow(String text, int maxChars, int overlap) {
        String parent = capParent(text.trim());
        return splitOversizedSegment(new RawSegment(text, parent, null, null, 0, text.length()), maxChars, overlap, parent);
    }

    private List<RawSegment> splitOversizedSegment(RawSegment seg, int maxChars, int overlap, String parentCtx) {
        List<RawSegment> out = new ArrayList<>();
        String s = seg.text();
        int len = s.length();
        int pos = 0;
        int step = Math.max(40, maxChars - overlap);

        while (pos < len) {
            int end = Math.min(len, pos + maxChars);
            if (end < len) {
                int lastSpace = s.lastIndexOf(' ', end);
                if (lastSpace > pos + (maxChars / 2)) {
                    end = lastSpace;
                }
            }
            String slice = s.substring(pos, end).trim();
            if (!slice.isEmpty()) {
                out.add(new RawSegment(slice, parentCtx, seg.pageNumber(), seg.sectionTitle(), seg.charStart() + pos, seg.charStart() + end));
            }
            if (end >= len) {
                break;
            }
            pos = Math.max(pos + 1, Math.max(pos + step / 2, end - overlap));
            if (pos < len && s.charAt(pos) != ' ' && pos > 0) {
                int nextSpace = s.indexOf(' ', pos);
                if (nextSpace > 0 && nextSpace - pos < 25) {
                    pos = nextSpace + 1;
                }
            }
            if (out.size() > 500) {
                break;
            }
        }
        return out;
    }

    private ChunkDraft buildDraft(
            int index,
            String content,
            String parentSectionContent,
            Integer pageNumber,
            String sectionTitle,
            int charStart,
            int charEnd
    ) {
        String normalizedTerms = normalizeQueryOrChunkTerms((sectionTitle != null ? sectionTitle + " " : "") + content);
        int tokenCount = Math.max(1, content.split("\\s+").length);
        String hashBasis = (pageNumber != null ? "p" + pageNumber : "")
                + "|" + (sectionTitle != null ? sectionTitle : "")
                + "|" + content;
        return new ChunkDraft(
                index,
                content,
                parentSectionContent,
                sha256Hex(hashBasis),
                normalizedTerms,
                tokenCount,
                pageNumber,
                sectionTitle,
                Math.max(0, charStart),
                Math.max(charStart, charEnd)
        );
    }

    private static String capParent(String text) {
        if (text == null) {
            return null;
        }
        if (text.length() <= MAX_PARENT_SECTION_CHARS) {
            return text;
        }
        return text.substring(0, MAX_PARENT_SECTION_CHARS) + "...";
    }

    private record IndexedMarkdownBlock(
            int h1GroupId,
            int headingLevel,
            String text,
            Integer pageNumber,
            String sectionTitle,
            int charStart,
            int charEnd
    ) {
    }

    private record RawSegment(
            String text,
            String parentContext,
            Integer pageNumber,
            String sectionTitle,
            int charStart,
            int charEnd
    ) {
    }
}
