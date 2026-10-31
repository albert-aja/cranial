package io.cranial.platform.platform.deprecation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Interceptor that injects RFC 8594 (Deprecation) and RFC 7231 (Sunset) headers
 * into responses for endpoints annotated with {@link ApiDeprecated}.
 */
@Component
public class ApiDeprecationInterceptor implements HandlerInterceptor {

    public static final String HEADER_DEPRECATION = "Deprecation";
    public static final String HEADER_SUNSET = "Sunset";
    public static final String HEADER_LINK = "Link";

    private static final DateTimeFormatter IMF_FIXDATE = DateTimeFormatter.RFC_1123_DATE_TIME
            .withZone(ZoneOffset.UTC);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        ApiDeprecated annotation = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), ApiDeprecated.class
        );
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(
                    handlerMethod.getBeanType(), ApiDeprecated.class
            );
        }

        if (annotation != null) {
            applyDeprecationHeaders(response, annotation);
        }

        return true;
    }

    private void applyDeprecationHeaders(HttpServletResponse response, ApiDeprecated annotation) {
        // RFC 8594 Deprecation header
        response.setHeader(HEADER_DEPRECATION, "true");

        // Sunset header if configured
        if (!annotation.sunset().isBlank()) {
            String formattedSunset = formatSunset(annotation.sunset().trim());
            if (formattedSunset != null) {
                response.setHeader(HEADER_SUNSET, formattedSunset);
            }
        }

        // Link headers for successor-version and deprecation docs
        List<String> links = new ArrayList<>();
        if (!annotation.successor().isBlank()) {
            links.add("<" + annotation.successor().trim() + ">; rel=\"successor-version\"");
        }
        if (!annotation.docUrl().isBlank()) {
            links.add("<" + annotation.docUrl().trim() + ">; rel=\"deprecation\"");
        }

        for (String link : links) {
            response.addHeader(HEADER_LINK, link);
        }
    }

    private String formatSunset(String rawSunset) {
        try {
            Instant instant = Instant.parse(rawSunset);
            return IMF_FIXDATE.format(instant);
        } catch (Exception e) {
            // If already in RFC 1123 or other format, return raw string
            return rawSunset;
        }
    }
}

