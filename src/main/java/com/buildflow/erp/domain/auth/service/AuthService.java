package com.buildflow.erp.domain.auth.service;

import com.buildflow.erp.domain.auth.dto.request.ChangeEmailRequest;
import com.buildflow.erp.domain.auth.dto.request.ChangePasswordRequest;
import com.buildflow.erp.domain.auth.dto.request.DeleteAccountRequest;
import com.buildflow.erp.domain.auth.dto.request.LoginRequest;
import com.buildflow.erp.domain.auth.dto.request.RegisterRequest;
import com.buildflow.erp.domain.auth.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    AuthResponse changeEmail(String currentEmail, ChangeEmailRequest request);
    void changePassword(String currentEmail, ChangePasswordRequest request);
    void deleteAccount(String currentEmail, DeleteAccountRequest request);
    void logout(String token);
}