package io.cranial.platform.customer.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.cranial.platform.customer.entity.CustomerSlangLexiconEntity;
import io.cranial.platform.customer.mapper.CustomerSlangLexiconMapper;
import io.cranial.platform.customer.model.CustomerSlangLexiconView;
import io.cranial.platform.customer.model.CustomerSlangReplacementView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, local-first Indonesian slang and colloquial spelling normalization engine
 * for customer support chats, reviews, and inquiries.
 *
 * <p>Preserves PII redaction masks, collapses expressive character elongations (e.g. lemooottt -> lemot),
 * normalizes multi-word colloquial phrases and -in verb suffixes (e.g. batalin, balikin duit,
 * lemot bgt, gak bisa login), and flags colloquial banking/refund demands to reinforce CRANIAL's
 * strict non-banking money management boundary.
 */
@Component
public class CustomerSlangNormalizer {

    private static final Pattern PII_PLACEHOLDER_PATTERN = Pattern.compile(
            "\\[(?:EMAIL|CARD|NIK|PHONE|REDACTED)[^\\]]*\\]"
    );

    private static final Pattern ELONGATION_PATTERN = Pattern.compile(
            "(?i)\\b(?!(?:iii|vii|viii)\\b)([a-zA-Z]*?)([a-zA-Z])\\2{2,}([a-zA-Z]*)\\b"
    );

    private static final Pattern UNMISTAKABLE_ID_SLANG_HINT = Pattern.compile(
            "(?i)\\b(?:batalin|balikin|duit|lemot|ngelag|bgt|banget|gak|nggak|ngga|gabisa|tolongin|bantuin|sambungin|gajian|boncos|bokek|nyatet|catet)\\b"
    );

    public record BuiltInRule(
            String slangTerm,
            Pattern pattern,
            String replacement,
            String category,
            boolean bankingBoundaryHint,
            String explanation
    ) {}

    public record NormalizationOutput(
            String originalText,
            String normalizedText,
            boolean slangDetected,
            int elongationCollapsedCount,
            List<CustomerSlangReplacementView> replacements,
            boolean bankingBoundaryHintDetected
    ) {}

    private static final List<BuiltInRule> PHRASE_RULES = new ArrayList<>();
    private static final List<BuiltInRule> TOKEN_RULES = new ArrayList<>();

