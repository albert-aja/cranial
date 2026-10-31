package io.cranial.platform.customer.engine;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class CustomerPromiseGuard {

    public static final String NON_BANKING_DISCLAIMER_EN =
            "CRANIAL and client applications (such as Peggy) are strictly money management platforms for budgeting, expense tracking, and financial intelligence. The platform does NOT process bank transfers, execute payments, or handle monetary settlements.";

    public static final String NON_BANKING_DISCLAIMER_ID =
            "CRANIAL dan aplikasi mitra (seperti Peggy) murni merupakan aplikasi pengelola keuangan untuk pencatatan anggaran, pengeluaran, dan alokasi pos keuangan. Sistem TIDAK DAPAT memproses transfer dana perbankan, eksekusi pembayaran, maupun pengembalian saldo langsung.";

    private static final List<String> FORBIDDEN_PROMISE_KEYWORDS = List.of(
            "promise to refund",
            "promise refund",
            "guarantee refund",
            "guarantee money back",
            "execute wire transfer",
            "wire transfer to",
            "transfer cash to",
            "transfer the funds",
            "guarantee 100% refund",
            "promise payment",
            "janji kembalikan uang",
            "janji uang saya kembali",
            "garansi uang kembali",
            "transfer saldo ke rekening",
            "transfer dana ke",
            "kirim saldo ke rekening",
            "tarik tunai otomatis",
            "proses refund rekening"
    );

    public record PromiseGuardEvaluation(
            boolean flagged,
            List<String> detectedTriggers,
            String boundaryDisclaimer
    ) {}

    public PromiseGuardEvaluation evaluate(String text, String locale) {
        if (text == null || text.isBlank()) {
            return new PromiseGuardEvaluation(false, List.of(), getDisclaimer(locale));
        }

        String lower = text.toLowerCase(Locale.ROOT);
        List<String> triggers = new ArrayList<>();

        for (String keyword : FORBIDDEN_PROMISE_KEYWORDS) {
            if (lower.contains(keyword)) {
                triggers.add(keyword);
            }
        }

        boolean flagged = !triggers.isEmpty();
        return new PromiseGuardEvaluation(flagged, triggers, getDisclaimer(locale));
    }

    public String getDisclaimer(String locale) {
        if (locale != null && locale.equalsIgnoreCase("id")) {
            return NON_BANKING_DISCLAIMER_ID;
        }
        return NON_BANKING_DISCLAIMER_EN;
    }
}

