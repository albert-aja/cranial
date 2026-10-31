package io.cranial.platform.knowledge.embedding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.provider.adapter.MockLocalProviderAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KnowledgeEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEmbeddingService.class);
    private static final TypeReference<List<Double>> VECTOR_TYPE = new TypeReference<>() {};
    public static final String DEFAULT_LOCAL_EMBED_MODEL = "local-semantic-embed-v1";
    public static final int DEFAULT_DIMENSIONS = 64;
    public static final int DEFAULT_HNSW_M = 16;
    public static final int DEFAULT_HNSW_EF_CONSTRUCTION = 64;
    public static final int DEFAULT_HNSW_EF_SEARCH = 40;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean pgVectorChecked = new AtomicBoolean(false);
    private volatile boolean pgVectorActive = false;

    public KnowledgeEmbeddingService(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
    }

    public record EmbeddingBatchOutput(
            String modelCode,
            int dimensions,
            List<List<Double>> vectors,
            long durationMs
    ) {
    }

    public static int clampHnswM(Integer m) {
        return m == null ? DEFAULT_HNSW_M : Math.max(4, Math.min(64, m));
    }

    public static int clampHnswEfConstruction(Integer efConstruction) {
        return efConstruction == null ? DEFAULT_HNSW_EF_CONSTRUCTION : Math.max(16, Math.min(512, efConstruction));
    }

    public static int clampHnswEfSearch(Integer efSearch) {
        return efSearch == null ? DEFAULT_HNSW_EF_SEARCH : Math.max(10, Math.min(500, efSearch));
    }

    public boolean isPgVectorActive() {
        ensurePgVectorBridgeInitialized();
        return pgVectorActive;
    }

    public EmbeddingBatchOutput embedTexts(List<String> texts, String requestedModelCode, int dimensions) {
        long start = System.currentTimeMillis();
        int dims = dimensions > 0 ? dimensions : DEFAULT_DIMENSIONS;
        String modelCode = (requestedModelCode != null && !requestedModelCode.isBlank())
                ? requestedModelCode.trim()
                : DEFAULT_LOCAL_EMBED_MODEL;

        List<List<Double>> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(MockLocalProviderAdapter.computeDeterministicSemanticVector(text, dims));
        }

        ensurePgVectorBridgeInitialized();
        long elapsed = Math.max(1L, System.currentTimeMillis() - start);
        return new EmbeddingBatchOutput(modelCode, dims, vectors, elapsed);
    }

    public String toJsonVector(List<Double> vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (Exception ex) {
            return "[]";
        }
    }

    public List<Double> parseJsonVector(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, VECTOR_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    public double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int len = Math.min(a.size(), b.size());
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < len; i++) {
            double va = a.get(i);
            double vb = b.get(i);
            dot += va * vb;
            normA += va * va;
            normB += vb * vb;
        }
        if (normA < 1e-12 || normB < 1e-12) {
            return 0.0;
        }
        double cos = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return Math.max(0.0, Math.min(1.0, cos));
    }

    public void syncPgVectorColumnIfAvailable(UUID chunkId, List<Double> vector) {
        ensurePgVectorBridgeInitialized();
        if (!pgVectorActive || chunkId == null || vector == null || vector.isEmpty()) {
            return;
        }
        String literal = toPgVectorLiteral(vector);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE ai_knowledge_chunks SET embedding_vec = CAST(? AS vector) WHERE id = ?")) {
            ps.setString(1, literal);
            ps.setObject(2, chunkId);
            ps.executeUpdate();
        } catch (Exception ex) {
            log.debug("Optional pgvector column sync skipped: {}", ex.getMessage());
        }
    }

    public void ensureCollectionHnswIndex(UUID collectionId, Integer m, Integer efConstruction, boolean recreate) {
        ensurePgVectorBridgeInitialized();
        if (!pgVectorActive || collectionId == null) {
            return;
        }
        int safeM = clampHnswM(m);
        int safeEfCons = clampHnswEfConstruction(efConstruction);
        String hexPrefix = collectionId.toString().replace("-", "").substring(0, 16);
        String indexName = "idx_kch_hnsw_" + hexPrefix;
        String colLiteral = collectionId.toString();

        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            if (recreate) {
                st.execute("DROP INDEX IF EXISTS " + indexName);
            }
            st.execute("CREATE INDEX IF NOT EXISTS " + indexName
                    + " ON ai_knowledge_chunks USING hnsw (embedding_vec vector_cosine_ops)"
                    + " WITH (m = " + safeM + ", ef_construction = " + safeEfCons + ")"
                    + " WHERE collection_id = '" + colLiteral + "'");
            log.info("Configured PostgreSQL pgvector HNSW index {} (m={}, ef_construction={})",
                    indexName, safeM, safeEfCons);
        } catch (Exception ex) {
            log.debug("pgvector HNSW index DDL skipped: {}", ex.getMessage());
        }
    }

    public void applyEfSearchHint(Integer efSearch) {
        ensurePgVectorBridgeInitialized();
        if (!pgVectorActive) {
            return;
        }
        int safeEfSearch = clampHnswEfSearch(efSearch);
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET hnsw.ef_search = " + safeEfSearch);
        } catch (Exception ex) {
            log.debug("pgvector hnsw.ef_search session hint skipped: {}", ex.getMessage());
        }
    }

    private void ensurePgVectorBridgeInitialized() {
        if (!pgVectorChecked.compareAndSet(false, true)) {
            return;
        }
        try (Connection conn = dataSource.getConnection()) {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null || !product.toLowerCase().contains("postgresql")) {
                return;
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'")) {
                if (rs.next()) {
                    st.execute("ALTER TABLE ai_knowledge_chunks ADD COLUMN IF NOT EXISTS embedding_vec vector(" + DEFAULT_DIMENSIONS + ")");
                    pgVectorActive = true;
                    log.info("Detected PostgreSQL pgvector extension; synchronized native vector({}) column on ai_knowledge_chunks", DEFAULT_DIMENSIONS);
                }
            }
        } catch (Exception ex) {
            log.debug("Running portable JSONB vector storage mode (pgvector native column not active: {})", ex.getMessage());
        }
    }

    private String toPgVectorLiteral(List<Double> vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec.get(i));
        }
        sb.append(']');
        return sb.toString();
    }
}
