package io.cranial.platform.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class AdminBootstrapInitializer implements ApplicationRunner {

    private final AdminAuthService adminAuthService;

    public AdminBootstrapInitializer(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @Override
    public void run(ApplicationArguments args) {
        adminAuthService.bootstrapInitialAdminIfEmpty();
    }
}

