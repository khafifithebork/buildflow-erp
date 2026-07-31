package com.buildflow.erp.domain.auth.service;

import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.domain.auth.dto.request.ChangeEmailRequest;
import com.buildflow.erp.domain.auth.dto.request.ChangePasswordRequest;
import com.buildflow.erp.domain.auth.dto.request.DeleteAccountRequest;
import com.buildflow.erp.domain.auth.dto.request.LoginRequest;
import com.buildflow.erp.domain.auth.dto.request.RegisterRequest;
import com.buildflow.erp.domain.auth.dto.response.AuthResponse;
import com.buildflow.erp.domain.auth.entity.RevokedToken;
import com.buildflow.erp.domain.auth.entity.User;
import com.buildflow.erp.domain.auth.entity.UserStatus;
import com.buildflow.erp.domain.auth.repository.RevokedTokenRepository;
import com.buildflow.erp.domain.auth.repository.UserRepository;
import com.buildflow.erp.security.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RevokedTokenRepository revokedTokenRepository;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setStatus(request.role().requiresApproval() ? UserStatus.PENDING : UserStatus.APPROVED);

        userRepository.save(user);

        if (user.getStatus() == UserStatus.PENDING) {
            return new AuthResponse(null, user.getEmail(), user.getRole().name());
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (DisabledException ex) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Votre compte est en attente d'approbation");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be reloaded"));

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    @Override
    @Transactional
    public AuthResponse changeEmail(String currentEmail, ChangeEmailRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be reloaded"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }

        if (!request.newEmail().equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(request.newEmail())) {
            throw new ConflictException("An account with this email already exists");
        }

        user.setEmail(request.newEmail());
        userRepository.save(user);

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user.getEmail(), user.getRole().name());
    }

    @Override
    @Transactional
    public void changePassword(String currentEmail, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be reloaded"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void deleteAccount(String currentEmail, DeleteAccountRequest request) {
        User user = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user could not be reloaded"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }

        userRepository.delete(user);
    }

    @Override
    @Transactional
    public void logout(String token) {
        try {
            String jti = jwtService.extractJti(token);
            if (jti == null) {
                return;
            }
            // Opportunistic prune so the denylist doesn't grow without bound.
            revokedTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
            if (!revokedTokenRepository.existsByJti(jti)) {
                LocalDateTime expiresAt = LocalDateTime.ofInstant(
                        jwtService.extractExpiration(token).toInstant(), ZoneId.systemDefault());
                revokedTokenRepository.save(new RevokedToken(jti, expiresAt));
            }
        } catch (JwtException ex) {
            // Malformed/expired/forged token — nothing meaningful to revoke.
        }
    }
}
