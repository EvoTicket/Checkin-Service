package com.capstone.checkinservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/webjars/**"
                        ).permitAll()

                        .requestMatchers("/api/v1/tickets/*/qr-token")
                        .hasAnyAuthority("ROLE_USER", "ROLE_BUYER", "ROLE_ADMIN")

                        .requestMatchers("/api/v1/admin/checker/**")
                        .hasAnyAuthority(
                                "ROLE_ADMIN",
                                "ROLE_CHECKER_SUPERVISOR",
                                "ROLE_ORGANIZER_MANAGER"
                        )

                        .requestMatchers("/api/v1/checker/**")
                        .hasAnyAuthority(
                                "ROLE_CHECKER",
                                "ROLE_CHECKER_SUPERVISOR",
                                "ROLE_ADMIN",
                                "ROLE_SUPPORT"
                        )

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(new IamJwtAuthenticationConverter())
                ));

        return http.build();
    }
}
