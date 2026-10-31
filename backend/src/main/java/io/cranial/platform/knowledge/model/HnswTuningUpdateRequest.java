package io.cranial.platform.knowledge.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record HnswTuningUpdateRequest(
        @JsonAlias({"hnswM", "hnsw_m"}) Integer hnswM,
        @JsonAlias({"hnswEfConstruction", "hnsw_ef_construction"}) Integer hnswEfConstruction,
        @JsonAlias({"hnswEfSearch", "hnsw_ef_search"}) Integer hnswEfSearch
) {
}