    static {
        // --- Multi-Word Colloquial Phrases (Longest / Most Specific First) ---
        // Non-banking refund & transfer slang (strictly flagged for money management Promise Guard)
        addPhrase("balikin duit", "(?i)\\bbalikin\\s+(?:duit|uang|dana|saldo)\\b", "kembalikan uang",
                "BANKING_REFUND_SLANG", true, "Colloquial refund demand normalized for Promise Guard verification");
        addPhrase("minta duit balik", "(?i)\\b(?:minta\\s+)?duit\\s+balik\\b", "kembalikan uang",
                "BANKING_REFUND_SLANG", true, "Informal phrase demanding cash return");
        addPhrase("tf saldo ke rek", "(?i)\\b(?:tf|trf|transfer|kirim)\\s+(?:duit|uang|dana|saldo)\\s+ke\\s+rek(?:ening)?\\b",
                "transfer saldo ke rekening", "BANKING_REFUND_SLANG", true,
                "Colloquial bank transfer demand intercepted by non-banking policy");
        addPhrase("tf ke rek", "(?i)\\b(?:tf|trf|transfer)\\s+ke\\s+rek(?:ening)?\\b",
                "transfer saldo ke rekening", "BANKING_REFUND_SLANG", true,
                "Abbreviated bank transfer demand normalized to canonical form");
        addPhrase("cairin saldo", "(?i)\\b(?:cairin|tarik)\\s+(?:saldo|duit|dana)\\b",
                "tarik tunai otomatis", "BANKING_REFUND_SLANG", true,
                "Colloquial cash withdrawal/disbursement demand");

        // Authentication & account access slang
        addPhrase("gak bisa login", "(?i)\\b(?:ga+k?|gk+|ngga+k?|kagak|tdk)\\s+(?:bi+sa+|bsa*)\\s+(?:login|masuk|log\\s*in)\\b",
                "tidak bisa login", "AUTH_ACCESS_SLANG", false,
                "Colloquial authentication failure phrasing mapped to account security");
        addPhrase("gabisa login", "(?i)\\b(?:ga+bi+sa+|gbs|ga+kbi+sa+)\\s+(?:login|masuk|log\\s*in)\\b",
                "tidak bisa login", "AUTH_ACCESS_SLANG", false,
                "Concatenated negation + login issue normalized");
        addPhrase("lupa pw", "(?i)\\blupa\\s+(?:pw|pass|sandi)\\b",
                "lupa password", "AUTH_ACCESS_SLANG", false,
                "Abbreviated forgotten password support request");

        // App performance & bug slang
        addPhrase("lemot bgt", "(?i)\\b(?:lemot|lelet|ngelag|lambat)\\s+(?:bgt|banget|kali|pol)\\b",
                "lambat sekali", "PERFORMANCE_SLANG", false,
                "Colloquial complaint about severe app sluggishness or sync latency");
        addPhrase("parah bgt", "(?i)\\b(?:parah|rusak|ancur|hancur)\\s+(?:bgt|banget)\\b",
                "parah sekali", "PERFORMANCE_SLANG", false,
                "High-intensity frustration phrase normalized to urgency trigger");
        addPhrase("keluar sendiri", "(?i)\\b(?:keluar\\s+sendiri|mental\\s+sendiri|mental\\s+terus)\\b",
                "force close", "PERFORMANCE_SLANG", false,
                "Colloquial Indonesian description for application crash / force close");

        // OCR & receipt tracking slang
        addPhrase("struk ga kebaca", "(?i)\\bstruk\\s+(?:gak|ga|gk|nggak|gabisa)\\s+(?:kebaca|terbaca|discan|di-scan)\\b",
                "struk rusak error", "OCR_SUPPORT_SLANG", false,
                "Colloquial receipt OCR recognition failure report");

        // Escalation & urgency shorthand phrases
        addPhrase("sambungin ke cs", "(?i)\\b(?:sambungin|hubungin)\\s+(?:gw\\s+|saya\\s+|aku\\s+)?(?:ke|sama|sm|dengan)\\s+(?:cs|agen|operator)(?:\\s+manusia)?\\b",
                "bicara dengan agen manusia", "ESCALATION_SLANG", false,
                "Colloquial demand for live human support agent handoff");
        addPhrase("bicara sm orang", "(?i)\\b(?:bicara|ngobrol)\\s+(?:sama|sm)\\s+(?:orang|manusia|cs\\s+manusia)\\b",
                "bicara dengan agen manusia", "ESCALATION_SLANG", false,
                "Informal phrasing requesting human operator instead of automated bot");
        addPhrase("jgn dijawab bot", "(?i)\\b(?:jgn|jangan)\\s+(?:dijawab\\s+)?bot\\b",
                "jangan dijawab bot", "ESCALATION_SLANG", false,
                "Abbreviated bot refusal normalized to explicit escalation trigger");
        addPhrase("skrg jg", "(?i)\\b(?:skrg|skg|sekarang)\\s+(?:jg|jga|juga)\\b",
                "sekarang juga", "CHAT_ABBREVIATION", false,
                "Abbreviated immediate urgency expression");

        // Numerical IDR abbreviations
        addPhrase("nominal jt", "(?i)\\b(\\d+)\\s*(?:jt|juta)\\b",
                "Rp $1.000.000", "FINANCIAL_SLANG", false,
                "Abbreviated million Rupiah expanded to canonical IDR notation");
        addPhrase("nominal rb", "(?i)\\b(\\d+)\\s*(?:rb|rebu|ribu)\\b",
                "Rp $1.000", "FINANCIAL_SLANG", false,
                "Abbreviated thousand Rupiah expanded to canonical IDR notation");

        // --- Single-Token Colloquialisms, -in Suffixes & Chat Abbreviations ---
        // Action -in colloquial verbs
        addToken("batalin", "(?i)\\bbatalin\\b", "batalkan", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for cancelling a record, plan, or subscription");
        addToken("cancelin", "(?i)\\bcancelin\\b", "batalkan", "ACTION_COLLOQUIALISM", false,
                "Code-mixed informal verb for cancel");
        addToken("balikin", "(?i)\\bbalikin\\b", "kembalikan", "BANKING_REFUND_SLANG", true,
                "Informal -in suffix verb for returning/refunding");
        addToken("benerin", "(?i)\\b(?:benerin|betulin)\\b", "perbaiki", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for fixing an issue");
        addToken("hapusin", "(?i)\\b(?:hapusin|ilangin)\\b", "hapus", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for deleting an entry");
        addToken("tambahin", "(?i)\\btambahin\\b", "tambahkan", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for requesting a new feature");
        addToken("jelasin", "(?i)\\bjelasin\\b", "jelaskan", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for asking an explanation");
        addToken("tolongin", "(?i)\\b(?:tolongin|bantuin)\\b", "bantu", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb requesting support assistance");
        addToken("aktifin", "(?i)\\b(?:aktifin|nyalain)\\b", "aktifkan", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for enabling a setting");
        addToken("matiin", "(?i)\\bmatiin\\b", "nonaktifkan", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for disabling a setting");
        addToken("cairin", "(?i)\\bcairin\\b", "cairkan", "BANKING_REFUND_SLANG", true,
                "Informal -in suffix verb for disbursing funds (non-banking boundary hint)");
        addToken("bayarin", "(?i)\\bbayarin\\b", "bayarkan", "BANKING_REFUND_SLANG", true,
                "Informal -in suffix verb for executing bill payment (non-banking boundary hint)");
        addToken("sambungin", "(?i)\\b(?:sambungin|hubungin)\\b", "hubungkan", "ACTION_COLLOQUIALISM", false,
                "Informal -in suffix verb for connecting to support");

        // Performance, frustration & app bug slang
        addToken("lemot", "(?i)\\b(?:lemot|lelet|ngelag)\\b", "lambat", "PERFORMANCE_SLANG", false,
                "Colloquial Indonesian term for slow or laggy performance");
        addToken("ngefreeze", "(?i)\\b(?:ngefreeze|ngehang|nyangkut|macet)\\b", "freeze", "PERFORMANCE_SLANG", false,
                "Colloquial term for unresponsive/frozen screen");
        addToken("eror", "(?i)\\beror\\b", "error", "PERFORMANCE_SLANG", false,
                "Phonetic Indonesian spelling of error");
        addToken("ilang", "(?i)\\b(?:ilang|kehapus|raib)\\b", "hilang", "PERFORMANCE_SLANG", false,
                "Colloquial spelling for missing or lost ledger entries");
        addToken("ancur", "(?i)\\b(?:ancur|bapuk|zonk)\\b", "rusak", "PERFORMANCE_SLANG", false,
                "Colloquial negative quality slang normalized to rusak");
        addToken("kesel", "(?i)\\b(?:kesel|sebel|kzl)\\b", "kesal", "PERFORMANCE_SLANG", false,
                "Colloquial spelling of annoyed/frustrated");
        addToken("catetannya", "(?i)\\bcatetannya\\b", "catatannya", "FINANCIAL_SLANG", false,
                "Colloquial spelling of ledger records");
        addToken("catet", "(?i)\\b(?:nyatet|catet)\\b", "catat", "FINANCIAL_SLANG", false,
                "Colloquial spelling of recording an expense");
        addToken("itung", "(?i)\\b(?:ngitung|itung)\\b", "hitung", "FINANCIAL_SLANG", false,
                "Colloquial spelling of calculating budget figures");

        // Negations & intensifiers
        addToken("gabisa", "(?i)\\b(?:ga+bi+sa+|gbs|ga+kbi+sa+)\\b", "tidak bisa", "NEGATION_SHORTHAND", false,
                "Merged colloquial negation for 'cannot'");
        addToken("gak", "(?i)\\b(?:ga+k?|gk+|ngga+k?|kagak|tdk)\\b", "tidak", "NEGATION_SHORTHAND", false,
                "Colloquial negation normalized to formal 'tidak'");
        addToken("jgn", "(?i)\\bjgn\\b", "jangan", "NEGATION_SHORTHAND", false,
                "Abbreviated prohibitive negation 'jangan'");
        addToken("blm", "(?i)\\b(?:blm|belom)\\b", "belum", "NEGATION_SHORTHAND", false,
                "Colloquial/abbreviated form of 'belum'");
        addToken("bgt", "(?i)\\b(?:bgt|banget|bener2|bnr2)\\b", "sekali", "CHAT_ABBREVIATION", false,
                "Colloquial intensifier normalized to 'sekali'");
        addToken("mulu", "(?i)\\b(?:mulu|melulu)\\b", "terus", "CHAT_ABBREVIATION", false,
                "Colloquial repetition marker normalized to 'terus'");

        // Pronouns & common chat abbreviations
        addToken("gw", "(?i)\\b(?:gw|gue|gua|sy|aku|aq)\\b", "saya", "CHAT_ABBREVIATION", false,
                "Informal first-person pronoun normalized to 'saya'");
        addToken("udh", "(?i)\\b(?:udh|udah|sdh)\\b", "sudah", "CHAT_ABBREVIATION", false,
                "Colloquial/abbreviated form of 'sudah'");
        addToken("bs", "(?i)\\b(?:bs|bsa)\\b", "bisa", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'bisa'");
        addToken("yg", "(?i)\\byg\\b", "yang", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'yang'");
        addToken("dgn", "(?i)\\b(?:dgn|dg|sm)\\b", "dengan", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'dengan'");
        addToken("utk", "(?i)\\butk\\b", "untuk", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'untuk'");
        addToken("krn", "(?i)\\b(?:krn|karna)\\b", "karena", "CHAT_ABBREVIATION", false,
                "Colloquial/abbreviated form of 'karena'");
        addToken("tp", "(?i)\\b(?:tp|tpi)\\b", "tetapi", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'tetapi'");
        addToken("trs", "(?i)\\b(?:trs|trus)\\b", "lalu", "CHAT_ABBREVIATION", false,
                "Colloquial conjunction normalized to 'lalu'");
        addToken("lg", "(?i)\\b(?:lg|lgi)\\b", "lagi", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'lagi'");
        addToken("jg", "(?i)\\b(?:jg|jga)\\b", "juga", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'juga'");
        addToken("dr", "(?i)\\b(?:dr|dri)\\b", "dari", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'dari'");
        addToken("dlm", "(?i)\\bdlm\\b", "dalam", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'dalam'");
        addToken("skrg", "(?i)\\b(?:skrg|skg)\\b", "sekarang", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'sekarang'");
        addToken("cpt", "(?i)\\b(?:cpt|cepet|cepetan|buruan|gercep)\\b", "segera", "CHAT_ABBREVIATION", false,
                "Urgency slang normalized to 'segera'");
        addToken("gimana", "(?i)\\b(?:gimana|gmn|gmna|bgmn)\\b", "bagaimana", "CHAT_ABBREVIATION", false,
                "Colloquial question word normalized to 'bagaimana'");
        addToken("kenapa", "(?i)\\b(?:kenapa|knp|knpa)\\b", "mengapa", "CHAT_ABBREVIATION", false,
                "Colloquial question word normalized to 'mengapa'");
        addToken("kpn", "(?i)\\bkpn\\b", "kapan", "CHAT_ABBREVIATION", false,
                "Chat shorthand for 'kapan'");
        addToken("tlg", "(?i)\\b(?:tlg|plis|pls)\\b", "tolong", "CHAT_ABBREVIATION", false,
                "Chat shorthand for polite request 'tolong'");
        addToken("bener", "(?i)\\bbener\\b", "benar", "CHAT_ABBREVIATION", false,
                "Colloquial spelling of 'benar'");
        addToken("makasih", "(?i)\\b(?:mksh|makasih)\\b", "terima kasih", "CHAT_ABBREVIATION", false,
                "Informal gratitude expression normalized");
        addToken("mantul", "(?i)\\b(?:mantul|gokil)\\b", "mantap", "CHAT_ABBREVIATION", false,
                "Positive Indonesian praise slang normalized to 'mantap'");

        // Financial, currency & account terms
        addToken("duit", "(?i)\\b(?:duit|fulus)\\b", "uang", "FINANCIAL_SLANG", false,
                "Colloquial Indonesian term for money");
        addToken("tf", "(?i)\\b(?:tf|trf)\\b", "transfer", "BANKING_REFUND_SLANG", true,
                "Chat shorthand for bank transfer (non-banking boundary hint)");
        addToken("rek", "(?i)\\brek\\b", "rekening", "BANKING_REFUND_SLANG", true,
                "Chat shorthand for bank account (rekening)");
        addToken("pw", "(?i)\\bpw\\b", "password", "AUTH_ACCESS_SLANG", false,
                "Chat shorthand for account password");
        addToken("nabung", "(?i)\\bnabung\\b", "tabungan", "FINANCIAL_SLANG", false,
                "Informal word for savings");
        addToken("gajian", "(?i)\\bgajian\\b", "gaji", "FINANCIAL_SLANG", false,
                "Informal word for payday / monthly salary");
        addToken("boncos", "(?i)\\b(?:boncos|tekor)\\b", "rugi", "FINANCIAL_SLANG", false,
                "Colloquial financial slang for overspending / budget deficit");
        addToken("bokek", "(?i)\\bbokek\\b", "saldo menipis", "FINANCIAL_SLANG", false,
                "Colloquial slang for depleted budget balance");
        addToken("gocap", "(?i)\\b(?:gocap|goban)\\b", "Rp 50.000", "FINANCIAL_SLANG", false,
                "Hokkien-Indonesian currency slang for Rp 50.000");
        addToken("ceban", "(?i)\\bceban\\b", "Rp 10.000", "FINANCIAL_SLANG", false,
                "Hokkien-Indonesian currency slang for Rp 10.000");
        addToken("pekgo", "(?i)\\bpekgo\\b", "Rp 150.000", "FINANCIAL_SLANG", false,
                "Hokkien-Indonesian currency slang for Rp 150.000");
        addToken("seceng", "(?i)\\bseceng\\b", "Rp 1.000", "FINANCIAL_SLANG", false,
                "Hokkien-Indonesian currency slang for Rp 1.000");
        addToken("noceng", "(?i)\\bnoceng\\b", "Rp 2.000", "FINANCIAL_SLANG", false,
                "Hokkien-Indonesian currency slang for Rp 2.000");
    }

    private static void addPhrase(String term, String regex, String replacement, String category, boolean bankingHint, String explanation) {
        PHRASE_RULES.add(new BuiltInRule(term, Pattern.compile(regex), replacement, category, bankingHint, explanation));
    }

    private static void addToken(String term, String regex, String replacement, String category, boolean bankingHint, String explanation) {
        TOKEN_RULES.add(new BuiltInRule(term, Pattern.compile(regex), replacement, category, bankingHint, explanation));
    }

    private final CustomerSlangLexiconMapper lexiconMapper;

    @Autowired
    public CustomerSlangNormalizer(CustomerSlangLexiconMapper lexiconMapper) {
        this.lexiconMapper = lexiconMapper;
    }

    /**
     * Convenience constructor for standalone unit testing without database mapper.
     */
    public CustomerSlangNormalizer() {
        this.lexiconMapper = null;
    }

    public NormalizationOutput normalize(String text, String locale) {
        if (text == null || text.isBlank()) {
            return new NormalizationOutput(text != null ? text : "", text != null ? text : "", false, 0, List.of(), false);
        }

        boolean explicitEnglish = locale != null && locale.toLowerCase(Locale.ROOT).startsWith("en");
        boolean hasIndonesianSlangHint = UNMISTAKABLE_ID_SLANG_HINT.matcher(text).find();
        if (explicitEnglish && !hasIndonesianSlangHint) {
            return new NormalizationOutput(text, text, false, 0, List.of(), false);
        }

        // 1. Protect PII redaction masks ([EMAIL: ...], [NIK: ...], [PHONE: ...], [CARD: ...])
        List<String> protectedSpans = new ArrayList<>();
        String working = maskPiiPlaceholders(text, protectedSpans);

        List<CustomerSlangReplacementView> replacements = new ArrayList<>();
        int elongationCount = 0;
        boolean bankingHintDetected = false;

        // 2. Collapse expressive character elongations (e.g. lemooottt -> lemot, bgtttt -> bgt)
        StringBuilder elongatedSb = new StringBuilder();
        Matcher elongMatcher = ELONGATION_PATTERN.matcher(working);
        int lastIdx = 0;
        while (elongMatcher.find()) {
            String fullWord = elongMatcher.group(0);
            if (fullWord.startsWith("PII_SPAN_")) {
                continue;
            }
            String collapsed = fullWord.replaceAll("(?i)([a-z])\\1{2,}", "$1");
            if (!collapsed.equals(fullWord)) {
                elongationCount++;
                replacements.add(new CustomerSlangReplacementView(
                        fullWord,
                        collapsed,
                        "ELONGATION_SPELLING",
                        false,
                        "Collapsed expressive repeated characters in customer chat"
                ));
                elongatedSb.append(working, lastIdx, elongMatcher.start()).append(collapsed);
                lastIdx = elongMatcher.end();
            }
        }
        elongatedSb.append(working.substring(lastIdx));
        working = elongatedSb.toString();

        // 3. Apply active operator-governed custom lexicon entries (sorted longest slang_term first)
        List<CustomerSlangLexiconEntity> customRules = loadActiveCustomRules();
        Set<String> appliedCustomTerms = new HashSet<>();
        for (CustomerSlangLexiconEntity custom : customRules) {
            if (custom.getSlangTerm() == null || custom.getSlangTerm().isBlank()) continue;
            String termLower = custom.getSlangTerm().trim().toLowerCase(Locale.ROOT);
            Pattern customPattern = Pattern.compile("(?i)\\b" + Pattern.quote(termLower) + "\\b");
            Matcher m = customPattern.matcher(working);
            boolean matched = false;
            while (m.find()) {
                matched = true;
                boolean hint = Boolean.TRUE.equals(custom.getIsBankingBoundaryHint());
                if (hint) bankingHintDetected = true;
                replacements.add(new CustomerSlangReplacementView(
                        m.group(),
                        custom.getCanonicalForm(),
                        custom.getCategory() != null ? custom.getCategory() : "ACTION_COLLOQUIALISM",
                        hint,
                        custom.getExplanation() != null ? custom.getExplanation() : "Operator custom lexicon normalization"
                ));
            }
            if (matched) {
                working = customPattern.matcher(working).replaceAll(Matcher.quoteReplacement(custom.getCanonicalForm()));
                appliedCustomTerms.add(termLower);
            }
        }

        // 4. Apply built-in multi-word colloquial phrases
        for (BuiltInRule rule : PHRASE_RULES) {
            if (appliedCustomTerms.contains(rule.slangTerm().toLowerCase(Locale.ROOT))) {
                continue;
            }
            working = applyBuiltInRule(working, rule, replacements);
            if (rule.bankingBoundaryHint() && replacements.stream().anyMatch(r -> r.bankingBoundaryHint())) {
                bankingHintDetected = true;
            }
        }

        // 5. Apply built-in single-token colloquialisms & chat abbreviations
        for (BuiltInRule rule : TOKEN_RULES) {
            if (appliedCustomTerms.contains(rule.slangTerm().toLowerCase(Locale.ROOT))) {
                continue;
            }
            working = applyBuiltInRule(working, rule, replacements);
        }

        for (CustomerSlangReplacementView r : replacements) {
            if (r.bankingBoundaryHint()) {
                bankingHintDetected = true;
                break;
            }
        }

        // 6. Restore PII placeholders untouched
        working = restorePiiPlaceholders(working, protectedSpans);

        return new NormalizationOutput(
                text,
                working,
                !replacements.isEmpty(),
                elongationCount,
                replacements,
                bankingHintDetected
        );
    }

    public List<CustomerSlangLexiconView> listBuiltInAndCustomLexicon(String categoryFilter, String search) {
        List<CustomerSlangLexiconView> result = new ArrayList<>();
        Set<String> customTerms = new HashSet<>();

        if (lexiconMapper != null) {
            List<CustomerSlangLexiconEntity> dbEntries = lexiconMapper.selectList(
                    new LambdaQueryWrapper<CustomerSlangLexiconEntity>()
                            .orderByDesc(CustomerSlangLexiconEntity::getUpdatedAt)
            );
            for (CustomerSlangLexiconEntity e : dbEntries) {
                customTerms.add(e.getSlangTerm().toLowerCase(Locale.ROOT));
                if (matchesFilter(e.getSlangTerm(), e.getCanonicalForm(), e.getCategory(), categoryFilter, search)) {
                    result.add(new CustomerSlangLexiconView(
                            e.getId(),
                            e.getSlangTerm(),
                            e.getCanonicalForm(),
                            e.getCategory(),
                            e.getLocale(),
                            Boolean.TRUE.equals(e.getIsBankingBoundaryHint()),
                            e.getExplanation(),
                            "CUSTOM_DB",
                            Boolean.TRUE.equals(e.getIsActive()),
                            e.getUpdatedAt()
                    ));
                }
            }
        }

        List<BuiltInRule> allBuiltIn = new ArrayList<>(PHRASE_RULES);
        allBuiltIn.addAll(TOKEN_RULES);
        for (BuiltInRule b : allBuiltIn) {
            if (customTerms.contains(b.slangTerm().toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (matchesFilter(b.slangTerm(), b.replacement(), b.category(), categoryFilter, search)) {
                result.add(new CustomerSlangLexiconView(
                        UUID.nameUUIDFromBytes(("builtin:" + b.slangTerm()).getBytes()),
                        b.slangTerm(),
                        b.replacement(),
                        b.category(),
                        "id",
                        b.bankingBoundaryHint(),
                        b.explanation(),
                        "BUILT_IN",
                        true,
                        Instant.EPOCH
                ));
            }
        }

        return result;
    }

    private boolean matchesFilter(String slang, String canonical, String cat, String categoryFilter, String search) {
        if (categoryFilter != null && !categoryFilter.isBlank() && !"ALL".equalsIgnoreCase(categoryFilter)) {
            if (!categoryFilter.equalsIgnoreCase(cat)) {
                return false;
            }
        }
        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            boolean hit = (slang != null && slang.toLowerCase(Locale.ROOT).contains(q))
                    || (canonical != null && canonical.toLowerCase(Locale.ROOT).contains(q));
            return hit;
        }
        return true;
    }

    private List<CustomerSlangLexiconEntity> loadActiveCustomRules() {
        if (lexiconMapper == null) {
            return List.of();
        }
        try {
            List<CustomerSlangLexiconEntity> list = lexiconMapper.selectList(
                    new LambdaQueryWrapper<CustomerSlangLexiconEntity>()
                            .eq(CustomerSlangLexiconEntity::getIsActive, true)
            );
            list.sort((a, b) -> Integer.compare(
                    b.getSlangTerm() != null ? b.getSlangTerm().length() : 0,
                    a.getSlangTerm() != null ? a.getSlangTerm().length() : 0
            ));
            return list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String applyBuiltInRule(String working, BuiltInRule rule, List<CustomerSlangReplacementView> replacements) {
        Matcher matcher = rule.pattern().matcher(working);
        List<String> matchedGroups = new ArrayList<>();
        while (matcher.find()) {
            String group = matcher.group();
            if (group.contains("PII_SPAN_")) {
                continue;
            }
            matchedGroups.add(group);
        }
        if (matchedGroups.isEmpty()) {
            return working;
        }

        String updated = rule.pattern().matcher(working).replaceAll(rule.replacement());
        for (String original : matchedGroups) {
            String expandedCanonical = rule.pattern().matcher(original).replaceAll(rule.replacement());
            replacements.add(new CustomerSlangReplacementView(
                    original,
                    expandedCanonical,
                    rule.category(),
                    rule.bankingBoundaryHint(),
                    rule.explanation()
            ));
        }
        return updated;
    }

    private String maskPiiPlaceholders(String input, List<String> spans) {
        Matcher m = PII_PLACEHOLDER_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (m.find()) {
            int idx = spans.size();
            spans.add(m.group());
            sb.append(input, last, m.start()).append("PII_SPAN_").append(idx).append("_END");
            last = m.end();
        }
        sb.append(input.substring(last));
        return sb.toString();
    }

    private String restorePiiPlaceholders(String text, List<String> spans) {
        String restored = text;
        for (int i = 0; i < spans.size(); i++) {
            restored = restored.replace("PII_SPAN_" + i + "_END", spans.get(i));
        }
        return restored;
    }
}
