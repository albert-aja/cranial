package io.cranial.platform.customer.engine;

import io.cranial.platform.customer.model.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class CustomerTriageEngine {

    private final CustomerPromiseGuard promiseGuard;

    public CustomerTriageEngine(CustomerPromiseGuard promiseGuard) {
        this.promiseGuard = promiseGuard;
    }

    public record TriageDecision(
            TicketCategory category,
            CustomerPriority priority,
            RoutingQueue routingQueue,
            boolean promiseGuardFlagged,
            List<String> promiseGuardReasons,
            String suggestedCannedResponse,
            double confidenceScore,
            boolean escalationRecommended,
            EscalationReason escalationReason
    ) {}

    public TriageDecision triage(
            String text,
            String locale,
            CustomerSentiment sentiment,
            double sentimentScore,
            CustomerUrgency urgency
    ) {
        String lower = text != null ? text.toLowerCase(Locale.ROOT) : "";
        boolean isId = locale != null && locale.equalsIgnoreCase("id");

        // 1. Evaluate Promise Guard (Money Management App Boundary: NO BANKING PROMISES)
        var promiseEval = promiseGuard.evaluate(text, locale);
        if (promiseEval.flagged()) {
            String canned = isId
                    ? "Halo, terima kasih telah menghubungi kami. " + promiseEval.boundaryDisclaimer() + " Tim pengawas kami akan meninjau catatan kendala Anda secara manual."
                    : "Hello, thank you for reaching out. " + promiseEval.boundaryDisclaimer() + " A support specialist will review your request.";

            return new TriageDecision(
                    TicketCategory.PROMISE_POLICY_RESTRICTION,
                    CustomerPriority.URGENT,
                    RoutingQueue.HUMAN_DISPUTE_DESK,
                    true,
                    promiseEval.detectedTriggers(),
                    canned,
                    0.96,
                    true,
                    EscalationReason.PROMISE_POLICY_RESTRICTION
            );
        }

        // 2. Explicit Human Agent Request Detection
        boolean explicitHumanRequested = lower.contains("human") || lower.contains("agen manusia") ||
                lower.contains("bicara dengan orang") || lower.contains("transfer me to a human") ||
                lower.contains("jangan dijawab bot") || lower.contains("talk to representative");

        // 3. Category Detection
        TicketCategory category;
        if (lower.contains("dark mode") || lower.contains("feature") || lower.contains("request") ||
                lower.contains("tambahkan") || lower.contains("can you add") || lower.contains("fitur baru")) {
            category = TicketCategory.FEATURE_REQUEST;
        } else if (lower.contains("freeze") || lower.contains("crash") || lower.contains("force close") ||
                lower.contains("error") || lower.contains("bug") || lower.contains("rusak") || lower.contains("hilang")) {
            category = TicketCategory.BUG_REPORT;
        } else if (lower.contains("budget") || lower.contains("50-30-20") || lower.contains("dana darurat") ||
                lower.contains("pos") || lower.contains("alokasi") || lower.contains("tabungan")) {
            category = TicketCategory.BUDGET_AND_POCKETS;
        } else if (lower.contains("scan") || lower.contains("receipt") || lower.contains("struk") ||
                lower.contains("pengeluaran") || lower.contains("expense") || lower.contains("catat")) {
            category = TicketCategory.EXPENSE_TRACKING_ISSUE;
        } else if (lower.contains("password") || lower.contains("login") || lower.contains("pin") ||
                lower.contains("biometric") || lower.contains("keamanan akun")) {
            category = TicketCategory.ACCOUNT_AND_SECURITY;
        } else if (lower.contains("saving") || lower.contains("keuangan") || lower.contains("money management") ||
                lower.contains("investasi") || lower.contains("gaji")) {
            category = TicketCategory.MONEY_MANAGEMENT_INQUIRY;
        } else {
            category = TicketCategory.GENERAL_INQUIRY;
        }

        // 4. Escalation Evaluation
        boolean escalationRecommended = false;
        EscalationReason escalationReason = null;

        if (explicitHumanRequested) {
            escalationRecommended = true;
            escalationReason = EscalationReason.USER_REQUESTED_AGENT;
        } else if (sentimentScore <= -0.6 || urgency == CustomerUrgency.CRITICAL) {
            escalationRecommended = true;
            escalationReason = EscalationReason.HIGH_FRUSTRATION;
        } else if (category == TicketCategory.BUG_REPORT && sentiment == CustomerSentiment.NEGATIVE && sentimentScore <= -0.4) {
            escalationRecommended = true;
            escalationReason = EscalationReason.COMPLEX_UNRESOLVED;
        }

        // 5. Priority & Queue
        CustomerPriority priority;
        RoutingQueue queue;

        if (escalationRecommended || urgency == CustomerUrgency.CRITICAL) {
            priority = CustomerPriority.URGENT;
        } else if (sentiment == CustomerSentiment.NEGATIVE || urgency == CustomerUrgency.HIGH) {
            priority = CustomerPriority.HIGH;
        } else if (sentiment == CustomerSentiment.NEUTRAL || category == TicketCategory.MONEY_MANAGEMENT_INQUIRY) {
            priority = CustomerPriority.NORMAL;
        } else {
            priority = CustomerPriority.LOW;
        }

        if (escalationRecommended && explicitHumanRequested) {
            queue = RoutingQueue.HUMAN_DISPUTE_DESK;
        } else if (category == TicketCategory.BUG_REPORT) {
            queue = RoutingQueue.TECH_SUPPORT;
        } else if (category == TicketCategory.BUDGET_AND_POCKETS || category == TicketCategory.MONEY_MANAGEMENT_INQUIRY) {
            queue = RoutingQueue.FINANCIAL_GUIDANCE;
        } else if (category == TicketCategory.ACCOUNT_AND_SECURITY) {
            queue = RoutingQueue.SECURITY_ESCALATION;
        } else {
            queue = RoutingQueue.TIER1_SUPPORT;
        }

        // 6. Canned Response Generation
        String canned = generateCannedResponse(category, isId);

        return new TriageDecision(
                category,
                priority,
                queue,
                false,
                List.of(),
                canned,
                0.90,
                escalationRecommended,
                escalationReason
        );
    }

    private String generateCannedResponse(TicketCategory category, boolean isId) {
        return switch (category) {
            case BUDGET_AND_POCKETS -> isId
                    ? "Terima kasih telah bertanya tentang pengelolaan pos anggaran di Peggy. Anda dapat membuat pos dana darurat, pos kebutuhan bulanan, dan pos tabungan di menu 'Kantong Keuangan'. Sistem akan membantu alokasi otomatis tanpa menyentuh saldo rekening bank Anda."
                    : "Thank you for asking about budget allocations in Peggy. You can configure emergency fund pockets, monthly expense limits, and savings goals in the 'Pockets' tab. The platform tracks your allocations as a money management guide.";
            case EXPENSE_TRACKING_ISSUE -> isId
                    ? "Mohon maaf atas kendala pencatatan transaksi Anda. Pastikan gambar struk belanja terlihat jelas dan memiliki pencahayaan cukup. Anda juga dapat mengoreksi data sebelum konfirmasi akhir disimpan di buku kas."
                    : "We apologize for the expense logging difficulty. Please ensure receipt scans are clear and well-lit. You can review and manually adjust all line items before confirming them to your ledger.";
            case BUG_REPORT -> isId
                    ? "Terima kasih atas laporan kendala teknis Anda. Tim teknis kami telah menerima diagnostik ini untuk penyelidikan lebih lanjut."
                    : "Thank you for reporting this issue. Our engineering team has logged the diagnostics and is investigating.";
            case FEATURE_REQUEST -> isId
                    ? "Ide fitur yang sangat menarik! Masukan Anda telah kami catat dalam backlog pengembangan fitur aplikasi."
                    : "Thank you for your valuable feedback! We have recorded your feature suggestion in our product backlog.";
            case ACCOUNT_AND_SECURITY -> isId
                    ? "Keamanan data finansial Anda adalah prioritas kami. Untuk melindungi privasi Anda, silakan ikuti panduan verifikasi akun aman melalui pengaturan aplikasi."
                    : "Security of your personal finance data is our highest priority. Please verify your identity securely via application settings.";
            default -> isId
                    ? "Terima kasih telah menghubungi pusat bantuan. Tim dukungan kami siap membantu pengelolaan keuangan Anda."
                    : "Thank you for reaching out to support. Our team is here to assist with your money management experience.";
        };
    }
}

