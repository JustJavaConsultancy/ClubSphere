package com.justjava.mycommunity.mobile;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.justjava.mycommunity.userManagement.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/mobile")
public class MobileBiometricAuthController {

    private static final String MOBILE_REGISTRATION_ID = "keycloak-mobile";

    private final RestTemplate restTemplate;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final UserService userService;

    public MobileBiometricAuthController(RestTemplate restTemplate,
                                         ClientRegistrationRepository clientRegistrationRepository,
                                         OAuth2AuthorizedClientService authorizedClientService,
                                         UserService userService) {
        this.restTemplate = restTemplate;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.authorizedClientService = authorizedClientService;
        this.userService = userService;
    }

    @GetMapping("/biometric/bootstrap")
    public ResponseEntity<Void> bootstrap(HttpServletRequest request) {
        if (isAuthenticated()) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/mobile/channel").build();
        }
        request.getSession(true).setAttribute("mobile", true);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, "/oauth2/authorization/" + MOBILE_REGISTRATION_ID)
                .build();
    }

    @GetMapping("/auth/tokens")
    public ResponseEntity<Map<String, Object>> sessionTokens(org.springframework.security.core.Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthenticated session"));
        }

        String registrationId = MOBILE_REGISTRATION_ID;
        if (authentication instanceof org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken token) {
            registrationId = token.getAuthorizedClientRegistrationId();
        }
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(registrationId, authentication.getName());
        if (client == null) {
            return ResponseEntity.status(404).body(Map.of("message", "No authorized client token found"));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("access_token", client.getAccessToken().getTokenValue());
        payload.put("access_token_expires_at", client.getAccessToken().getExpiresAt());
        if (client.getRefreshToken() != null) {
            payload.put("refresh_token", client.getRefreshToken().getTokenValue());
        }
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<Map<String, Object>> refresh(@RequestBody RefreshRequest requestBody) {
        if (requestBody == null || requestBody.refreshToken() == null || requestBody.refreshToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "refreshToken is required"));
        }

        ClientRegistration registration = getMobileClientRegistration();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", requestBody.refreshToken());
        form.add("client_id", registration.getClientId());
        if (registration.getClientSecret() != null && !registration.getClientSecret().isBlank()) {
            form.add("client_secret", registration.getClientSecret());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<Map> tokenResponse = restTemplate.exchange(
                registration.getProviderDetails().getTokenUri(),
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                Map.class
        );
        return ResponseEntity.status(tokenResponse.getStatusCode()).body(tokenResponse.getBody());
    }

    @PostMapping("/auth/session/login")
    public ResponseEntity<Map<String, Object>> sessionLogin(@RequestBody SessionLoginRequest requestBody,
                                                             HttpServletRequest request) {
        if (requestBody == null || requestBody.accessToken() == null || requestBody.accessToken().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "accessToken is required"));
        }
        ClientRegistration registration = getMobileClientRegistration();
        Map<String, Object> userInfo = getUserInfo(registration, requestBody.accessToken());
        if (userInfo == null || userInfo.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("message", "Could not validate access token"));
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(300);
        try {
            JWTClaimsSet claimsSet = JWTParser.parse(requestBody.accessToken()).getJWTClaimsSet();
            if (claimsSet.getIssueTime() != null) {
                issuedAt = claimsSet.getIssueTime().toInstant();
            }
            if (claimsSet.getExpirationTime() != null) {
                expiresAt = claimsSet.getExpirationTime().toInstant();
            }
        } catch (ParseException ignored) {
            // Keep defaults if token cannot be parsed locally.
        }

        Map<String, Object> idTokenClaims = new HashMap<>(userInfo);
        idTokenClaims.putIfAbsent(IdTokenClaimNames.SUB, userInfo.get("sub"));
        OidcIdToken idToken = new OidcIdToken(requestBody.accessToken(), issuedAt, expiresAt, idTokenClaims);
        OidcUserAuthority authority = new OidcUserAuthority(idToken, new org.springframework.security.oauth2.core.oidc.OidcUserInfo(userInfo));
        DefaultOidcUser principal = new DefaultOidcUser(Collections.singletonList(authority), idToken, "sub");

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute("mobile", true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);

        OAuth2AuthorizedClient client = buildAuthorizedClient(registration, requestBody.accessToken(), requestBody.refreshToken(), principal.getName(), expiresAt);
        if (client != null) {
            authorizedClientService.saveAuthorizedClient(client, authentication);
        }
        userService.saveAuthenticatedUser();

        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "redirectUrl", "/mobile/channel"
        ));
    }

    private Map<String, Object> getUserInfo(ClientRegistration registration, String accessToken) {
        String userInfoUri = registration.getProviderDetails().getUserInfoEndpoint().getUri();
        if (userInfoUri == null || userInfoUri.isBlank()) {
            String issuerUri = registration.getProviderDetails().getIssuerUri();
            if (issuerUri != null) {
                userInfoUri = issuerUri + "/protocol/openid-connect/userinfo";
            }
        }
        if (userInfoUri == null || userInfoUri.isBlank()) {
            return Collections.emptyMap();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map> userInfoResponse = restTemplate.exchange(
                userInfoUri,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        return userInfoResponse.getBody() == null ? Collections.emptyMap() : userInfoResponse.getBody();
    }

    private OAuth2AuthorizedClient buildAuthorizedClient(ClientRegistration registration,
                                                         String accessToken,
                                                         String refreshToken,
                                                         String principalName,
                                                         Instant expiresAt) {
        var access = new org.springframework.security.oauth2.core.OAuth2AccessToken(
                org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,
                accessToken,
                Instant.now(),
                expiresAt
        );
        if (refreshToken != null && !refreshToken.isBlank()) {
            var refresh = new org.springframework.security.oauth2.core.OAuth2RefreshToken(refreshToken, Instant.now());
            return new OAuth2AuthorizedClient(registration, principalName, access, refresh);
        }
        return new OAuth2AuthorizedClient(registration, principalName, access);
    }

    private ClientRegistration getMobileClientRegistration() {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId(MOBILE_REGISTRATION_ID);
        if (registration == null) {
            throw new IllegalStateException("Missing OAuth client registration: " + MOBILE_REGISTRATION_ID);
        }
        return registration;
    }

    private boolean isAuthenticated() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()));
    }

    private record RefreshRequest(String refreshToken) {}
    private record SessionLoginRequest(String accessToken, String refreshToken) {}
}
