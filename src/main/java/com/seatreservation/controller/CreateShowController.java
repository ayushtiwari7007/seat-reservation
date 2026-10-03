package com.seatreservation.controller;

import com.seatreservation.auth.JwtAuthenticationFilter;
import com.seatreservation.exception.ForbiddenException;
import com.seatreservation.dto.request.CreateShowRequest;
import com.seatreservation.dto.response.CreateShowResponse;
import com.seatreservation.service.CreateShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.net.URI;

@RestController
@RequestMapping("/shows")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CreateShowController {

    private final CreateShowService createShowService;

    @PostMapping
    public ResponseEntity<CreateShowResponse> create(
            @RequestAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE) String role,
            @Valid @RequestBody CreateShowRequest request) {

        if (!JwtAuthenticationFilter.ADMIN_ROLE.equals(role)) {
            throw new ForbiddenException("admin role is required to create a show");
        }

        CreateShowResponse createShowResponse = createShowService.create(request);
        return ResponseEntity.created(URI.create("/shows/" + createShowResponse.id())).body(createShowResponse);
    }
}
