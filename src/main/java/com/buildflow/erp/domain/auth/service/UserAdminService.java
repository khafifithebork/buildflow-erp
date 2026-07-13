package com.buildflow.erp.domain.auth.service;

import com.buildflow.erp.domain.auth.dto.response.PendingUserResponse;
import com.buildflow.erp.domain.auth.entity.Role;
import com.buildflow.erp.domain.auth.entity.User;
import com.buildflow.erp.domain.auth.entity.UserStatus;
import com.buildflow.erp.domain.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;

    public List<PendingUserResponse> listApprovableFor(Role approverRole) {
        return userRepository.findByStatus(UserStatus.PENDING).stream()
                .filter(u -> approverRole.canApprove(u.getRole()))
                .map(u -> new PendingUserResponse(u.getId(), u.getEmail(), u.getRole().name(), u.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void approve(UUID userId, Role approverRole) {
        User user = getPendingOrThrow(userId, approverRole);
        user.setStatus(UserStatus.APPROVED);
        userRepository.save(user);
    }

    @Transactional
    public void reject(UUID userId, Role approverRole) {
        User user = getPendingOrThrow(userId, approverRole);
        user.setStatus(UserStatus.REJECTED);
        userRepository.save(user);
    }

    private User getPendingOrThrow(UUID userId, Role approverRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() != UserStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This user is not pending approval");
        }
        if (!approverRole.canApprove(user.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot approve this role");
        }
        return user;
    }
}