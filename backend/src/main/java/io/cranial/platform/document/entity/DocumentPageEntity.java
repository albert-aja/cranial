package io.cranial.platform.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.Instant;
import java.util.UUID;

@TableName("ai_document_pages")
public class DocumentPageEntity {

    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID documentId;
    private Integer pageNumber;
    private String status;
    private String engineUsed;
    private Double ocrConfidence;
    private String extractedText;
    private Integer charCount;
    private Integer wordCount;
    private String preprocessAppliedJson;
    private String diagnosticsJson;
    private Long durationMs;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDocumentId() { return documentId; }
    public void setDocumentId(UUID documentId) { this.documentId = documentId; }

    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEngineUsed() { return engineUsed; }
    public void setEngineUsed(String engineUsed) { this.engineUsed = engineUsed; }

    public Double getOcrConfidence() { return ocrConfidence; }
    public void setOcrConfidence(Double ocrConfidence) { this.ocrConfidence = ocrConfidence; }

    public String getExtractedText() { return extractedText; }
    public void setExtractedText(String extractedText) { this.extractedText = extractedText; }

    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public String getPreprocessAppliedJson() { return preprocessAppliedJson; }
    public void setPreprocessAppliedJson(String preprocessAppliedJson) { this.preprocessAppliedJson = preprocessAppliedJson; }

    public String getDiagnosticsJson() { return diagnosticsJson; }
    public void setDiagnosticsJson(String diagnosticsJson) { this.diagnosticsJson = diagnosticsJson; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}

