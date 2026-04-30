package com.orderms.service;

import com.orderms.dto.request.LoginRequest;
import com.orderms.dto.request.RegisterRequest;
import com.orderms.dto.response.AuthResponse;
import com.orderms.dto.response.UserResponse;

import java.util.List;

/**
 * User management and authentication service contract.
 */
public interface UserService {

    AuthResponse   register(RegisterRequest request);
    AuthResponse   login(LoginRequest request);
    AuthResponse   refreshToken(String refreshToken);
    void           logout(String accessToken);
    UserResponse   getUserById(String id);
    UserResponse   getUserByEmail(String email);
    List<UserResponse> getAllUsers();
    UserResponse   updateUser(String id, RegisterRequest request);
    void           deleteUser(String id);
}
