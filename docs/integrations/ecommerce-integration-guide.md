# E-Commerce Platform Integration Guide

This guide describes how to integrate an **E-Commerce Platform** with **CRANIAL**.

## 1. Overview & Architectural Boundaries

- **Role:** Online retail storefront for product discovery, checkout, and order tracking.
- **Ownership:** E-Commerce owns the product catalog, real-time inventory, pricing, customer orders, payment gateway integrations, and fulfillment states.
- **CRANIAL provides:** Customer Q&A, product discovery assistant, policy FAQ retrieval, and order status lookups using controlled read-only application tools.
- **Safety Invariant:** CRANIAL never makes promises about unauthorized refunds, discounts, or stock reserves without verifying through e-commerce tools.

## 2. Customer Support Assistant Flow

```text
Customer -> POST /api/v1/orchestrator/execute
  Headers:
    X-Cranial-Api-Key: crn_dev_ecm_app001.secret_sandbox_ecm_key_9904
    X-Cranial-Context: [Customer Signed Context]
  Body:
    task_code: "ecom_customer_support"
    inputs:
      question: "What is the status of my order ORD-2026-9901?"
      order_context: "Customer email: buyer@shop.test"

CRANIAL Orchestrator:
  - Invokes authorized tool: ecom_get_order_status
  - Formats polite response with tracking number and estimated delivery date
```

## 3. TypeScript SDK Example

```typescript
import { CranialClient, SignedContextSigner } from '@cranial/sdk';

const client = CranialClient.create({
  baseUrl: 'http://localhost:8080',
  apiKey: 'crn_dev_ecm_app001.secret_sandbox_ecm_key_9904',
  signingSecret: 'secret_signing_key_sandbox_ecm_04',
});

const customerCtx = SignedContextSigner.create({
  tenantRef: 'indo-market-online',
  userRef: 'buyer@shop.test',
  roles: ['CUSTOMER'],
  scopes: ['ecom:order_read', 'ecom:catalog_read'],
});

const supportResponse = await client.executeTask(
  'ecom_customer_support',
  {
    question: 'Where is my order ORD-2026-9901?',
    order_context: 'Order Reference: ORD-2026-9901',
  },
  customerCtx
);

console.log('Customer Support Answer:', supportResponse);
```

