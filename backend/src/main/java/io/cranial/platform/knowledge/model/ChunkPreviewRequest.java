package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record ChunkPreviewRequest(
        @NotBlank String content,
        @JsonAlias({"contentType", "content_type"}) String contentType,
        @JsonAlias({"collectionId", "collection_id"}) UUID collectionId,
        @JsonAlias({"leftStrategy", "left_strategy"}) String leftStrategy,
        @JsonAlias({"leftChunkSizeChars", "left_chunk_size_chars"}) Integer leftChunkSizeChars,
        @JsonAlias({"leftChunkOverlapChars", "left_chunk_overlap_chars"}) Integer leftChunkOverlapChars,
        @JsonAlias({"rightStrategy", "right_strategy"}) String rightStrategy,
        @JsonAlias({"rightChunkSizeChars", "right_chunk_size_chars"}) Integer rightChunkSizeChars,
        @JsonAlias({"rightChunkOverlapChars", "right_chunk_overlap_chars"}) Integer rightChunkOverlapChars
) {
}

