package com.justjava.mycommunity.config;

import com.justjava.mycommunity.keycloak.KeycloakAdminService;
import com.justjava.mycommunity.organization.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StartupRunner implements ApplicationRunner {
    private final KeycloakAdminService keycloakAdminService;
    private final OrganizationService organizationService;

    @Override
    public void run(ApplicationArguments args) {
        //Temporary: current error - realm does not exist
        keycloakAdminService.syncKeycloak();
       organizationService.createDefaultOrgAndCommunity();
    }
}
