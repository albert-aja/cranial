package io.cranial.platform.application.maintenance;

import java.time.Instant;
import java.util.Map;

public interface MaintenanceCleanupContributor {

    String contributorName();

    Map<String, Object> cleanupExpired(Instant now);
}

