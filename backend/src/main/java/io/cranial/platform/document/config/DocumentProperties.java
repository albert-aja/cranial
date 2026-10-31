package io.cranial.platform.document.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cranial.document")
public record DocumentProperties(
        String storageDir,
        Long maxDocumentBytes,
        Integer uploadUrlTtlMinutes,
        Integer downloadUrlTtlMinutes,
        String tesseractCommand,
        Integer tesseractTimeoutSeconds,
        Integer asyncPageThreshold
) {
    public String effectiveStorageDir() {
        if (storageDir == null || storageDir.isBlank()) {
            return System.getProperty("java.io.tmpdir") + "/cranial-doc-store";
        }
        return storageDir;
    }

    public long effectiveMaxDocumentBytes() {
        return maxDocumentBytes != null && maxDocumentBytes > 0 ? maxDocumentBytes : 10L * 1024 * 1024;
    }

    public int effectiveUploadUrlTtlMinutes() {
        return uploadUrlTtlMinutes != null && uploadUrlTtlMinutes > 0 ? uploadUrlTtlMinutes : 15;
    }

    public int effectiveDownloadUrlTtlMinutes() {
        return downloadUrlTtlMinutes != null && downloadUrlTtlMinutes > 0 ? downloadUrlTtlMinutes : 5;
    }

    public String effectiveTesseractCommand() {
        return tesseractCommand != null && !tesseractCommand.isBlank() ? tesseractCommand : "tesseract";
    }

    public int effectiveTesseractTimeoutSeconds() {
        return tesseractTimeoutSeconds != null && tesseractTimeoutSeconds > 0 ? tesseractTimeoutSeconds : 30;
    }

    public int effectiveAsyncPageThreshold() {
        return asyncPageThreshold != null && asyncPageThreshold > 0 ? asyncPageThreshold : 5;
    }
}

