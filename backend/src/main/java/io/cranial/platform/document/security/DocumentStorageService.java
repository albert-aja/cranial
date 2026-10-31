package io.cranial.platform.document.security;

import io.cranial.platform.document.config.DocumentProperties;
import io.cranial.platform.platform.crypto.CryptoService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
public class DocumentStorageService {

    private final DocumentProperties properties;
    private final CryptoService cryptoService;

    public DocumentStorageService(DocumentProperties properties, CryptoService cryptoService) {
        this.properties = properties;
        this.cryptoService = cryptoService;
    }

    public String storeEncrypted(byte[] rawBytes) {
        try {
            Path dir = Path.of(properties.effectiveStorageDir());
            Files.createDirectories(dir);
            String storageKey = UUID.randomUUID() + ".enc";
            Path target = dir.resolve(storageKey).normalize();
            if (!target.startsWith(dir.normalize())) {
                throw new IllegalStateException("Storage path traversal blocked");
            }
            byte[] encrypted = cryptoService.encryptBytes(rawBytes);
            Files.write(target, encrypted, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return storageKey;
        } catch (IOException ex) {
            throw new CranialException(ErrorCode.INTERNAL_ERROR, "Failed to persist encrypted document blob");
        }
    }

    public byte[] loadDecrypted(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("/") || storageKey.contains("\\") || storageKey.contains("..")) {
            throw new CranialException(ErrorCode.DOCUMENT_EXPIRED, "Document binary content is no longer retained");
        }
        try {
            Path dir = Path.of(properties.effectiveStorageDir()).normalize();
            Path target = dir.resolve(storageKey).normalize();
            if (!target.startsWith(dir) || !Files.exists(target)) {
                throw new CranialException(ErrorCode.DOCUMENT_EXPIRED, "Document binary blob expired or purged");
            }
            byte[] encrypted = Files.readAllBytes(target);
            return cryptoService.decryptBytes(encrypted);
        } catch (CranialException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new CranialException(ErrorCode.INTERNAL_ERROR, "Failed to read encrypted document blob");
        }
    }

    public boolean deleteBlobIfExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("/") || storageKey.contains("\\") || storageKey.contains("..")) {
            return false;
        }
        try {
            Path dir = Path.of(properties.effectiveStorageDir()).normalize();
            Path target = dir.resolve(storageKey).normalize();
            if (target.startsWith(dir)) {
                return Files.deleteIfExists(target);
            }
            return false;
        } catch (IOException ex) {
            return false;
        }
    }
}

