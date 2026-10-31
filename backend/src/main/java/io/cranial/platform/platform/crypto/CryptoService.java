package io.cranial.platform.platform.crypto;

import io.cranial.platform.platform.config.CranialProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CryptoService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final byte[] apiKeyPepperBytes;
    private final SecretKeySpec aesKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(CranialProperties properties) {
        String pepper = properties.security().apiKeyPepper();
        String encKey = properties.security().encryptionKey();
        this.apiKeyPepperBytes = pepper.getBytes(StandardCharsets.UTF_8);
        this.aesKeySpec = new SecretKeySpec(sha256Bytes(encKey.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public String generateRandomToken(int numBytes) {
        byte[] bytes = new byte[numBytes];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String generateAlphaNumericPrefix(int length) {
        final String alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    public String hashApiKeySecret(String rawSecret) {
        return hmacSha256Hex(apiKeyPepperBytes, rawSecret);
    }

    public boolean verifyApiKeySecret(String rawSecret, String expectedHash) {
        String candidate = hashApiKeySecret(rawSecret);
        return constantTimeEquals(candidate, expectedHash);
    }

    public String hmacSha256Hex(String secret, String message) {
        return hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), message);
    }

    public String hmacSha256Hex(byte[] secretKeyBytes, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_SHA256));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", ex);
        }
    }

    public boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8)
        );
    }

    public String sha256Hex(byte[] input) {
        return HexFormat.of().formatHex(sha256Bytes(input));
    }

    public String sha256Hex(String input) {
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    public String encryptSecret(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt secret", ex);
        }
    }

    public String decryptSecret(String encodedCiphertext) {
        if (encodedCiphertext == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encodedCiphertext);
            if (combined.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt secret", ex);
        }
    }

    public byte[] encryptBytes(byte[] plaintextBytes) {
        if (plaintextBytes == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintextBytes);
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return combined;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt binary payload", ex);
        }
    }

    public byte[] decryptBytes(byte[] combined) {
        if (combined == null) {
            return null;
        }
        try {
            if (combined.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted binary payload");
            }
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[combined.length - GCM_IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(combined, GCM_IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, aesKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt binary payload", ex);
        }
    }

    public String signServerToken(String canonicalPayload) {
        return hmacSha256Hex(apiKeyPepperBytes, canonicalPayload);
    }

    public boolean verifyServerToken(String canonicalPayload, String signatureHex) {
        String expected = signServerToken(canonicalPayload);
        return constantTimeEquals(expected, signatureHex);
    }

    private static byte[] sha256Bytes(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

