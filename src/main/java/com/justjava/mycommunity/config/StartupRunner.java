package com.justjava.mycommunity.config;

import com.justjava.mycommunity.keycloak.KeycloakAdminService;
import com.justjava.mycommunity.organization.OrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {
    private final KeycloakAdminService keycloakAdminService;
    private final OrganizationService organizationService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            keycloakAdminService.syncKeycloak();
        } catch (Exception e) {
            log.error("Keycloak sync failed at startup; continuing so the app can boot.", e);
        }
        try {
            organizationService.createDefaultOrgAndCommunity();
        } catch (Exception e) {
            log.error("createDefaultOrgAndCommunity failed at startup; continuing so the app can boot.", e);
        }
    }
}
