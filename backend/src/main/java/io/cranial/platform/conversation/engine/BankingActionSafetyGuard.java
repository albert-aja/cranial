package io.cranial.platform.conversation.engine;

import io.cranial.platform.conversation.model.BlockType;
import io.cranial.platform.conversation.model.StructuredUiBlock;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class BankingActionSafetyGuard {

    public record SafetyEvaluationResult(
            boolean isViolation,
            String safetyReason,
            String refusalMessage,
            List<StructuredUiBlock> refusalBlocks
    ) {
        public static SafetyEvaluationResult pass() {
            return new SafetyEvaluationResult(false, null, null, List.of());
        }

        public static SafetyEvaluationResult violation(String reason, String refusalMessage, List<StructuredUiBlock> blocks) {
            return new SafetyEvaluationResult(true, reason, refusalMessage, blocks);
        }
    }

    private static final List<Pattern> BANKING_ATTEMPT_PATTERNS = List.of(
            // Fund transfer / wire transfer
            Pattern.compile("(?i)\\b(?:transfer|kirim)\\s+(?:uang|dana|saldo|rupiah|duit)\\b"),
            Pattern.compile("(?i)\\b(?:wire|send|transfer)\\s+(?:money|funds|cash)\\b"),
            Pattern.compile("(?i)\\b(?:transfer|kirim)\\s+ke\\s+rekening\\b"),

            // Direct payment execution
            Pattern.compile("(?i)\\b(?:bayar|lunasi)\\s+(?:tagihan|listrik|air|cicilan)\\s+(?:sekarang|langsung)\\b"),
            Pattern.compile("(?i)\\b(?:pay|settle)\\s+(?:bill|debt|invoice)\\s+(?:now|immediately|directly)\\b"),
            Pattern.compile("(?i)\\b(?:charge|debit)\\s+my\\s+(?:card|account)\\b"),

            // Card issuing / account opening
            Pattern.compile("(?i)\\b(?:buka|buat)\\s+rekening\\s+(?:bank|baru)\\b"),
            Pattern.compile("(?i)\\b(?:open|create)\\s+bank\\s+account\\b"),
            Pattern.compile("(?i)\\b(?:bikin|cetak|issue)\\s+(?:kartu\\s+debit|kartu\\s+kredit)\\b"),
            Pattern.compile("(?i)\\b(?:issue|order)\\s+(?:debit|credit)\\s+card\\b"),

            // Cash withdrawal / ATM
            Pattern.compile("(?i)\\b(?:tarik\\s+tunai|ambil\\s+uang\\s+di\\s+atm)\\b"),
            Pattern.compile("(?i)\\b(?:withdraw\\s+cash|atm\\s+withdrawal)\\b")
    );

    public SafetyEvaluationResult evaluate(String userMessage, String locale) {
        if (userMessage == null || userMessage.isBlank()) {
            return SafetyEvaluationResult.pass();
        }

        for (Pattern pattern : BANKING_ATTEMPT_PATTERNS) {
            if (pattern.matcher(userMessage).find()) {
                boolean isId = "id".equalsIgnoreCase(locale);
                String refusalMessage = isId
                        ? "Permintaan eksekusi transaksi perbankan ditolak. CRANIAL dan Peggy adalah platform manajemen keuangan dan edukasi pencatatan anggaran, bukan aplikasi perbankan atau penyedia jasa pembayaran. Kami tidak dapat melakukan transfer dana, penarikan saldo, atau pembayaran tagihan."
                        : "Banking transaction execution request rejected. CRANIAL is strictly a money management and financial analytics platform, NOT a banking or payment execution system. We cannot initiate fund transfers, banking transactions, or bill payments.";

                StructuredUiBlock disclaimerBlock = StructuredUiBlock.financialDisclaimer(
                        isId ? "Batas Kebijakan Platform Non-Perbankan" : "Strict Non-Banking Platform Policy",
                        isId
                                ? "CRANIAL murni merupakan platform manajemen keuangan pribadi dan analitik pencatatan (non-banking platform). Sistem tidak memiliki integrasi gerbang pembayaran atau otorisasi transfer dana ke bank."
                                : "CRANIAL is strictly an educational money management and ledger analytics platform. It does not integrate payment rails, deposit facilities, or fund disbursement tools."
                );

                StructuredUiBlock actionCard = StructuredUiBlock.actionCard(
                        isId ? "Catat Pengeluaran di Buku Kas" : "Record in Expense Ledger",
                        isId
                                ? "Setelah Anda menyelesaikan pembayaran melalui aplikasi mobile banking atau ATM bank Anda, catat transaksi tersebut di buku kas Peggy untuk memperbarui analitik anggaran 50/30/20 Anda."
                                : "After executing the payment via your certified banking application, record the expense into your ledger to track budget allocation.",
                        isId ? "Buka Buku Kas Peggy" : "Open Expense Ledger",
                        "NAVIGATE_EXPENSE_ENTRY"
                );

                return SafetyEvaluationResult.violation(
                        "NON_BANKING_APPLICATION_POLICY",
                        refusalMessage,
                        List.of(disclaimerBlock, actionCard)
                );
            }
        }

        return SafetyEvaluationResult.pass();
    }
}

