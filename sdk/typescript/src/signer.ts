import type { SignedContextClaims, SignedHeaders } from './types.js';

export class SignedContextSigner {
  private claims: SignedContextClaims;

  constructor(claims: SignedContextClaims) {
    this.claims = {
      tenantRef: claims.tenantRef,
      userRef: claims.userRef,
      roles: claims.roles || [],
      scopes: claims.scopes || [],
    };
  }

  public static create(claims: SignedContextClaims): SignedContextSigner {
    return new SignedContextSigner(claims);
  }

  public async generateHeaders(
    method: string,
    path: string,
    rawBody: Uint8Array | string | undefined,
    signingSecret: string
  ): Promise<SignedHeaders> {
    if (!signingSecret) {
      throw new Error('signingSecret is required to generate signed context headers');
    }

    const payloadJson = JSON.stringify({
      tenantRef: this.claims.tenantRef,
      userRef: this.claims.userRef,
      roles: this.claims.roles,
      scopes: this.claims.scopes,
    });

    const contextHeader = base64UrlEncode(new TextEncoder().encode(payloadJson));
    const timestampHeader = Math.floor(Date.now() / 1000).toString();

    let bodyBytes: Uint8Array;
    if (!rawBody) {
      bodyBytes = new Uint8Array(0);
    } else if (typeof rawBody === 'string') {
      bodyBytes = new TextEncoder().encode(rawBody);
    } else {
      bodyBytes = rawBody;
    }

    const bodySha256 = await sha256Hex(bodyBytes);
    const canonicalString = `${timestampHeader}.${method.toUpperCase()}.${path}.${contextHeader}.${bodySha256}`;

    const signatureHeader = await hmacSha256Hex(signingSecret, canonicalString);

    return {
      contextHeader,
      timestampHeader,
      signatureHeader,
    };
  }
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  const base64 = btoa(binary);
  return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

async function sha256Hex(data: Uint8Array): Promise<string> {
  const hashBuffer = await crypto.subtle.digest('SHA-256', data);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function hmacSha256Hex(secret: string, message: string): Promise<string> {
  const enc = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw',
    enc.encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign']
  );
  const signature = await crypto.subtle.sign('HMAC', key, enc.encode(message));
  const sigArray = Array.from(new Uint8Array(signature));
  return sigArray.map((b) => b.toString(16).padStart(2, '0')).join('');
}

