//package com.orderms.service;
//
//import com.orderms.dto.request.LoginRequest;
//import com.orderms.dto.request.RegisterRequest;
//import com.orderms.dto.response.AuthResponse;
//import com.orderms.entity.Role;
//import com.orderms.entity.User;
//import com.orderms.exception.DuplicateResourceException;
//import com.orderms.exception.ResourceNotFoundException;
//import com.orderms.repository.UserRepository;
//import com.orderms.security.JwtService;
//import com.orderms.service.impl.UserServiceImpl;
//import org.junit.jupiter.api.*;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.*;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.security.authentication.AuthenticationManager;
//import org.springframework.security.authentication.BadCredentialsException;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.test.util.ReflectionTestUtils;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.*;
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//@DisplayName("UserServiceImpl – Unit Tests")
//class UserServiceImplTest {
//
//    @Mock private UserRepository        userRepository;
//    @Mock private PasswordEncoder       passwordEncoder;
//    @Mock private JwtService            jwtService;
//    @Mock private AuthenticationManager authenticationManager;
//
//    @InjectMocks
//    private UserServiceImpl userService;
//
//    @BeforeEach
//    void setup() {
//        ReflectionTestUtils.setField(userService, "accessTokenExpiryMs", 900000L);
//    }
//
//    private User sampleUser() {
//        return User.builder()
//                .id("user-uuid-1").username("johndoe")
//                .email("john@example.com").passwordHash("hashed")
//                .firstName("John").lastName("Doe")
//                .role(Role.ROLE_USER).enabled(true).build();
//    }
//
//    private RegisterRequest sampleRegisterRequest() {
//        RegisterRequest req = new RegisterRequest();
//        req.setUsername("johndoe");
//        req.setEmail("john@example.com");
//        req.setPassword("Password1");
//        req.setFirstName("John");
//        req.setLastName("Doe");
//        return req;
//    }
//
//    // ── register ──────────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("register() – success → returns AuthResponse with tokens")
//    void register_success() {
//        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
//        when(userRepository.existsByUsername("johndoe")).thenReturn(false);
//        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
//        when(userRepository.save(any(User.class))).thenReturn(sampleUser());
//        when(jwtService.generateAccessToken(any())).thenReturn("access-token");
//        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
//
//        AuthResponse result = userService.register(sampleRegisterRequest());
//
//        assertThat(result.getAccessToken()).isEqualTo("access-token");
//        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
//        assertThat(result.getUser().getEmail()).isEqualTo("john@example.com");
//        verify(userRepository).save(any(User.class));
//    }
//
//    @Test
//    @DisplayName("register() – duplicate email → throws DuplicateResourceException")
//    void register_duplicateEmail_throws() {
//        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);
//        assertThatThrownBy(() -> userService.register(sampleRegisterRequest()))
//                .isInstanceOf(DuplicateResourceException.class)
//                .hasMessageContaining("john@example.com");
//        verify(userRepository, never()).save(any());
//    }
//
//    @Test
//    @DisplayName("register() – duplicate username → throws DuplicateResourceException")
//    void register_duplicateUsername_throws() {
//        when(userRepository.existsByEmail(anyString())).thenReturn(false);
//        when(userRepository.existsByUsername("johndoe")).thenReturn(true);
//        assertThatThrownBy(() -> userService.register(sampleRegisterRequest()))
//                .isInstanceOf(DuplicateResourceException.class)
//                .hasMessageContaining("johndoe");
//    }
//
//    // ── login ─────────────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("login() – valid credentials → returns AuthResponse")
//    void login_success() {
//        LoginRequest req = new LoginRequest();
//        req.setUsernameOrEmail("john@example.com");
//        req.setPassword("Password1");
//
//        when(userRepository.findByEmailOrUsername("john@example.com")).thenReturn(Optional.of(sampleUser()));
//        when(jwtService.generateAccessToken(any())).thenReturn("access");
//        when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
//
//        AuthResponse result = userService.login(req);
//
//        assertThat(result.getAccessToken()).isEqualTo("access");
//        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
//    }
//
//    @Test
//    @DisplayName("login() – bad credentials → throws BadCredentialsException")
//    void login_badCredentials_throws() {
//        LoginRequest req = new LoginRequest();
//        req.setUsernameOrEmail("john@example.com");
//        req.setPassword("wrong");
//
//        doThrow(new BadCredentialsException("Bad credentials"))
//                .when(authenticationManager).authenticate(any());
//
//        assertThatThrownBy(() -> userService.login(req))
//                .isInstanceOf(BadCredentialsException.class);
//    }
//
//    // ── getUserById ───────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("getUserById() – found → returns UserResponse")
//    void getUserById_found() {
//        when(userRepository.findById("user-uuid-1")).thenReturn(Optional.of(sampleUser()));
//        var response = userService.getUserById("user-uuid-1");
//        assertThat(response.getId()).isEqualTo("user-uuid-1");
//        assertThat(response.getEmail()).isEqualTo("john@example.com");
//    }
//
//    @Test
//    @DisplayName("getUserById() – not found → throws ResourceNotFoundException")
//    void getUserById_notFound_throws() {
//        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
//        assertThatThrownBy(() -> userService.getUserById("ghost"))
//                .isInstanceOf(ResourceNotFoundException.class);
//    }
//
//    // ── getAllUsers ───────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("getAllUsers() – returns mapped list")
//    void getAllUsers_returnsList() {
//        when(userRepository.findAll()).thenReturn(List.of(sampleUser()));
//        var list = userService.getAllUsers();
//        assertThat(list).hasSize(1);
//        assertThat(list.get(0).getUsername()).isEqualTo("johndoe");
//    }
//
//    // ── logout ────────────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("logout() – delegates blacklisting to JwtService")
//    void logout_blacklistsToken() {
//        userService.logout("some-jwt-token");
//        verify(jwtService).blacklistToken("some-jwt-token");
//    }
//
//    // ── deleteUser ────────────────────────────────────────────────────────────
//
//    @Test
//    @DisplayName("deleteUser() – deletes when found")
//    void deleteUser_success() {
//        when(userRepository.findById("user-uuid-1")).thenReturn(Optional.of(sampleUser()));
//        userService.deleteUser("user-uuid-1");
//        verify(userRepository).delete(any(User.class));
//    }
//}
