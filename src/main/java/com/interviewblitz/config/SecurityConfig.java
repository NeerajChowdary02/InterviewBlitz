package com.interviewblitz.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * HTTP Basic authentication for all /api/** endpoints.
 * Static frontend files are publicly accessible so the SPA shell loads before
 * any credentials are required. The frontend sends an Authorization header with
 * every API fetch call; Spring Security validates it on each request.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${APP_USERNAME:admin}")
    private String username;

    @Value("${APP_PASSWORD:interviewblitz}")
    private String password;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            // CSRF protection is unnecessary here: the client uses the Authorization
            // header (not cookies) for authentication, so there is no CSRF vector.
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // Static app shell — must load before the user can log in
                .requestMatchers(
                        "/", "/index.html", "/app.js", "/manifest.json",
                        "/sw.js", "/icons/**"
                ).permitAll()
                // All API endpoints require authentication
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails user = User.builder()
                .username(username)
                .password(passwordEncoder().encode(password))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Allows same-origin requests with credentials so the frontend can include
     * the Authorization header on API calls served from the same host.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:8080"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
