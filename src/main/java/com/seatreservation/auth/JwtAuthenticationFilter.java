package com.seatreservation.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatreservation.exception.ApiErrorResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates the JWT for every protected endpoint and exposes the authenticated
 * user id and role to the controller layer.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";
    public static final String ROLE_ATTRIBUTE = "authenticatedRole";
    public static final String ADMIN_ROLE = "ADMIN";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/auth/token".equals(path)
                || path.startsWith("/actuator/health")
                || "/actuator/prometheus".equals(path)
                || path.startsWith("/actuator/metrics")
                || path.startsWith("/swagger-ui/")
                || "/swagger-ui.html".equals(path)
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response, "Bearer JWT token required");
            return;
        }

        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            unauthorized(response, "Bearer JWT token required");
            return;
        }

        try {
            Claims claims = jwtService.parseClaims(token);
            String userId = jwtService.extractUserId(claims);
            String role = jwtService.extractRole(claims);

            request.setAttribute(USER_ID_ATTRIBUTE, userId);
            request.setAttribute(ROLE_ATTRIBUTE, role);
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            unauthorized(response, "invalid or expired JWT token");
        }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse(HttpServletResponse.SC_UNAUTHORIZED, message));
    }
}
