package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.services.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody UserWithPasswordDTO body) {
        return authService.register(body);
    }

    @PostMapping("/login")
    public TokenDTO login(@RequestBody LoginRequestDTO body) {
        return authService.login(body);
    }

    @PostMapping("/change-password")
    @RequireAuth
    public TokenDTO changePassword(Authentication auth, @RequestBody ChangePasswordRequestDTO body) {
        return authService.changePassword(auth.getName(), body);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponseDTO forgotPassword(@RequestBody ForgotPasswordRequestDTO body) {
        return authService.forgotPassword(body);
    }

    @PostMapping("/reset-password")
    public MessageDTO resetPassword(@RequestBody ResetPasswordRequestDTO body) {
        return authService.resetPassword(body);
    }

    @GetMapping("/me")
    @RequireAuth
    public UserDTO me(Authentication authentication) {
        return authService.me(authentication.getName());
    }

    @GetMapping("/test_mail")
    public Map<String, Object> testMail(@RequestParam String email) {
        return authService.testMail(email);
    }

    @GetMapping("/verify/{code}")
    public Map<String, Object> verify(@PathVariable String code) {
        return authService.verify(code);
    }
}
