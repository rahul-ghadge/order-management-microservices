package com.orderms.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AuthResponse {
    private String       accessToken;
    private String       refreshToken;
    private String       tokenType;
    private long         expiresInMs;
    private UserResponse user;
}
