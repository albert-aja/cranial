package io.cranial.platform.conversation;

import io.cranial.platform.conversation.engine.*;
import io.cranial.platform.conversation.entity.ConversationMessageEntity;
import io.cranial.platform.conversation.model.BlockType;
import io.cranial.platform.conversation.model.SlangNormalizeResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationalIntelligenceTest {

    private final SlangNormalizerEngine slangEngine = new SlangNormalizerEngine();
    private final BankingActionSafetyGuard safetyGuard = new BankingActionSafetyGuard();
    private final ContextWindowManager contextManager = new ContextWindowManager();
    private final ClarificationEngine clarificationEngine = new ClarificationEngine();
    private final ConversationTitleGenerator titleGenerator = new ConversationTitleGenerator();

    @Test
    @DisplayName("SlangNormalizer converts Indonesian currency terms and abbreviations accurately")
    void testSlangNormalization() {
        String input = "Gaji saya 8jt per bulan, makan gocap sehari, kosan 2jt, beli jajan ceban.";
        SlangNormalizeResult result = slangEngine.normalize(input);

        assertThat(result.hasSlangDetected()).isTrue();
        assertThat(result.replacementsCount()).isGreaterThanOrEqualTo(4);
        assertThat(result.normalizedText()).contains("Rp 8.000.000");
        assertThat(result.normalizedText()).contains("Rp 50.000");
        assertThat(result.normalizedText()).contains("Rp 2.000.000");
        assertThat(result.normalizedText()).contains("Rp 10.000");
    }

    @Test
    @DisplayName("SlangNormalizer converts informal colloquialisms like balikin duit and boncos")
    void testSlangColloquialisms() {
        String input = "Bulan ini boncos banget, tolong balikin duit pesanan yang saya batalin";
        SlangNormalizeResult result = slangEngine.normalize(input);

        assertThat(result.hasSlangDetected()).isTrue();
        assertThat(result.normalizedText()).contains("pemborosan/rugi");
        assertThat(result.normalizedText()).contains("pengembalian dana (refund)");
        assertThat(result.normalizedText()).contains("batalkan");
    }

    @Test
    @DisplayName("BankingActionSafetyGuard strictly intercepts money transfer and payment attempts")
    void testBankingSafetyGuardRejectsWireTransfers() {
        String wireAttempt = "Tolong transfer uang Rp 500.000 ke rekening BCA sekarang";
        BankingActionSafetyGuard.SafetyEvaluationResult result = safetyGuard.evaluate(wireAttempt, "id");

        assertThat(result.isViolation()).isTrue();
        assertThat(result.safetyReason()).isEqualTo("NON_BANKING_APPLICATION_POLICY");
        assertThat(result.refusalMessage()).contains("bukan aplikasi perbankan");
        assertThat(result.refusalBlocks()).isNotEmpty();
        assertThat(result.refusalBlocks().stream().anyMatch(b -> b.type() == BlockType.FINANCIAL_DISCLAIMER)).isTrue();
    }

    @Test
    @DisplayName("BankingActionSafetyGuard allows legitimate money management budgeting queries")
    void testBankingSafetyGuardAllowsBudgetQueries() {
        String budgetQuery = "Bagaimana cara membagi gaji Rp 8.000.000 dengan aturan 50/30/20?";
        BankingActionSafetyGuard.SafetyEvaluationResult result = safetyGuard.evaluate(budgetQuery, "id");

        assertThat(result.isViolation()).isFalse();
        assertThat(result.safetyReason()).isNull();
    }

    @Test
    @DisplayName("ContextWindowManager enforces sliding turn window and creates rolling summaries")
    void testContextWindowManager() {
        List<ConversationMessageEntity> messages = new ArrayList<>();
        UUID convId = UUID.randomUUID();

        for (int i = 1; i <= 25; i++) {
            ConversationMessageEntity msg = new ConversationMessageEntity();
            msg.setId(UUID.randomUUID());
            msg.setConversationId(convId);
            msg.setRole(i % 2 == 1 ? "USER" : "ASSISTANT");
            msg.setContent("Turn number " + i + " content details here.");
            msg.setTokenCount(10);
            msg.setCreatedAt(Instant.now().plusSeconds(i));
            messages.add(msg);
        }

        ContextWindowManager.WindowResult window = contextManager.manageWindow(messages, null, 10, 2048);

        assertThat(window.activeMessages()).hasSize(10);
        assertThat(window.updatedSummary()).isNotBlank();
        assertThat(window.updatedSummary()).contains("Turn number");
    }

    @Test
    @DisplayName("ClarificationEngine detects ambiguous spending questions and generates options")
    void testClarificationEngine() {
        String ambiguous = "bagaimana pengeluaran saya";
        ClarificationEngine.ClarificationResult result = clarificationEngine.evaluateClarification(ambiguous, "id");

        assertThat(result.requiresClarification()).isTrue();
        assertThat(result.clarificationBlock()).isNotNull();
        assertThat(result.clarificationBlock().type()).isEqualTo(BlockType.CLARIFICATION);
        assertThat(result.clarificationBlock().options()).hasSize(3);
    }

    @Test
    @DisplayName("ConversationTitleGenerator creates clean 3 to 6 word titles")
    void testTitleGenerator() {
        String msg = "Halo Peggy, tolong bantu hitung alokasi pengeluaran bulanan saya";
        String title = titleGenerator.generateTitle(msg, "id");

        assertThat(title).isEqualTo("Halo Peggy Tolong Bantu Hitung");
    }
}

