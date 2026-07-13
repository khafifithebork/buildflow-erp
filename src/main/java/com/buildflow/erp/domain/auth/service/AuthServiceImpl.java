package com.buildflow.erp.domain.auth.service;

import com.buildflow.erp.common.exception.ConflictException;
import com.buildflow.erp.domain.auth.dto.request.LoginRequest;
import com.buildflow.erp.domain.auth.dto.request.RegisterRequest;
import com.buildflow.erp.domain.auth.dto.response.AuthResponse;
import com.buildflow.erp.domain.auth.entity.User;
import com.buildflow.erp.domain.auth.entity.UserStatus;
import com.buildflow.erp.domain.auth.repository.UserRepository;
import com.buildflow.erp.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

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
}
