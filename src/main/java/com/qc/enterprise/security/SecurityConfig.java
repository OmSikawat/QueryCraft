package com.qc.enterprise.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 1. Explicitly allow your React frontend
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));

        // 2. Allow all standard HTTP methods, ESPECIALLY 'OPTIONS'
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 3. Explicitly allow the Authorization and Content-Type headers
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));

        // 4. Allow credentials (required for JWTs)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable()) // Disable CSRF for REST APIs
                .authorizeHttpRequests(auth -> auth
                        // Allow anyone to access the login/auth endpoints
                        .requestMatchers("/api/auth/**").permitAll()
                        // In SecurityConfig.java
                        .requestMatchers("/api/test/execute").hasAnyAuthority("ROLE_ADMIN", "ROLE_SALES")
                        .requestMatchers("/api/ai/embed").hasRole("ADMIN")
                        .requestMatchers("/api/ai/query").hasAnyAuthority("ROLE_ADMIN", "ROLE_SALES")
                        // For today, allow the schema extraction endpoint so we can test it
                        .requestMatchers("/api/schema/refresh").permitAll()
                        // Require authentication for all future AI Query endpoints
                        .anyRequest().authenticated()
                )
                // JWT means no sessions; every request is independently verified
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Add our Bouncer before the standard Spring Security checks
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}