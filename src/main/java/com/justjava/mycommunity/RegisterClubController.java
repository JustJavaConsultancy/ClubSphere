package com.justjava.mycommunity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
public class RegisterClubController {

    @Value("${keycloak.base-url:https://ngcloak-production.up.railway.app}")
    private String keycloakBaseUrl;

    @Value("${keycloak.realm-name:community}")
    private String keycloakRealm;

    @Value("${keycloak.client-id:community}")
    private String keycloakClientId;

    @GetMapping("/register-club")
    public void registerClub(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String base = scheme + "://" + host;
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        if (!defaultPort) base = base + ":" + port;

        String redirectUri = base + "/register-club-callback";

        String url = keycloakBaseUrl
                + "/realms/" + keycloakRealm
                + "/protocol/openid-connect/registrations"
                + "?client_id=" + URLEncoder.encode(keycloakClientId, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&intent=create-club";

        response.sendRedirect(url);
    }

    @GetMapping("/register-club-callback")
    public void registerClubCallback(HttpServletResponse response) throws IOException {
        // The user just registered on Keycloak — they now have an active KC session.
        // Kick them through the normal Spring OAuth2 login so the app receives their tokens
        // and the OidcAuthenticationSuccessHandler runs (which saves the user locally,
        // and HomeController.home() will then finish the club creation).
        response.sendRedirect("/oauth2/authorization/keycloak");
    }
}
