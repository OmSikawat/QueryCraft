package com.qc.enterprise.controller;

import com.qc.enterprise.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        // MOCK AUTHENTICATION: Assign roles based on who is logging in
        String role;
        if ("admin".equals(username) && "admin123".equals(password)) {
            role = "ROLE_ADMIN";
        } else if ("sales".equals(username) && "sales123".equals(password)) {
            role = "ROLE_SALES";
        } else if ("intern".equals(username) && "intern123".equals(password)) {
            role = "ROLE_INTERN";
        } else {
            throw new RuntimeException("Invalid credentials. The Bouncer kicks you out.");
        }

        // Generate the Cryptographic Passport (JWT)
        String token = jwtService.generateToken(username, role);

        // Return it to the user
        Map<String, String> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("username", username);
        response.put("role", role);
        response.put("token", token);
        return response;
    }
}