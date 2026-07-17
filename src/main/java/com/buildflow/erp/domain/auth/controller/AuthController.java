package com.buildflow.erp.domain.auth.controller;

import com.buildflow.erp.common.dto.ApiResponse;
import com.buildflow.erp.domain.auth.dto.request.ChangeEmailRequest;
import com.buildflow.erp.domain.auth.dto.request.ChangePasswordRequest;
import com.buildflow.erp.domain.auth.dto.request.DeleteAccountRequest;
import com.buildflow.erp.domain.auth.dto.request.LoginRequest;
import com.buildflow.erp.domain.auth.dto.request.RegisterRequest;
import com.buildflow.erp.domain.auth.dto.response.AuthResponse;
import com.buildflow.erp.domain.auth.entity.User;
import com.buildflow.erp.domain.auth.service.AuthService;
import com.buildflow.erp.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<AuthResponse>> me(@AuthenticationPrincipal UserPrincipal principal) {
        User user = principal.getUser();
        return ResponseEntity.ok(ApiResponse.success(
                new AuthResponse(null, user.getEmail(), user.getRole().name())));
    }

    @PatchMapping("/me/email")
    public ResponseEntity<ApiResponse<AuthResponse>> changeEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangeEmailRequest request) {
        AuthResponse response = authService.changeEmail(principal.getUser().getEmail(), request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getUser().getEmail(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Password updated"));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeleteAccountRequest request) {
        authService.deleteAccount(principal.getUser().getEmail(), request);
        return ResponseEntity.noContent().build();
    }
}