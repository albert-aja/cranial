# CRANIAL TypeScript Client SDK

Official TypeScript & JavaScript client SDK for **CRANIAL — Universal AI Integration Platform**.

## Installation

```bash
npm install @cranial/sdk
```

## Quick Start

```typescript
import { CranialClient, SignedContextSigner } from '@cranial/sdk';

// 1. Initialize Client
const client = CranialClient.create({
  baseUrl: 'http://localhost:8080',
  apiKey: 'crn_dev_sms_app001.secret_sandbox_sms_key_9901',
  signingSecret: 'secret_signing_key_sandbox_sms_01',
});

// 2. Health check
const health = await client.checkHealth();
console.log('Platform status:', health.status, 'Time:', health.server_time_utc);

// 3. Create Signed Context
const signer = SignedContextSigner.create({
  tenantRef: 'store-jakarta-01',
  userRef: 'owner@store.test',
  roles: ['STORE_OWNER'],
  scopes: ['sms:owner_reports', 'app:read'],
});

// 4. Execute AI Task
const result = await client.executeTask(
  'sms_business_summary',
  {
    store_name: 'Jakarta Flagship Store',
    inquiry: 'How are today sales and critical low-stock items?',
  },
  signer
);

console.log('Orchestration Result:', result.data);
```

## Peggy Money Management Integration (Strict Non-Banking Rule)

CRANIAL is strictly an integration layer for money management and does **not** execute banking, funds transfers, or payment disbursements.

```typescript
const peggySigner = SignedContextSigner.create({
  tenantRef: 'peggy-family-budget',
  userRef: 'user@peggy.test',
  roles: ['BUDGET_USER'],
  scopes: ['peggy:budget_read', 'peggy:spending_read'],
});

// Retrieve read-only 50-30-20 budget advice
const budgetAdvice = await client.executeTask(
  'peggy_financial_advisor',
  {
    user_question: 'Evaluate my 50-30-20 budget breakdown this month.',
    budget_context: 'Income: IDR 15.000.000',
  },
  peggySigner
);
```

