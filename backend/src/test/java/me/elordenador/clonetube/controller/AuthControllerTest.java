package me.elordenador.clonetube.controller;

import me.elordenador.clonetube.dtos.LoginRequestDTO;
import me.elordenador.clonetube.dtos.TokenDTO;
import me.elordenador.clonetube.dtos.UserWithPasswordDTO;
import me.elordenador.clonetube.services.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private me.elordenador.clonetube.repository.UserRepository userRepository;

    @Test
    void register_returns_pending_status_on_success() throws Exception {
        when(authService.register(any(UserWithPasswordDTO.class)))
                .thenReturn(Map.of("status","pending_confirmation"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret123","email":"alice@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_confirmation"));
    }

    @Test
    void register_rejects_missing_email() throws Exception {
        when(authService.register(any(UserWithPasswordDTO.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Username, password and email are required"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret123"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_rejects_long_username() throws Exception {
        when(authService.register(any(UserWithPasswordDTO.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Field length exceeded"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"secret123","email":"a@b.com"}
                                """.formatted("a".repeat(51))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_rejects_duplicate_username() throws Exception {
        when(authService.register(any(UserWithPasswordDTO.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Username or email already taken"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret123","email":"a@b.com"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void login_returns_token_on_success() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenReturn(new TokenDTO("fake-jwt-token", "bearer"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("fake-jwt-token"))
                .andExpect(jsonPath("$.token_type").value("bearer"));
    }

    @Test
    void login_rejects_wrong_password() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_rejects_unknown_user() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "Invalid credentials"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nobody","password":"secret123"}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_rejects_missing_fields() throws Exception {
        when(authService.login(any(LoginRequestDTO.class)))
                .thenThrow(new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Username and password are required"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
