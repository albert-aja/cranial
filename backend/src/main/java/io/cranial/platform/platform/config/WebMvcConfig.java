package io.cranial.platform.platform.config;

import io.cranial.platform.platform.deprecation.ApiDeprecationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiDeprecationInterceptor deprecationInterceptor;

    public WebMvcConfig(ApiDeprecationInterceptor deprecationInterceptor) {
        this.deprecationInterceptor = deprecationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(deprecationInterceptor);
    }
}

