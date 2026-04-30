package com.orderms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderms.dto.request.LoginRequest;
import com.orderms.dto.request.RegisterRequest;
import com.orderms.dto.response.AuthResponse;
import com.orderms.dto.response.UserResponse;
import com.orderms.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@DisplayName("AuthController – MockMvc Tests")
class AuthControllerTest {

    @Autowired private MockMvc       mockMvc;
    @Autowired private ObjectMapper  objectMapper;

    @MockBean private UserService userService;

    // ── Stub helpers ──────────────────────────────────────────────────────────

    private AuthResponse stubAuthResponse() {
        UserResponse user = UserResponse.builder()
                .id("user-id-1").username("johndoe")
                .email("john@example.com").role("ROLE_USER")
                .enabled(true).createdAt(LocalDateTime.now()).build();
        return AuthResponse.builder()
                .accessToken("access-jwt").refreshToken("refresh-jwt")
                .tokenType("Bearer").expiresInMs(900000L).user(user).build();
    }

    private RegisterRequest validRegisterRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("johndoe");
        req.setEmail("john@example.com");
        req.setPassword("Password1");
        req.setFirstName("John");
        req.setLastName("Doe");
        return req;
    }

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/register – 201 with AuthResponse on valid request")
    void register_shouldReturn201() throws Exception {
        when(userService.register(any(RegisterRequest.class))).thenReturn(stubAuthResponse());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.email").value("john@example.com"));
    }

    @Test
    @DisplayName("POST /auth/register – 400 when email is invalid")
    void register_shouldReturn400_invalidEmail() throws Exception {
        RegisterRequest req = validRegisterRequest();
        req.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register – 400 when password is too weak")
    void register_shouldReturn400_weakPassword() throws Exception {
        RegisterRequest req = validRegisterRequest();
        req.setPassword("weak"); // no uppercase, no digit, < 8 chars

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/register – 400 when username is blank")
    void register_shouldReturn400_blankUsername() throws Exception {
        RegisterRequest req = validRegisterRequest();
        req.setUsername("");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login – 200 with AuthResponse on valid credentials")
    void login_shouldReturn200() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("john@example.com");
        req.setPassword("Password1");

        when(userService.login(any(LoginRequest.class))).thenReturn(stubAuthResponse());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-jwt"));
    }

    @Test
    @DisplayName("POST /auth/login – 400 when usernameOrEmail is blank")
    void login_shouldReturn400_blankUsername() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("");
        req.setPassword("Password1");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /auth/login – 400 when password is blank")
    void login_shouldReturn400_blankPassword() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsernameOrEmail("john@example.com");
        req.setPassword("");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /auth/logout – 200 when Authorization header is present")
    void logout_shouldReturn200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer some-jwt-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
