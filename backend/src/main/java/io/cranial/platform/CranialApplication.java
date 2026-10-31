package io.cranial.platform;

import io.cranial.platform.document.config.DocumentProperties;
import io.cranial.platform.platform.config.CranialProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties({CranialProperties.class, DocumentProperties.class})
@MapperScan(
        basePackages = {
                "io.cranial.platform.audit",
                "io.cranial.platform.application",
                "io.cranial.platform.provider.mapper",
                "io.cranial.platform.orchestration.mapper",
                "io.cranial.platform.document.mapper",
                "io.cranial.platform.knowledge.mapper",
                "io.cranial.platform.analytics.mapper",
                "io.cranial.platform.decision.mapper",
                "io.cranial.platform.customer.mapper",
                "io.cranial.platform.admin"
        },
        annotationClass = org.apache.ibatis.annotations.Mapper.class
)
public class CranialApplication {

    public static void main(String[] args) {
        SpringApplication.run(CranialApplication.class, args);
    }
}
