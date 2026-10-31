package io.cranial.platform.gateway;

import io.cranial.platform.document.model.DocumentView;
import io.cranial.platform.document.service.DocumentIntelligenceService;
import io.cranial.platform.platform.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/documents/transfer")
@Tag(name = "Document Signed Transfers", description = "Expiring HMAC-signed temporary upload and download endpoints")
public class DocumentTransferController {

    private final DocumentIntelligenceService documentService;

    public DocumentTransferController(DocumentIntelligenceService documentService) {
        this.documentService = documentService;
    }

    @PutMapping("/upload/{signedToken}")
    @Operation(summary = "Upload raw document bytes using a temporary signed upload URL")
    public ApiResponse<DocumentView> uploadViaToken(
            @PathVariable String signedToken,
            @RequestBody byte[] rawBytes,
            HttpServletRequest request
    ) {
        DocumentView view = documentService.completeTemporaryUpload(
                signedToken,
                rawBytes,
                request.getContentType(),
                request.getRemoteAddr()
        );
        return ApiResponse.ok(view);
    }

    @GetMapping("/download/{signedToken}")
    @Operation(summary = "Download document bytes using a temporary signed download URL")
    public ResponseEntity<byte[]> downloadViaToken(
            @PathVariable String signedToken,
            HttpServletRequest request
    ) {
        DocumentIntelligenceService.DownloadedDocumentPayload payload =
                documentService.downloadByToken(signedToken, request.getRemoteAddr());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(payload.mimeType()));
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(payload.fileName(), StandardCharsets.UTF_8).build()
        );
        headers.set("X-Content-Type-Options", "nosniff");
        headers.setCacheControl("no-store, max-age=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(payload.bytes());
    }
}

