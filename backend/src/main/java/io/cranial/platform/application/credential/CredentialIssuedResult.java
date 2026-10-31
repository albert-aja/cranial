package io.cranial.platform.application.credential;

/**
 * Reveal-once credential creation/rotation payload.
 * Raw secrets are returned ONLY at issue/rotation time and are never persisted in plaintext.
 */
public record CredentialIssuedResult(
        CredentialView credential,
        String rawApiKey,
        String rawSigningSecret,
        String warning
) {
}

