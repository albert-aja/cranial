# Official SDK Quickstart Guide (Java & TypeScript)

Official quickstart guide for integrating client applications with **CRANIAL** using the Java and TypeScript SDKs.

## Sandbox Credentials Cheat Sheet (Non-Production)

| Application | Code | Sandbox API Key | Context Signing Secret | Default Tenant |
|---|---|---|---|---|
| **SMS** (Store Management) | `sms` | `crn_dev_sms_app001.secret_sandbox_sms_key_9901` | `secret_signing_key_sandbox_sms_01` | `store-jakarta-01` |
| **Worker App** | `worker_app` | `crn_dev_wrk_app001.secret_sandbox_wrk_key_9902` | `secret_signing_key_sandbox_wrk_02` | `store-jakarta-01` |
| **Peggy** (Money Management) | `peggy` | `crn_dev_pgy_app001.secret_sandbox_pgy_key_9903` | `secret_signing_key_sandbox_pgy_03` | `peggy-family-budget` |
| **E-Commerce** | `ecommerce` | `crn_dev_ecm_app001.secret_sandbox_ecm_key_9904` | `secret_signing_key_sandbox_ecm_04` | `indo-market-online` |

> **Note on Peggy:** CRANIAL enforces that Peggy is strictly a money management and financial education platform. No payment, disbursement, or banking transactions are handled.

---

## 1. Java SDK (`cranial-sdk-java`)

Add to `pom.xml`:
```xml
<dependency>
    <groupId>io.cranial</groupId>
    <artifactId>cranial-sdk-java</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Usage:
```java
import io.cranial.sdk.*;

CranialClient client = CranialClient.create(
    "http://localhost:8080",
    "crn_dev_sms_app001.secret_sandbox_sms_key_9901",
    "secret_signing_key_sandbox_sms_01"
);

// 1. Health check & clock synchronization probe
Map<String, Object> health = client.checkHealth();
System.out.println("CRANIAL Status: " + health.get("status"));

// 2. Build signed context with user identity and roles
SignedContext ctx = SignedContext.builder()
    .tenantRef("store-jakarta-01")
    .userRef("owner@store.test")
    .addRole("STORE_OWNER")
    .addScope("sms:owner_reports")
    .build();

// 3. Test HMAC signing handshake
Map<String, Object> echo = client.echo(ctx, Map.of("ping", "pong"));
System.out.println("Signature Verified: " + echo.get("verified"));

// 4. Execute AI Task
Map<String, Object> taskRes = client.executeTask(
    "sms_business_summary",
    Map.of("store_name", "Jakarta Central", "inquiry", "How are today's sales?"),
    ctx
);
```

---

## 2. TypeScript SDK (`@cranial/sdk`)

Install:
```bash
npm install @cranial/sdk
```

### Usage:
```typescript
import { CranialClient, SignedContextSigner } from '@cranial/sdk';

const client = CranialClient.create({
  baseUrl: 'http://localhost:8080',
  apiKey: 'crn_dev_sms_app001.secret_sandbox_sms_key_9901',
  signingSecret: 'secret_signing_key_sandbox_sms_01',
});

// Build Signed Context
const signer = SignedContextSigner.create({
  tenantRef: 'store-jakarta-01',
  userRef: 'owner@store.test',
  roles: ['STORE_OWNER'],
  scopes: ['sms:owner_reports'],
});

// Execute AI Task
const res = await client.executeTask(
  'sms_business_summary',
  {
    store_name: 'Jakarta Central',
    inquiry: 'How are today sales?',
  },
  signer
);
```

