package io.cranial.platform.conversation.engine;

import io.cranial.platform.conversation.model.ClarificationOption;
import io.cranial.platform.conversation.model.StructuredUiBlock;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class ClarificationEngine {

    private static final Pattern SPENDING_AMBIGUOUS_PATTERNS = Pattern.compile(
            "(?i)^(?:bagaimana\\s+pengeluaran(?:\\s+saya)?|cek\\s+pengeluaran|how(?:'s|\\s+is)\\s+my\\s+spending|analyze(?:\\s+my)?\\s+spending)$"
    );

    private static final Pattern BUDGET_AMBIGUOUS_PATTERNS = Pattern.compile(
            "(?i)^(?:bantu\\s+bikin\\s+anggaran|buatkan\\s+anggaran|help(?:\\s+me)?\\s+budget|create\\s+budget)$"
    );

    private static final Pattern INVENTORY_AMBIGUOUS_PATTERNS = Pattern.compile(
            "(?i)^(?:cek\\s+stok|lihat\\s+stok|check\\s+inventory|stock\\s+level)$"
    );

    public record ClarificationResult(
            boolean requiresClarification,
            String clarifyingQuestion,
            StructuredUiBlock clarificationBlock
    ) {
        public static ClarificationResult none() {
            return new ClarificationResult(false, null, null);
        }

        public static ClarificationResult needed(String question, StructuredUiBlock block) {
            return new ClarificationResult(true, question, block);
        }
    }

    public ClarificationResult evaluateClarification(String input, String locale) {
        if (input == null || input.isBlank()) {
            return ClarificationResult.none();
        }

        String trimmed = input.trim();
        boolean isId = "id".equalsIgnoreCase(locale);

        if (SPENDING_AMBIGUOUS_PATTERNS.matcher(trimmed).find()) {
            List<ClarificationOption> options = List.of(
                    new ClarificationOption("opt_today", isId ? "Hari Ini" : "Today", isId ? "Pengeluaran hari ini" : "Today's spending", isId ? "Lihat transaksi pengeluaran hari ini" : "View today's transactions"),
                    new ClarificationOption("opt_week", isId ? "Minggu Ini" : "This Week", isId ? "Pengeluaran 7 hari terakhir" : "Past 7 days spending", isId ? "Tren mingguan" : "Weekly trend"),
                    new ClarificationOption("opt_month", isId ? "Bulan Ini" : "This Month", isId ? "Pengeluaran bulan berjalan" : "Current calendar month", isId ? "Evaluasi rasio 50/30/20 bulan ini" : "Evaluate 50/30/20 ratio this month")
            );

            StructuredUiBlock block = StructuredUiBlock.clarification(
                    isId ? "Pilih Rentang Waktu Pengeluaran" : "Select Spending Timeframe",
                    isId ? "Untuk menganalisis pos pengeluaran Anda dengan tepat, pilih periode yang ingin diperiksa:" : "To accurately analyze your spending, please select a timeframe:",
                    options
            );

            return ClarificationResult.needed(
                    isId ? "Periode pengeluaran mana yang ingin Anda periksa?" : "Which spending timeframe would you like to review?",
                    block
            );
        }

        if (BUDGET_AMBIGUOUS_PATTERNS.matcher(trimmed).find()) {
            List<ClarificationOption> options = List.of(
                    new ClarificationOption("opt_inc_5", "Rp 5.000.000 / bln", isId ? "Gaji saya 5 juta per bulan" : "My monthly income is Rp 5M", isId ? "Paket anggaran dasar" : "Basic budget tier"),
                    new ClarificationOption("opt_inc_10", "Rp 10.000.000 / bln", isId ? "Gaji saya 10 juta per bulan" : "My monthly income is Rp 10M", isId ? "Paket menengah mandiri" : "Intermediate budget tier"),
                    new ClarificationOption("opt_inc_custom", isId ? "Penghasilan Kustom" : "Custom Income", isId ? "Gaji saya [masukkan angka] per bulan" : "My monthly income is [amount]", isId ? "Ketikkan nominal penghasilan spesifik Anda" : "Provide exact income amount")
            );

            StructuredUiBlock block = StructuredUiBlock.clarification(
                    isId ? "Pilih Nominal Penghasilan Bulanan" : "Select Monthly Income",
                    isId ? "Alokasi anggaran 50/30/20 dihitung dari penghasilan bersih bulanan Anda:" : "The 50/30/20 budget framework is calculated from your net monthly income:",
                    options
            );

            return ClarificationResult.needed(
                    isId ? "Berapa perkiraan total penghasilan bersih bulanan Anda?" : "What is your approximate monthly net income?",
                    block
            );
        }

        if (INVENTORY_AMBIGUOUS_PATTERNS.matcher(trimmed).find()) {
            List<ClarificationOption> options = List.of(
                    new ClarificationOption("opt_inv_low", isId ? "Stok Menipis (Di Bawah Minimum)" : "Low Stock Items", isId ? "Tampilkan barang yang hampir habis" : "Show items below reorder threshold", isId ? "Daftar barang perlu restock segera" : "Immediate restock needed"),
                    new ClarificationOption("opt_inv_slow", isId ? "Stok Lambat Terjual (Deadstock)" : "Slow-Moving Deadstock", isId ? "Tampilkan stok yang tidak bergerak 30 hari" : "Show stock untouched for 30 days", isId ? "Identifikasi modal tertahan" : "Identify tied-up capital"),
                    new ClarificationOption("opt_inv_all", isId ? "Semua Kategori" : "All Inventory", isId ? "Ringkasan total nilai inventaris" : "Total inventory asset valuation", isId ? "Laporan menyeluruh" : "Comprehensive report")
            );

            StructuredUiBlock block = StructuredUiBlock.clarification(
                    isId ? "Pilih Jenis Pemeriksaan Stok" : "Select Inventory Inspection Type",
                    isId ? "Pilih kategori pemeriksaan inventaris toko yang Anda perlukan:" : "Select the inventory query focus you need:",
                    options
            );

            return ClarificationResult.needed(
                    isId ? "Kategori stok mana yang ingin Anda pantau?" : "Which inventory category would you like to inspect?",
                    block
            );
        }

        return ClarificationResult.none();
    }
}

