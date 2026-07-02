package com.buildflow.erp.domain.auth.service;

import com.buildflow.erp.domain.auth.dto.request.LoginRequest;
import com.buildflow.erp.domain.auth.dto.request.RegisterRequest;
import com.buildflow.erp.domain.auth.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}