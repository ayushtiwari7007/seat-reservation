package com.seatreservation.controller;

import com.seatreservation.auth.JwtAuthenticationFilter;
import com.seatreservation.auth.JwtService;
import com.seatreservation.exception.BadRequestException;
import com.seatreservation.dto.request.TokenRequest;
import com.seatreservation.dto.response.TokenResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    /**
     * Minimal token issuer for this assignment. The current project has no
     * users/password store, so the caller supplies the user id and optional role.
     * In a production system, credentials would be authenticated before issuing JWT.
     */
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> token(@Valid @RequestBody TokenRequest request) {
        String userId = request.getUserId().trim();
        String role = request.getRole() == null || request.getRole().isBlank()
                ? "USER"
                : request.getRole().trim().toUpperCase();

        if (!"USER".equals(role) && !JwtAuthenticationFilter.ADMIN_ROLE.equals(role)) {
            throw new BadRequestException("role must be USER or ADMIN");
        }

        String token = jwtService.generateToken(userId, role);
        return ResponseEntity.ok(new TokenResponse(token, "Bearer", userId, role));
    }
}
