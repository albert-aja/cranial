package io.cranial.platform.platform.observability;

import io.cranial.platform.platform.config.CranialProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("cranialPlatform")
public class CranialHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;
    private final CranialProperties properties;

    public CranialHealthIndicator(JdbcTemplate jdbcTemplate, CranialProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Override
    public Health health() {
        try {
            Integer probe = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (probe == null || probe != 1) {
                return Health.down().withDetail("database", "unexpected probe result").build();
            }
            return Health.up()
                    .withDetail("platform", "CRANIAL")
                    .withDetail("environment", properties.environment())
                    .withDetail("localFirstDefault", true)
                    .withDetail("database", "reachable")
                    .build();
        } catch (Exception ex) {
            return Health.down()
                    .withDetail("platform", "CRANIAL")
                    .withDetail("database", "unreachable")
                    .build();
        }
    }
}

