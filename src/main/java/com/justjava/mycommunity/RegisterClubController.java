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

    /**
     * Normalise APP_BASE_URL. Railway env vars regularly ship without an
     * `https://` prefix (or with a trailing slash), which produces a malformed
     * `redirect_uri` and lands the browser at a 404. Rather than depend on the
     * env var being set perfectly, coerce it into a valid absolute URL here.
     * Also prefer X-Forwarded-Host if present so it works even when APP_BASE_URL
     * is empty/misconfigured — the request itself tells us what host to use.
     */
    private String resolveBaseUrl(HttpServletRequest request) {
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();

        // Strip common env-var junk.
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);

        // If empty, reconstruct from the incoming request (respecting the
        // reverse-proxy headers Railway sets).
        if (base.isEmpty()) {
            String scheme = header(request, "X-Forwarded-Proto", request.getScheme());
            String host = header(request, "X-Forwarded-Host", request.getServerName());
            base = scheme + "://" + host;
        } else if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://" + base;
        }
        return base;
    }

    private static String header(HttpServletRequest request, String name, String fallback) {
        String v = request.getHeader(name);
        return (v == null || v.isBlank()) ? fallback : v.split(",")[0].trim();
    }

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
        String redirectUri = resolveBaseUrl(request) + "/register-club-callback";
        String url = keycloakBaseUrl
                + "/realms/" + keycloakRealm
                + "/protocol/openid-connect/registrations"
                + "?client_id=" + URLEncoder.encode(keycloakClientId, StandardCharsets.UTF_8)
                + "&response_type=code"
                + "&scope=openid"
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "#intent=create-club";

        log.info("[register-club] appBaseUrl='{}' resolved redirect_uri='{}' -> {}", appBaseUrl, redirectUri, url);
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
