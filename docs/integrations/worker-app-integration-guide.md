# Worker App Integration Guide

This guide describes how to integrate the **Worker App** with **CRANIAL**, emphasizing strict tenant and role boundaries.

## 1. Overview & Security Boundaries

- **Role:** Restricted mobile application for warehouse workers and delivery drivers.
- **Allowed Access:** Assigned logistics tasks, delivery manifest details, warehouse standard operating procedures (SOPs), and delivery note OCR.
- **Strict Boundary (Owner vs Worker):** Workers are strictly blocked from accessing store sales, financial profit margins, payroll, or business-owner analytics. Any attempt to invoke owner tools (`sms:owner_reports`) or retrieve owner-restricted policies is rejected with `403 FORBIDDEN` / `TOOL_PERMISSION_DENIED`.

## 2. Worker Identity & Signed Context

```json
{
  "tenantRef": "store-jakarta-01",
  "userRef": "worker1@store.test",
  "roles": ["WORKER"],
  "scopes": ["worker:tasks_read", "worker:delivery_read", "app:read"]
}
```

## 3. Worker Task Assistant Flow

```text
Worker -> POST /api/v1/orchestrator/execute
  Headers:
    X-Cranial-Api-Key: crn_dev_wrk_app001.secret_sandbox_wrk_key_9902
    X-Cranial-Context: [Signed worker claims]
  Body:
    task_code: "worker_task_assistance"
    inputs:
      worker_id: "worker1@store.test"
      task_query: "What is my next priority delivery?"

CRANIAL Orchestrator:
  - Verifies WORKER role
  - Invokes authorized tool: worker_get_assigned_tasks
  - Synthesizes clear navigation and task priority
  - Returns task instructions with zero financial margin exposure
```

## 4. TypeScript SDK Example

```typescript
import { CranialClient, SignedContextSigner } from '@cranial/sdk';

const client = CranialClient.create({
  baseUrl: 'http://localhost:8080',
  apiKey: 'crn_dev_wrk_app001.secret_sandbox_wrk_key_9902',
  signingSecret: 'secret_signing_key_sandbox_wrk_02',
});

const workerContext = SignedContextSigner.create({
  tenantRef: 'store-jakarta-01',
  userRef: 'worker1@store.test',
  roles: ['WORKER'],
  scopes: ['worker:tasks_read', 'worker:delivery_read'],
});

const taskGuidance = await client.executeTask(
  'worker_task_assistance',
  {
    worker_id: 'worker1@store.test',
    task_query: 'Show my assigned deliveries and packaging instructions.',
  },
  workerContext
);

console.log('Worker Guidance:', taskGuidance);
```

