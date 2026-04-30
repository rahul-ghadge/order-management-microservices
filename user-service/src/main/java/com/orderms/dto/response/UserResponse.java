package com.orderms.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserResponse {
    private String        id;
    private String        username;
    private String        email;
    private String        firstName;
    private String        lastName;
    private String        phone;
    private String        role;
    private boolean       enabled;
    private LocalDateTime createdAt;
}
