package com.justjava.mycommunity.config;

import com.justjava.mycommunity.userManagement.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AnonymousConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

@RequiredArgsConstructor
@Configuration
@EnableWebSecurity
public class Oauth2SecurityConfig {
    private static final Logger log = LoggerFactory.getLogger(Oauth2SecurityConfig.class);
    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;
    @Autowired
    private UserService userService;

    @Bean
    protected SecurityFilterChain configure(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        log.debug("Configuring security");


        http.anonymous(AnonymousConfigurer::disable)
                .sessionManagement(httpSecuritySessionManagementConfigurer ->
                        httpSecuritySessionManagementConfigurer
                                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
                .csrf(CsrfConfigurer::disable)
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/landing")
                        .successHandler(oidcAuthenticationSuccessHandler()))
                .oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt)
                .authorizeHttpRequests(
                        authorize -> authorize
                                .requestMatchers(new MvcRequestMatcher(introspector, "/login"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/landing"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/register-club"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/register-club-callback"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/swagger-ui/**"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/invoice/pay/**"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/invoice/payment-callback/**"))
                                .permitAll()
                                .requestMatchers(new AntPathRequestMatcher("/api-v/auth", "POST"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/support/submit-request"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/mobile/biometric/bootstrap"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/mobile/auth/refresh"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/mobile/auth/session/login"))
                                .permitAll()
                                .requestMatchers(new MvcRequestMatcher(introspector, "/clubsphere-mobile/**"))
                                .permitAll()
                                .anyRequest()
                                .authenticated()
                )

                .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler())
                        .invalidateHttpSession(false)
                        .logoutUrl("/logout"));
        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler=
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);
        return (request, response, authentication) -> {
            HttpSession session = request.getSession(false);
            boolean isMobile = session != null && Boolean.TRUE.equals(session.getAttribute("mobile"));
            if (isMobile) {
                oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/oauth2/authorization/keycloak-mobile");
            } else {
                oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
            }
            if (session != null)
                session.invalidate();
            oidcLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);
        };
    }

    private AuthenticationSuccessHandler oidcAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            userService.saveAuthenticatedUser();
            request.getSession(true).setAttribute("mobile", false);
            if(request.getRequestURI()!=null&&request.getRequestURI().contains("keycloak-mobile")){
                request.getSession(true).setAttribute("mobile", true);
                response.sendRedirect("/mobile/channel"); //mobile url
            } else {
                response.sendRedirect("/");
            }
        };
    }
}
