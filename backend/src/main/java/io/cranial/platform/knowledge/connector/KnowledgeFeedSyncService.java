package io.cranial.platform.knowledge.connector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cranial.platform.audit.AuditEvent;
import io.cranial.platform.audit.AuditService;
import io.cranial.platform.knowledge.chunking.KnowledgeChunker;
import io.cranial.platform.knowledge.entity.KnowledgeCollectionEntity;
import io.cranial.platform.knowledge.entity.KnowledgeConnectorEntity;
import io.cranial.platform.knowledge.mapper.KnowledgeCollectionMapper;
import io.cranial.platform.knowledge.mapper.KnowledgeConnectorMapper;
import io.cranial.platform.knowledge.model.KnowledgeConnectorRequest;
import io.cranial.platform.knowledge.model.KnowledgeConnectorSyncResult;
import io.cranial.platform.knowledge.model.KnowledgeConnectorView;
import io.cranial.platform.knowledge.model.KnowledgeSourceIngestRequest;
import io.cranial.platform.knowledge.model.KnowledgeSourceView;
import io.cranial.platform.knowledge.service.KnowledgeIntelligenceService;
import io.cranial.platform.platform.exception.CranialException;
import io.cranial.platform.platform.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class KnowledgeFeedSyncService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final int MAX_FEED_BODY_CHARS = 512_000;

    private final KnowledgeConnectorMapper connectorMapper;
    private final KnowledgeCollectionMapper collectionMapper;
    private final KnowledgeIntelligenceService knowledgeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public KnowledgeFeedSyncService(
            KnowledgeConnectorMapper connectorMapper,
            KnowledgeCollectionMapper collectionMapper,
            KnowledgeIntelligenceService knowledgeService,
            AuditService auditService,
            ObjectMapper objectMapper
    ) {
        this.connectorMapper = connectorMapper;
        this.collectionMapper = collectionMapper;
        this.knowledgeService = knowledgeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public List<KnowledgeConnectorView> listConnectors(UUID collectionId, UUID callerAppId, boolean isAdmin) {
        LambdaQueryWrapper<KnowledgeConnectorEntity> qw = new LambdaQueryWrapper<KnowledgeConnectorEntity>()
                .orderByAsc(KnowledgeConnectorEntity::getCreatedAt);
        if (collectionId != null) {
            qw.eq(KnowledgeConnectorEntity::getCollectionId, collectionId);
        }
        return connectorMapper.selectList(qw).stream()
                .filter(c -> isAdmin || c.getApplicationId() == null || Objects.equals(c.getApplicationId(), callerAppId))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public KnowledgeConnectorView createConnector(
            KnowledgeConnectorRequest request,
            UUID callerAppId,
            UUID callerTenantId,
            boolean isAdmin,
            String actor,
            String clientIp
    ) {
        KnowledgeCollectionEntity collection = knowledgeService.resolveAuthorizedCollection(
                request.collectionId(), null, callerAppId, callerTenantId, isAdmin
        );

        String code = request.connectorCode().trim().toLowerCase(Locale.ROOT);
        KnowledgeConnectorEntity existing = connectorMapper.selectOne(
                new LambdaQueryWrapper<KnowledgeConnectorEntity>()
                        .eq(KnowledgeConnectorEntity::getConnectorCode, code)
        );
        if (existing != null) {
            throw new CranialException(ErrorCode.CONFLICT, "Knowledge connector already exists: " + code);
        }

        String uri = request.feedUri().trim();
        String type = resolveConnectorType(request.connectorType(), uri);
        if ("HTTP_FEED".equals(type)) {
            assertSafeExternalFeedUri(uri);
        } else if (!uri.startsWith("cranial://sample-feed/")) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR,
                    "INTERNAL_SAMPLE connectors must use cranial://sample-feed/<feed-slug>");
        }

        Instant now = Instant.now();
        KnowledgeConnectorEntity entity = new KnowledgeConnectorEntity();
        entity.setId(UUID.randomUUID());
        entity.setConnectorCode(code);
        entity.setCollectionId(collection.getId());
        entity.setApplicationId(collection.getApplicationId() != null ? collection.getApplicationId() : callerAppId);
        entity.setTenantId(collection.getTenantId() != null ? collection.getTenantId() : callerTenantId);
        entity.setName(request.name().trim());
        entity.setConnectorType(type);
        entity.setFeedUri(uri);
        entity.setTargetSourceCode(request.targetSourceCode().trim().toLowerCase(Locale.ROOT));
        entity.setContentType(request.contentType() != null && !request.contentType().isBlank()
                ? request.contentType().trim().toUpperCase(Locale.ROOT)
                : "MARKDOWN");
        entity.setPermissionScope(request.permissionScope() != null && !request.permissionScope().isBlank()
                ? request.permissionScope().trim().toUpperCase(Locale.ROOT)
                : "AUTHENTICATED");
        entity.setAllowedRolesJson(toJson(request.allowedRoles() != null ? request.allowedRoles() : List.of()));
        entity.setPollIntervalMinutes(request.pollIntervalMinutes() != null ? Math.max(5, request.pollIntervalMinutes()) : 60);
        entity.setLastSyncStatus("NEVER_SYNCED");
        entity.setIsActive(true);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        connectorMapper.insert(entity);

        auditService.record(new AuditEvent(
                isAdmin ? "OPERATOR" : "APPLICATION",
                actor != null ? actor : "system",
                entity.getApplicationId(),
                entity.getTenantId(),
                "KNOWLEDGE_CONNECTOR_CREATED",
                "KNOWLEDGE_CONNECTOR",
                code,
                "SUCCESS",
                Map.of("connector_type", type, "feed_uri", uri),
                clientIp
        ));

        return toView(entity);
    }

    @Transactional
    public KnowledgeConnectorSyncResult syncConnector(
            UUID connectorId,
            boolean force,
            String simulatedRevision,
            UUID callerAppId,
            UUID callerTenantId,
            boolean isAdmin,
            String actor,
            String clientIp
    ) {
        KnowledgeConnectorEntity connector = connectorMapper.selectById(connectorId);
        if (connector == null) {
            throw new CranialException(ErrorCode.RESOURCE_NOT_FOUND, "Knowledge connector not found");
        }
        knowledgeService.resolveAuthorizedCollection(
                connector.getCollectionId(), null, callerAppId, callerTenantId, isAdmin
        );

        Instant now = Instant.now();
        if ("INTERNAL_SAMPLE".equalsIgnoreCase(connector.getConnectorType())) {
            return syncInternalSampleFeed(connector, force, simulatedRevision, callerAppId, callerTenantId, isAdmin, actor, clientIp, now);
        }

        assertSafeExternalFeedUri(connector.getFeedUri());
        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(connector.getFeedUri()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "text/markdown, text/plain, application/json;q=0.8")
                    .GET();
            if (!force && connector.getLastEtag() != null && !connector.getLastEtag().isBlank()) {
                reqBuilder.header("If-None-Match", connector.getLastEtag());
            }
            if (!force && connector.getLastModifiedHeader() != null && !connector.getLastModifiedHeader().isBlank()) {
                reqBuilder.header("If-Modified-Since", connector.getLastModifiedHeader());
            }

            HttpResponse<String> resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = resp.statusCode();

            if (status == 304) {
                String msg = "304 Not Modified (ETag/Last-Modified unchanged; skipped re-indexing)";
                connector.setLastSyncStatus("NOT_MODIFIED");
                connector.setLastSyncMessage(msg);
                connector.setLastSyncedAt(now);
                connector.setUpdatedAt(now);
                connectorMapper.updateById(connector);
                return new KnowledgeConnectorSyncResult(
                        connector.getId(), connector.getConnectorCode(), "NOT_MODIFIED", 304, false,
                        connector.getLastEtag(), connector.getLastModifiedHeader(), connector.getTargetSourceCode(),
                        0, 0, msg, now
                );
            }

            if (status < 200 || status >= 300) {
                String err = "Feed returned HTTP " + status;
                connector.setLastSyncStatus("FAILED");
                connector.setLastSyncMessage(err);
                connector.setLastSyncedAt(now);
                connector.setUpdatedAt(now);
                connectorMapper.updateById(connector);
                return new KnowledgeConnectorSyncResult(
                        connector.getId(), connector.getConnectorCode(), "FAILED", status, false,
                        connector.getLastEtag(), connector.getLastModifiedHeader(), connector.getTargetSourceCode(),
                        0, 0, err, now
                );
            }

            String body = resp.body() != null ? resp.body() : "";
            if (body.length() > MAX_FEED_BODY_CHARS) {
                body = body.substring(0, MAX_FEED_BODY_CHARS);
            }
            String newEtag = resp.headers().firstValue("ETag")
                    .or(() -> resp.headers().firstValue("etag"))
                    .orElse("\"" + KnowledgeChunker.sha256Hex(body).substring(0, 16) + "\"");
            String newLastMod = resp.headers().firstValue("Last-Modified")
                    .or(() -> resp.headers().firstValue("last-modified"))
                    .orElse(null);

            return ingestPulledContent(connector, body, newEtag, newLastMod, force, callerAppId, callerTenantId, isAdmin, actor, clientIp, now);
        } catch (CranialException ce) {
            throw ce;
        } catch (Exception ex) {
            String err = "Feed fetch failed: " + ex.getMessage();
            connector.setLastSyncStatus("FAILED");
            connector.setLastSyncMessage(err.length() > 950 ? err.substring(0, 950) : err);
            connector.setLastSyncedAt(now);
            connector.setUpdatedAt(now);
            connectorMapper.updateById(connector);
            return new KnowledgeConnectorSyncResult(
                    connector.getId(), connector.getConnectorCode(), "FAILED", 502, false,
                    connector.getLastEtag(), connector.getLastModifiedHeader(), connector.getTargetSourceCode(),
                    0, 0, err, now
            );
        }
    }

    public static void assertSafeExternalFeedUri(String rawUri) {
        if (rawUri == null || rawUri.isBlank()) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Feed URI is required");
        }
        URI uri;
        try {
            uri = URI.create(rawUri.trim());
        } catch (Exception ex) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Malformed feed URI: " + rawUri);
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "HTTP_FEED URI must use http or https scheme");
        }
        if (uri.getUserInfo() != null) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Feed URI must not embed user credentials");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR, "Feed URI missing host");
        }
        String lowerHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lowerHost)
                || lowerHost.endsWith(".local")
                || lowerHost.endsWith(".internal")
                || "metadata.google.internal".equals(lowerHost)) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR,
                    "SSRF guard blocked internal/local feed host: " + host);
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (addr.isAnyLocalAddress()
                        || addr.isLoopbackAddress()
                        || addr.isLinkLocalAddress()
                        || addr.isSiteLocalAddress()
                        || addr.isMulticastAddress()) {
                    throw new CranialException(ErrorCode.VALIDATION_ERROR,
                            "SSRF guard blocked private, loopback, or link-local feed target: " + host);
                }
                byte[] b = addr.getAddress();
                if (b.length == 4) {
                    int b0 = b[0] & 0xFF;
                    int b1 = b[1] & 0xFF;
                    if (b0 == 10
                            || b0 == 127
                            || (b0 == 169 && b1 == 254)
                            || (b0 == 172 && (b1 >= 16 && b1 <= 31))
                            || (b0 == 192 && b1 == 168)
                            || (b0 == 100 && (b1 >= 64 && b1 <= 127))) {
                        throw new CranialException(ErrorCode.VALIDATION_ERROR,
                                "SSRF guard blocked private or cloud-metadata IPv4 address: " + host);
                    }
                } else if (b.length == 16) {
                    if ((b[0] & 0xFE) == 0xFC) {
                        throw new CranialException(ErrorCode.VALIDATION_ERROR,
                                "SSRF guard blocked IPv6 unique-local address: " + host);
                    }
                }
            }
        } catch (CranialException ce) {
            throw ce;
        } catch (Exception ex) {
            throw new CranialException(ErrorCode.VALIDATION_ERROR,
                    "Unable to resolve external feed host '" + host + "': " + ex.getMessage());
        }
    }

    private KnowledgeConnectorSyncResult syncInternalSampleFeed(
            KnowledgeConnectorEntity connector,
            boolean force,
            String simulatedRevision,
            UUID callerAppId,
            UUID callerTenantId,
            boolean isAdmin,
            String actor,
            String clientIp,
            Instant now
    ) {
        String effectiveRev = simulatedRevision != null && !simulatedRevision.isBlank()
                ? simulatedRevision.trim()
                : extractQueryRev(connector.getFeedUri());
        String feedBody = buildDeterministicSampleBody(connector.getFeedUri(), effectiveRev);
        String computedEtag = "\"cranial-feed-" + KnowledgeChunker.sha256Hex(feedBody).substring(0, 16) + "\"";
        String computedLastMod = "Thu, 30 Oct 2026 09:00:00 GMT";

        if (!force && Objects.equals(computedEtag, connector.getLastEtag())) {
            String msg = "304 Not Modified (ETag " + computedEtag + " unchanged; skipped embedding & chunking)";
            connector.setLastSyncStatus("NOT_MODIFIED");
            connector.setLastSyncMessage(msg);
            connector.setLastSyncedAt(now);
            connector.setUpdatedAt(now);
            connectorMapper.updateById(connector);

            return new KnowledgeConnectorSyncResult(
                    connector.getId(), connector.getConnectorCode(), "NOT_MODIFIED", 304, false,
                    computedEtag, computedLastMod, connector.getTargetSourceCode(),
                    0, 0, msg, now
            );
        }

        return ingestPulledContent(connector, feedBody, computedEtag, computedLastMod, force,
                callerAppId, callerTenantId, isAdmin, actor, clientIp, now);
    }

    private KnowledgeConnectorSyncResult ingestPulledContent(
            KnowledgeConnectorEntity connector,
            String body,
            String newEtag,
            String newLastMod,
            boolean force,
            UUID callerAppId,
            UUID callerTenantId,
            boolean isAdmin,
            String actor,
            String clientIp,
            Instant now
    ) {
        KnowledgeSourceView srcView = knowledgeService.ingestOrUpdateSource(
                connector.getCollectionId(),
                new KnowledgeSourceIngestRequest(
                        connector.getTargetSourceCode(),
                        connector.getName(),
                        connector.getFeedUri(),
                        connector.getContentType(),
                        null,
                        body,
                        connector.getPermissionScope(),
                        parseList(connector.getAllowedRolesJson()),
                        List.of(),
                        Map.of(
                                "connector_code", connector.getConnectorCode(),
                                "etag", newEtag,
                                "sync_mechanism", "conditional_pull"
                        ),
                        now,
                        null,
                        force,
                        false
                ),
                callerAppId,
                callerTenantId,
                isAdmin,
                actor != null ? actor : "connector:" + connector.getConnectorCode(),
                clientIp
        );

        boolean skippedUnchanged = "SKIPPED_UNCHANGED".equals(srcView.lastIndexOutcome());
        String syncStatus = skippedUnchanged ? "SKIPPED_UNCHANGED" : "INDEXED";
        String msg = skippedUnchanged
                ? "200 OK — SHA-256 content unchanged; 0 chunks re-embedded"
                : "200 OK — Incrementally indexed " + srcView.lastChunksAdded() + " new chunks ("
                        + srcView.lastChunksReused() + " reused) with ETag " + newEtag;

        connector.setLastEtag(newEtag);
        connector.setLastModifiedHeader(newLastMod);
        connector.setLastSyncStatus(syncStatus);
        connector.setLastSyncMessage(msg);
        connector.setLastSyncedAt(now);
        connector.setUpdatedAt(now);
        connectorMapper.updateById(connector);

        return new KnowledgeConnectorSyncResult(
                connector.getId(),
                connector.getConnectorCode(),
                syncStatus,
                200,
                !skippedUnchanged,
                newEtag,
                newLastMod,
                connector.getTargetSourceCode(),
                srcView.lastChunksAdded(),
                srcView.lastChunksReused(),
                msg,
                now
        );
    }

    private String buildDeterministicSampleBody(String feedUri, String rev) {
        StringBuilder sb = new StringBuilder();
        sb.append("Q: What is a Sinking Fund (Kantong Tujuan Berkala) in personal money management?\n");
        sb.append("A: A sinking fund sets aside small monthly budget allocations in a dedicated personal tracking pocket for planned annual expenses such as school tuition, vehicle maintenance, or holiday travel without taking debt.\n\n");
        sb.append("Pertanyaan: Bagaimana cara memantau cicilan dan arus kas bersih bulanan di Peggy?\n");
        sb.append("Jawaban: Catat seluruh pemasukan dan pengeluaran terencana; jaga rasio cicilan di bawah 30% pendapatan bersih bulanan (educational money-management tracking only; never executes payments).\n");
        if (rev != null && !rev.isBlank() && !"v1".equalsIgnoreCase(rev)) {
            sb.append("\nQ: Feed Revision ").append(rev).append(" — How should irregular freelance income be budgeted?\n");
            sb.append("A: Base essential monthly pocket targets on your lowest 6-month baseline income and allocate surplus months to emergency or education reserves (Revision ").append(rev).append(").\n");
        }
        return sb.toString();
    }

    private static String extractQueryRev(String uri) {
        if (uri == null) {
            return "v1";
        }
        int q = uri.indexOf("rev=");
        if (q < 0) {
            return "v1";
        }
        String tail = uri.substring(q + 4);
        int amp = tail.indexOf('&');
        return amp >= 0 ? tail.substring(0, amp) : tail;
    }

    private static String resolveConnectorType(String requested, String uri) {
        if (requested != null && !requested.isBlank()) {
            String u = requested.trim().toUpperCase(Locale.ROOT);
            if (!"INTERNAL_SAMPLE".equals(u) && !"HTTP_FEED".equals(u)) {
                throw new CranialException(ErrorCode.VALIDATION_ERROR,
                        "Unsupported connector_type '" + requested + "'. Allowed: INTERNAL_SAMPLE, HTTP_FEED");
            }
            return u;
        }
        return uri.startsWith("cranial://sample-feed/") ? "INTERNAL_SAMPLE" : "HTTP_FEED";
    }

    private KnowledgeConnectorView toView(KnowledgeConnectorEntity c) {
        KnowledgeCollectionEntity col = collectionMapper.selectById(c.getCollectionId());
        return new KnowledgeConnectorView(
                c.getId(),
                c.getConnectorCode(),
                c.getCollectionId(),
                col != null ? col.getCollectionCode() : null,
                c.getApplicationId(),
                c.getTenantId(),
                c.getName(),
                c.getConnectorType(),
                c.getFeedUri(),
                c.getTargetSourceCode(),
                c.getContentType(),
                c.getPermissionScope(),
                parseList(c.getAllowedRolesJson()),
                c.getPollIntervalMinutes() != null ? c.getPollIntervalMinutes() : 60,
                c.getLastEtag(),
                c.getLastModifiedHeader(),
                c.getLastSyncStatus(),
                c.getLastSyncMessage(),
                c.getLastSyncedAt(),
                Boolean.TRUE.equals(c.getIsActive()),
                c.getCreatedAt()
        );
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception ex) {
            return "[]";
        }
    }
}

