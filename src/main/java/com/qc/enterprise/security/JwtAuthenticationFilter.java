package com.qc.enterprise.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String userEmail;
        final String userRole;

        // 1. Check if the request has a Bearer token
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Extract Token and Data
        jwt = authHeader.substring(7);
        try {
            userEmail = jwtService.extractUsername(jwt);
            userRole = jwtService.extractRole(jwt);

            // 3. If valid, set the Security Context
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                // We create a temporary UserDetails object based purely on the JWT data
                UserDetails userDetails = new User(userEmail, "",
                        Collections.singletonList(new SimpleGrantedAuthority(userRole)));

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Officially authenticate the user for this request
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (Exception e) {
            // Token is invalid or expired
            logger.error("JWT Authentication failed: " + e.getMessage());
        }

        String userEmails = jwtService.extractUsername(jwt);
        String userRoles = jwtService.extractRole(jwt);

        // ADD THIS LINE:
        System.out.println("Bouncer Check -> User: " + userEmails + " | Role: " + userRoles);

        filterChain.doFilter(request, response);
    }
}