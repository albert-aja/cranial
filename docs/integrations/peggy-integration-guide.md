# Peggy Money Management Integration Guide

This guide describes how to integrate **Peggy** with **CRANIAL**.

## 1. Core Principle & Non-Negotiable Boundary

> **IMPORTANT:** Peggy is purely a personal and collaborative **money management and financial tracking** app. It is **NOT** a banking application and does **NOT** handle payments, bank transfers, wire settlements, or funds disbursements.

- **Peggy owns:** Transactions, pockets, balance reconciliation, budgets, category assignments, and financial validation.
- **CRANIAL provides:** Receipt OCR extraction previews, 50-30-20 budget advice, spending pattern trend explanations, and financial literacy RAG search.
- **Strict Boundary:** All OCR and tool outputs returned by CRANIAL are **read-only previews**. Peggy's user reviews and confirms extraction previews in Peggy UI, and Peggy's own backend validates and saves transactions in its own database. CRANIAL cannot create ledger entries or execute money transfers.

## 2. Supported Capabilities for Peggy

1. **Receipt OCR & Structured Extraction (Phase 5):**
   - Upload receipt image via signed transfer token or POST `/api/v1/documents`.
   - Returns structured line items, merchant name, date, subtotal, tax, and currency.
   - User reviews extraction in Peggy; user confirms and Peggy backend saves to Peggy DB.

2. **Educational Financial Advisor (Phase 3 & Phase 10):**
   - Task `peggy_financial_advisor`: Interprets 50-30-20 budget breakdown (Needs, Wants, Savings).
   - Read-only tools `peggy_get_budget_breakdown` and `peggy_get_monthly_spending`.

3. **Financial Encyclopedia & Literacy RAG (Phase 6):**
   - Collection `peggy_financial_encyclopedia_ref` in English and Bahasa Indonesia.
   - Answers financial literacy questions (e.g. *Dana Darurat*, *Anggaran 50-30-20*, *Inflasi*, *Sinking Fund*) with exact citations `[C1]`.

## 3. Signed Context & Code Example

```java
CranialClient client = CranialClient.create(
    "http://localhost:8080",
    "crn_dev_pgy_app001.secret_sandbox_pgy_key_9903",
    "secret_signing_key_sandbox_pgy_03"
);

SignedContext peggyCtx = SignedContext.builder()
    .tenantRef("peggy-family-budget")
    .userRef("user@peggy.test")
    .addRole("BUDGET_USER")
    .addScope("peggy:budget_read")
    .addScope("peggy:spending_read")
    .build();

// Ask Peggy Money Management Advisor
Map<String, Object> advice = client.executeTask(
    "peggy_financial_advisor",
    Map.of(
        "user_question", "Bagaimana status anggaran 50-30-20 saya bulan ini?",
        "budget_context", "Penghasilan bulanan: Rp 15.000.000"
    ),
    peggyCtx
);

System.out.println("Saran Keuangan: " + advice.get("data"));
```

