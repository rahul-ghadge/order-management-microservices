package com.orderms.service.impl;

import com.orderms.dto.request.LoginRequest;
import com.orderms.dto.request.RegisterRequest;
import com.orderms.dto.response.AuthResponse;
import com.orderms.dto.response.UserResponse;
import com.orderms.entity.Role;
import com.orderms.entity.User;
import com.orderms.exception.DuplicateResourceException;
import com.orderms.exception.ResourceNotFoundException;
import com.orderms.repository.UserRepository;
import com.orderms.security.JwtService;
import com.orderms.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Full implementation of {@link UserService}.
 * User profiles are cached in Redis; cache is evicted on update/delete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository        userRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtService            jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    // ── Register ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        log.debug("register() – email={}", req.getEmail());

        if (userRepository.existsByEmail(req.getEmail()))
            throw new DuplicateResourceException("Email already registered: " + req.getEmail());
        if (userRepository.existsByUsername(req.getUsername()))
            throw new DuplicateResourceException("Username already taken: " + req.getUsername());

        User user = User.builder()
                .username(req.getUsername())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .firstName(req.getFirstName())
                .lastName(req.getLastName())
                .phone(req.getPhone())
                .role(Role.ROLE_USER)
                .enabled(true)
                .accountNonLocked(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        return buildAuthResponse(accessToken, refreshToken, user);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Override
    public AuthResponse login(LoginRequest req) {
        log.debug("login() – usernameOrEmail={}", req.getUsernameOrEmail());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            req.getUsernameOrEmail(), req.getPassword()));
        } catch (BadCredentialsException ex) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = userRepository.findByEmailOrUsername(req.getUsernameOrEmail())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + req.getUsernameOrEmail()));

        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);

        log.info("User logged in: id={}", user.getId());
        return buildAuthResponse(accessToken, refreshToken, user);
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        String username = jwtService.extractUsername(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        if (!jwtService.isTokenValid(refreshToken, user))
            throw new IllegalArgumentException("Refresh token is invalid or expired.");

        String newAccessToken = jwtService.generateAccessToken(user);
        return buildAuthResponse(newAccessToken, refreshToken, user);
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Override
    public void logout(String accessToken) {
        jwtService.blacklistToken(accessToken);
        log.info("Token blacklisted – user logged out");
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Override
    @Cacheable(value = "users", key = "#id")
    public UserResponse getUserById(String id) {
        return toResponse(findUserById(id));
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
        return toResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(this::toResponse).toList();
    }

    // ── Update / Delete ───────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public UserResponse updateUser(String id, RegisterRequest req) {
        User user = findUserById(id);
        if (req.getFirstName() != null) user.setFirstName(req.getFirstName());
        if (req.getLastName()  != null) user.setLastName(req.getLastName());
        if (req.getPhone()     != null) user.setPhone(req.getPhone());
        if (req.getPassword()  != null)
            user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        return toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    @CacheEvict(value = "users", key = "#id")
    public void deleteUser(String id) {
        userRepository.delete(findUserById(id));
        log.info("User deleted: id={}", id);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User findUserById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: id=" + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .enabled(user.isEnabled())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInMs(accessTokenExpiryMs)
                .user(toResponse(user))
                .build();
    }
}
