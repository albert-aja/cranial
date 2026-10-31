package io.cranial.platform.document.security;

import io.cranial.platform.document.config.DocumentProperties;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

@Component
public class DocumentSecurityValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "bmp", "txt", "csv", "md", "html"
    );

    private static final Set<String> ALLOWED_MIMES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/bmp",
            "image/x-ms-bmp",
            "text/plain",
            "text/csv",
            "text/markdown",
            "text/x-markdown",
            "text/html"
    );

    private final Tika tika = new Tika();
    private final DocumentProperties properties;

    public DocumentSecurityValidator(DocumentProperties properties) {
        this.properties = properties;
    }

    public record ValidatedDocumentInput(
            String sanitizedFileName,
            String claimedMimeType,
            String detectedMimeType,
            String extension
    ) {
    }

    public ValidatedDocumentInput validate(String rawFileName, String claimedMimeType, byte[] bytes, Long appMaxBytes) {
        if (rawFileName == null || rawFileName.isBlank()) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Document file_name is required");
        }
        if (bytes == null || bytes.length == 0) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Document content cannot be empty (0 bytes)");
        }

        long maxAllowed = properties.effectiveMaxDocumentBytes();
        if (appMaxBytes != null && appMaxBytes > 0) {
            maxAllowed = Math.min(maxAllowed, appMaxBytes);
        }
        if (bytes.length > maxAllowed) {
            throw new CranialException(
                    ErrorCode.PAYLOAD_TOO_LARGE,
                    "Document size (" + bytes.length + " bytes) exceeds maximum allowed limit (" + maxAllowed + " bytes)"
            );
        }

        String sanitizedName = sanitizeFileName(rawFileName);
        String ext = extractExtension(sanitizedName);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new CranialException(
                    ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file extension '." + ext + "'. Allowed: " + ALLOWED_EXTENSIONS
            );
        }

        rejectExecutableSignatures(bytes);

        String magicMime = tika.detect(bytes);
        String detectedMime = ("application/octet-stream".equalsIgnoreCase(magicMime) || "text/plain".equalsIgnoreCase(magicMime))
                ? tika.detect(bytes, sanitizedName)
                : magicMime;

        if (!ALLOWED_MIMES.contains(detectedMime.toLowerCase(Locale.ROOT))) {
            throw new CranialException(
                    ErrorCode.FILE_SIGNATURE_MISMATCH,
                    "Detected file content signature '" + detectedMime + "' is prohibited or unsupported"
            );
        }

        verifyExtensionMatchesSignature(ext, bytes, magicMime);

        String effectiveClaimed = (claimedMimeType != null && !claimedMimeType.isBlank())
                ? claimedMimeType.trim().toLowerCase(Locale.ROOT)
                : detectedMime.toLowerCase(Locale.ROOT);

        return new ValidatedDocumentInput(sanitizedName, effectiveClaimed, detectedMime.toLowerCase(Locale.ROOT), ext);
    }

    public static String sanitizeFileName(String raw) {
        String normalized = raw.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        base = base.replaceAll("[^a-zA-Z0-9._\\- ()]", "_").trim();
        if (base.isEmpty() || base.startsWith(".")) {
            base = "document" + base;
        }
        return base.length() > 200 ? base.substring(base.length() - 200) : base;
    }

    private static String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void rejectExecutableSignatures(byte[] b) {
        // Windows PE / MZ header
        if (b.length >= 2 && b[0] == 0x4D && b[1] == 0x5A) {
            throw new CranialException(ErrorCode.FILE_SIGNATURE_MISMATCH, "Executable binary (PE/MZ) disguised as document rejected");
        }
        // Linux ELF header
        if (b.length >= 4 && b[0] == 0x7F && b[1] == 'E' && b[2] == 'L' && b[3] == 'F') {
            throw new CranialException(ErrorCode.FILE_SIGNATURE_MISMATCH, "Executable binary (ELF) disguised as document rejected");
        }
        // ZIP / JAR / Office macro archive when not explicitly permitted
        if (b.length >= 4 && b[0] == 0x50 && b[1] == 0x4B && b[2] == 0x03 && b[3] == 0x04) {
            throw new CranialException(ErrorCode.FILE_SIGNATURE_MISMATCH, "Archive/JAR signature disguised as document rejected");
        }
    }

    private static void verifyExtensionMatchesSignature(String ext, byte[] b, String magicMime) {
        switch (ext) {
            case "pdf" -> {
                String head = new String(b, 0, Math.min(b.length, 8), StandardCharsets.US_ASCII);
                if (!head.startsWith("%PDF-")) {
                    throw new CranialException(ErrorCode.FILE_SIGNATURE_MISMATCH, "File claims .pdf extension but lacks %PDF- magic header");
                }
            }
            case "png" -> {
                if (b.length < 8 || (b[0] & 0xFF) != 0x89 || b[1] != 0x50 || b[2] != 0x4E || b[3] != 0x47) {
                    throw new CranialException(ErrorCode.FILE_SIGNATURE_MISMATCH, "File claims .png extension but lacks PNG magic header");
                }
            }
            case "jpg", "jpeg" -> {
                if (b.length < 3 || (b[0] & 0xFF) != 0xFF || (b[1] & 0xFF) != 0xD8 || (b[2] & 0xFF) != 0xFF) {
                    throw new CranialException(ErrorCode.FILE_SIGNATURE_MISMATCH, "File claims JPEG extension but lacks FF D8 FF magic header");
                }
            }
            case "bmp" -> {
                if (b.length < 2 || b[0] != 'B' || b[1] != 'M') {
                    throw new CranialException(ErrorCode.FILE_SIGNATURE_MISMATCH, "File claims .bmp extension but lacks BM magic header");
                }
            }
            case "txt", "csv", "md" -> {
                if (!magicMime.startsWith("text/") && !"application/octet-stream".equalsIgnoreCase(magicMime)) {
                    throw new CranialException(
                            ErrorCode.FILE_SIGNATURE_MISMATCH,
                            "Text file extension '." + ext + "' conflicts with binary signature '" + magicMime + "'"
                    );
                }
            }
            default -> {
            }
        }
    }
}

