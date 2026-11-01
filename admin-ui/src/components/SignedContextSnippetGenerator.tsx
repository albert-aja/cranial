import React, { useState } from 'react';
import type { ApplicationView, CredentialView } from '../api/types';

export interface SignedContextSnippetGeneratorProps {
  app: ApplicationView;
  credentials: CredentialView[];
}

export function SignedContextSnippetGenerator({ app, credentials }: SignedContextSnippetGeneratorProps) {
  const [selectedLanguage, setSelectedLanguage] = useState<'curl' | 'java' | 'typescript'>('curl');
  const [selectedCredId, setSelectedCredId] = useState<string>(credentials[0]?.id || '');
  const [capability, setCapability] = useState('conversational_intelligence');
  const [tenantId, setTenantId] = useState('tenant-001');
  const [copied, setCopied] = useState(false);

  const activeCred = credentials.find((c) => c.id === selectedCredId) || credentials[0];
  const keyPrefix = activeCred ? activeCred.key_prefix : `crn_${app.environment.toLowerCase()}_demo`;
  const endpointUrl = 'http://localhost:8080/api/v1/orchestration/chat';

  const generateCurlSnippet = () => {
    return `# 1. Prepare timestamp and signature payload
TIMESTAMP=$(date +%s)
SECRET="<YOUR_HMAC_SIGNING_SECRET>"
PAYLOAD='{"capability":"${capability}","message":"Explain spending trends","tenant_id":"${tenantId}"}'

# 2. Compute HMAC-SHA256 signature
SIGNATURE=$(echo -n "\${TIMESTAMP}.\${PAYLOAD}" | openssl dgst -sha256 -hmac "\${SECRET}" -binary | base64)

# 3. Execute signed gateway request to CRANIAL
curl -X POST "${endpointUrl}" \\
  -H "Content-Type: application/json" \\
  -H "X-CRANIAL-APP-CODE: ${app.code}" \\
  -H "X-CRANIAL-KEY-PREFIX: ${keyPrefix}" \\
  -H "X-CRANIAL-TIMESTAMP: \${TIMESTAMP}" \\
  -H "X-CRANIAL-SIGNATURE: \${SIGNATURE}" \\
  -H "X-CRANIAL-TENANT-ID: ${tenantId}" \\
  -d "\${PAYLOAD}"`;
  };

  const generateJavaSnippet = () => {
    return `import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class CranialIntegrationClient {
    private static final String APP_CODE = "${app.code}";
    private static final String KEY_PREFIX = "${keyPrefix}";
    private static final String SIGNING_SECRET = "<YOUR_HMAC_SIGNING_SECRET>";
    private static final String ENDPOINT = "${endpointUrl}";

    public static void main(String[] args) throws Exception {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String payload = """
            {
              "capability": "${capability}",
              "message": "Explain spending trends",
              "tenant_id": "${tenantId}"
            }
            """;

        // Compute HMAC-SHA256
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sigBytes = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(sigBytes);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(ENDPOINT))
            .header("Content-Type", "application/json")
            .header("X-CRANIAL-APP-CODE", APP_CODE)
            .header("X-CRANIAL-KEY-PREFIX", KEY_PREFIX)
            .header("X-CRANIAL-TIMESTAMP", timestamp)
            .header("X-CRANIAL-SIGNATURE", signature)
            .header("X-CRANIAL-TENANT-ID", "${tenantId}")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("CRANIAL Response [" + response.statusCode() + "]: " + response.body());
    }
}`;
  };

  const generateTypeScriptSnippet = () => {
    return `import crypto from 'node:crypto';

async function sendCranialSignedRequest() {
  const appCode = '${app.code}';
  const keyPrefix = '${keyPrefix}';
  const signingSecret = process.env.CRANIAL_SIGNING_SECRET || '<YOUR_HMAC_SIGNING_SECRET>';
  const endpoint = '${endpointUrl}';

  const timestamp = Math.floor(Date.now() / 1000).toString();
  const payload = JSON.stringify({
    capability: '${capability}',
    message: 'Explain spending trends',
    tenant_id: '${tenantId}',
  });

  // Calculate HMAC-SHA256 hex/base64 signature
  const signature = crypto
    .createHmac('sha256', signingSecret)
    .update(\`\${timestamp}.\${payload}\`)
    .digest('base64');

  const response = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-CRANIAL-APP-CODE': appCode,
      'X-CRANIAL-KEY-PREFIX': keyPrefix,
      'X-CRANIAL-TIMESTAMP': timestamp,
      'X-CRANIAL-SIGNATURE': signature,
      'X-CRANIAL-TENANT-ID': '${tenantId}',
    },
    body: payload,
  });

  const result = await response.json();
  console.log('CRANIAL Response:', result);
}

sendCranialSignedRequest().catch(console.error);`;
  };

  const getActiveCode = () => {
    switch (selectedLanguage) {
      case 'java':
        return generateJavaSnippet();
      case 'typescript':
        return generateTypeScriptSnippet();
      case 'curl':
      default:
        return generateCurlSnippet();
    }
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(getActiveCode());
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback
    }
  };

  return (
    <div className="rounded-xl border border-slate-800 bg-slate-900/70 p-4 space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h4 className="text-sm font-semibold text-white flex items-center gap-2">
            <span className="p-1 rounded bg-indigo-500/10 text-indigo-400">⚡</span>
            Signed-Context Test Snippet Generator
          </h4>
          <p className="text-xs text-slate-400 mt-0.5">
            1-click code templates for client apps using HMAC-SHA256 request authentication.
          </p>
        </div>

        {/* Language Tabs */}
        <div className="inline-flex rounded-lg bg-slate-950 p-1 border border-slate-800 text-xs font-medium">
          <button
            type="button"
            onClick={() => setSelectedLanguage('curl')}
            className={`rounded-md px-3 py-1 transition ${
              selectedLanguage === 'curl' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'
            }`}
          >
            cURL
          </button>
          <button
            type="button"
            onClick={() => setSelectedLanguage('java')}
            className={`rounded-md px-3 py-1 transition ${
              selectedLanguage === 'java' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'
            }`}
          >
            Java 21
          </button>
          <button
            type="button"
            onClick={() => setSelectedLanguage('typescript')}
            className={`rounded-md px-3 py-1 transition ${
              selectedLanguage === 'typescript' ? 'bg-indigo-600 text-white' : 'text-slate-400 hover:text-white'
            }`}
          >
            TypeScript
          </button>
        </div>
      </div>

      {/* Snippet Parameters */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
        <div>
          <label className="block text-slate-400 mb-1">Credential Prefix</label>
          <select
            value={selectedCredId}
            onChange={(e) => setSelectedCredId(e.target.value)}
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-white"
          >
            {credentials.length === 0 ? (
              <option value="">(No active credentials issued)</option>
            ) : (
              credentials.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name} ({c.key_prefix}…)
                </option>
              ))
            )}
          </select>
        </div>

        <div>
          <label className="block text-slate-400 mb-1">Target Capability</label>
          <select
            value={capability}
            onChange={(e) => setCapability(e.target.value)}
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-white"
          >
            <option value="conversational_intelligence">Conversational Intelligence</option>
            <option value="document_intelligence">Document Intelligence (OCR)</option>
            <option value="data_intelligence">Data Intelligence</option>
            <option value="decision_intelligence">Decision Intelligence</option>
            <option value="knowledge_intelligence">Knowledge Intelligence (RAG)</option>
          </select>
        </div>

        <div>
          <label className="block text-slate-400 mb-1">Tenant ID Scope</label>
          <input
            type="text"
            value={tenantId}
            onChange={(e) => setTenantId(e.target.value)}
            placeholder="tenant-001"
            className="w-full rounded-lg border border-slate-700 bg-slate-950 px-2.5 py-1.5 text-white font-mono"
          />
        </div>
      </div>

      {/* Code Display Area */}
      <div className="relative rounded-lg border border-slate-800 bg-slate-950 p-3 font-mono text-xs">
        <button
          type="button"
          onClick={handleCopy}
          className="absolute top-2.5 right-2.5 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-300 px-2.5 py-1 text-[11px] font-sans flex items-center gap-1.5 border border-slate-700 transition"
        >
          {copied ? (
            <span className="text-emerald-400 font-semibold">✓ Copied!</span>
          ) : (
            <span>Copy Snippet</span>
          )}
        </button>
        <pre className="text-slate-300 overflow-x-auto text-[11px] leading-relaxed pt-6">
          {getActiveCode()}
        </pre>
      </div>
    </div>
  );
}

