package io.cranial.platform.customer;

import io.cranial.platform.customer.engine.CustomerPrivacyGuard;
import io.cranial.platform.customer.engine.CustomerSlangNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerSlangNormalizerTest {

    private final CustomerSlangNormalizer normalizer = new CustomerSlangNormalizer();
    private final CustomerPrivacyGuard privacyGuard = new CustomerPrivacyGuard();

    @Test
    @DisplayName("Normalizes core Indonesian customer support slang: batalin, balikin duit, lemot bgt, gak bisa login")
    void normalizesCoreSupportColloquialisms() {
        String raw = "Min tolong batalin transaksi, balikin duit gw krn aplikasinya lemot bgt dan gw gak bisa login!";
        var out = normalizer.normalize(raw, "id");

        assertThat(out.slangDetected()).isTrue();
        assertThat(out.bankingBoundaryHintDetected()).isTrue();
        assertThat(out.normalizedText())
                .contains("batalkan")
                .contains("kembalikan uang")
                .contains("lambat sekali")
                .contains("tidak bisa login")
                .contains("saya")
                .contains("karena");
    }

    @Test
    @DisplayName("Collapses expressive character elongations before phrase normalization")
    void collapsesExpressiveElongations() {
        String raw = "Aplikasinya lemooottt bgtttt parahhh gaak bisaaa login!";
        var out = normalizer.normalize(raw, "id");

        assertThat(out.elongationCollapsedCount()).isGreaterThanOrEqualTo(3);
        assertThat(out.normalizedText())
                .contains("lambat sekali")
                .contains("parah")
                .contains("tidak bisa login");
    }

    @Test
    @DisplayName("Preserves PII redaction masks untouched during slang normalization")
    void preservesPiiMasks() {
        String raw = "Min balikin duit gw ke rek BCA! Email rudi_kecewaaa@example.com kartu 4111222233334444 hp 081234567890 [NIK: 317101******0001]";
        var redacted = privacyGuard.sanitize(raw);
        var out = normalizer.normalize(redacted.redactedText(), "id");

        assertThat(out.normalizedText())
                .contains("kembalikan uang")
                .contains("rekening BCA")
                .contains("[EMAIL:")
                .contains("[CARD:")
                .contains("[PHONE:")
                .contains("[NIK: 317101******0001]");
    }

    @Test
    @DisplayName("Leaves pure English support messages untouched when locale is en")
    void leavesEnglishMessagesUntouched() {
        String en = "I cannot sign in to my budget dashboard, please pass this to tech support.";
        var out = normalizer.normalize(en, "en");

        assertThat(out.slangDetected()).isFalse();
        assertThat(out.normalizedText()).isEqualTo(en);
    }
}
