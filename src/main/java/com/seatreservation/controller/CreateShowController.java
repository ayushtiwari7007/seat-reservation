package com.seatreservation.controller;

import com.seatreservation.auth.JwtAuthenticationFilter;
import com.seatreservation.exception.ForbiddenException;
import com.seatreservation.dto.request.CreateShowRequest;
import com.seatreservation.dto.response.CreateShowResponse;
import com.seatreservation.service.CreateShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CreateShowController {

    private final CreateShowService createShowService;

    @PostMapping
    public ResponseEntity<CreateShowResponse> create(
            @RequestAttribute(JwtAuthenticationFilter.ROLE_ATTRIBUTE) String role,
            @Valid @RequestBody CreateShowRequest request) {

        log.info("controller.create_show.request role={} seatCount={}",
                role, request.getSeats() == null ? 0 : request.getSeats().size());
        if (!JwtAuthenticationFilter.ADMIN_ROLE.equals(role)) {
            log.warn("controller.create_show.denied reason=admin_role_required role={}", role);
            throw new ForbiddenException("admin role is required to create a show");
        }

        CreateShowResponse createShowResponse = createShowService.create(request);
        return ResponseEntity.created(URI.create("/shows/" + createShowResponse.id())).body(createShowResponse);
    }
}
