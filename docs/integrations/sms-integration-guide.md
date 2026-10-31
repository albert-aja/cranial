# SMS (Store Management System) Integration Guide

This guide describes how to integrate the **Store Management System (SMS)** with the **CRANIAL Universal AI Integration Platform**.

## 1. Overview & Architectural Boundaries

- **Role:** SMS is a store operations, sales, inventory, and backoffice management platform.
- **Ownership:** SMS owns products, sales transactions, stock balances, staff accounts, and business validation.
- **CRANIAL's Role:** CRANIAL provides natural-language business analysis, sales reporting summaries, low-stock insights, and delivery note OCR.
- **Safety Invariant:** CRANIAL is read-only for reporting and suggestions. Deterministic numbers are calculated by SMS reporting APIs; CRANIAL explains trends and formats structured output.

## 2. Authentication & Signed Context

All requests from SMS must include:
- `X-Cranial-Api-Key`: Issued by CRANIAL Admin Console (or `crn_dev_sms_app001.secret_sandbox_sms_key_9901` in sandbox).
- `X-Cranial-Context`: Base64URL JSON containing:
  ```json
  {
    "tenantRef": "store-jakarta-01",
    "userRef": "owner@store.test",
    "roles": ["STORE_OWNER"],
    "scopes": ["sms:owner_reports", "sms:inventory_read", "app:read"]
  }
  ```
- `X-Cranial-Timestamp`: Epoch seconds (valid within ±300s).
- `X-Cranial-Signature`: HMAC-SHA256 signature calculated using the application signing secret.

## 3. End-to-End Flow: Store Owner Business Assistant

```text
+-------------------+             +-----------------------+             +----------------------+
|  SMS Web / App    |             |        CRANIAL        |             |  SMS Reporting Tool  |
+---------+---------+             +-----------+-----------+             +----------+-----------+
          |                                   |                                    |
          | 1. POST /api/v1/orchestrator/exec |                                    |
          |    task: sms_business_summary     |                                    |
          +---------------------------------->|                                    |
          |                                   | 2. Checks role & entitlements      |
          |                                   | 3. Resolves prompt template        |
          |                                   | 4. Invokes tool: sms_get_daily_sales|
          |                                   +----------------------------------->|
          |                                   |<-----------------------------------+
          |                                   | 5. Synthesizes business insights   |
          | 6. Returns structured report      |                                    |
          |<----------------------------------+                                    |
          |                                   |                                    |
```

## 4. Java SDK Example

```java
CranialClient client = CranialClient.create(
    "http://localhost:8080",
    "crn_dev_sms_app001.secret_sandbox_sms_key_9901",
    "secret_signing_key_sandbox_sms_01"
);

SignedContext ctx = SignedContext.builder()
    .tenantRef("store-jakarta-01")
    .userRef("owner@store.test")
    .addRole("STORE_OWNER")
    .addScope("sms:owner_reports")
    .build();

Map<String, Object> result = client.executeTask(
    "sms_business_summary",
    Map.of(
        "store_name", "Jakarta Flagship Store",
        "inquiry", "Summarize today's sales and highlight any items below reorder threshold."
    ),
    ctx
);

System.out.println("Insights: " + result.get("data"));
```

