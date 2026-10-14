package com.travelplatform.userauth.api;

import com.travelplatform.common.api.ApiResponse;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.userauth.domain.JwtService;
import com.travelplatform.userauth.domain.PasswordHasher;
import com.travelplatform.userauth.domain.User;
import com.travelplatform.userauth.domain.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;

    public AuthController(UserRepository userRepository, PasswordHasher passwordHasher, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "email already registered");
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setPasswordHash(passwordHasher.hash(request.password()));
        user.setCreatedAt(Instant.now());
        User saved = userRepository.save(user);
        return ApiResponse.success(new AuthResponse(jwtService.issue(saved), saved.getId()));
    }

    @PostMapping("/auth/login")
    public ApiResponse<AuthResponse> login(@RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "invalid credentials"));
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "invalid credentials");
        }
        return ApiResponse.success(new AuthResponse(jwtService.issue(user), user.getId()));
    }

    @GetMapping("/users/me")
    public ApiResponse<UserResponse> me(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        String resolvedId = resolveUserId(userId, authorization);
        User user = userRepository.findById(resolvedId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "user not found"));
        return ApiResponse.success(new UserResponse(user.getId(), user.getEmail(), user.getPhone()));
    }

    private String resolveUserId(String headerUserId, String authorization) {
        if (headerUserId != null && !headerUserId.isBlank()) {
            return headerUserId;
        }
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return jwtService.parse(authorization.substring(7)).getPayload().getSubject();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED, "missing credentials");
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            String phone) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, String userId) {
    }

    public record UserResponse(String id, String email, String phone) {
    }
}
