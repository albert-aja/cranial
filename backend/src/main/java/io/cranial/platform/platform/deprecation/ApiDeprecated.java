package io.cranial.platform.platform.deprecation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller class or handler method as deprecated in accordance with RFC 8594.
 * Emits standard Deprecation, Sunset, and Link headers on HTTP responses.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiDeprecated {

    /**
     * Platform version since which this endpoint was deprecated (e.g. "v1.1").
     */
    String since() default "v1";

    /**
     * ISO-8601 or RFC-1123 date string when this endpoint will be decommissioned (e.g. "2027-06-01T00:00:00Z").
     */
    String sunset() default "";

    /**
     * Successor URI or route replacement (e.g. "/api/v2/gateway/verify-request").
     */
    String successor() default "";

    /**
     * Documentation link explaining migration path.
     */
    String docUrl() default "";
}

