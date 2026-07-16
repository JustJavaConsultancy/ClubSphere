package com.justjava.mycommunity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
public class RegisterClubController {

    // Set on the HttpSession before the Keycloak round-trip so HomeController can
    // log whether the intent survived Spring's session-fixation change on login.
    // Not load-bearing for the actual club creation — the source of truth is the
    // Keycloak user attributes that register.ftl writes.
    public static final String SESSION_INTENT_KEY = "registerClubIntent";

    @Value("${keycloak.base-url}")
    private String keycloakBaseUrl;

    @Value("${keycloak.realm-name}")
    private String keycloakRealm;

    @Value("${keycloak.client-id}")
    private String keycloakClientId;

    @Value("${app.base.url}")
    private String appBaseUrl;

    @GetMapping("/register-club")
    public void registerClub(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession s = request.getSession(true);
        s.setAttribute(SESSION_INTENT_KEY, Boolean.TRUE);

        // Manually build the Keycloak registration URL.
        //
        // Why not /oauth2/authorization/keycloak with a Spring resolver? Because
        // Spring's OAuth resolver appends state/nonce/etc. as query params, and
        // Keycloak's own internal /registrations → /login-actions/registration
        // redirect drops query params we tack on for our register.ftl theme. The
        // `#intent=create-club` URL fragment DOES survive that redirect (browsers
        // preserve fragments across HTTP redirects) — so it reliably reaches
        // register.ftl's JS, which reveals the club-creation fields.
        String redirectUri = appBaseUrl + "/register-club-callback";
        String url = keycloakBaseUrl
                + "/realms/" + keycloakRealm
                + "/protocol/openid-connect/registrations"
                + "?client_id=" + URLEncoder.encode(keycloakClientId, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "#intent=create-club";

        log.info("[register-club] Redirecting to Keycloak registration: {}", url);
        response.sendRedirect(url);
    }

    // Keycloak posts the auth code back here after registration. We don't do the
    // code exchange ourselves — bouncing to /oauth2/authorization/keycloak hands
    // off to Spring Security, which runs its normal flow and lands the user on
    // "/". HomeController then materialises the club from the Keycloak user
    // attributes register.ftl wrote during signup.
    @GetMapping("/register-club-callback")
    public void registerClubCallback(HttpServletResponse response) throws IOException {
        response.sendRedirect("/oauth2/authorization/keycloak");
    }
}
