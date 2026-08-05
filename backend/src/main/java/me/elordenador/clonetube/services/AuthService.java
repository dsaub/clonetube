package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.exceptions.ResourceNotFoundException;
import me.elordenador.clonetube.models.EmailMessage;
import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${aws.sqs.queue-url}")
    private String sqsQueueUrl;
    @Value("${domain}")
    private String domain;

    public Map<String, Object> register(UserWithPasswordDTO body) {
        String username = body.getUsername();
        String password = body.getPassword();
        String email = body.getEmail();

        if (username == null || username.isBlank()
                || password == null || password.isBlank()
                || email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username, password and email are required");
        }
        if (username.length() > 50 || email.length() > 255
                || (body.getFull_name() != null && body.getFull_name().length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field length exceeded");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password too long");
        }

        User user = User.builder()
                .username(username)
                .password_hash(passwordEncoder.encode(password))
                .password_version(0)
                .email(email)
                .full_name(body.getFull_name())
                .points(0)
                .is_admin(false)
                .verifyCode(UUID.randomUUID().toString())
                .build();
        EmailMessage verMessage = new EmailMessage(
                UUID.randomUUID().toString(),
                user.getEmail(),
                "Verificación de cuenta",
                "Entra en el siguiente enlace para verificar tu cuenta: " + domain + "/verify/" + user.getVerifyCode()
        );
        try {
            String json = objectMapper.writeValueAsString(verMessage);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .messageBody(json)
                    .build());
        } catch (Exception e) {
            System.err.println("==== ERROR ====");
            e.printStackTrace();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error, check the logs for more details");
        }

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already taken");
        }

        return Map.of("status", "pending_verification");
    }

    public TokenDTO login(LoginRequestDTO body) {
        if (body.getUsername() == null || body.getUsername().isBlank()
                || body.getPassword() == null || body.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        User user = userRepository.findByUsername(body.getUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (!passwordEncoder.matches(body.getPassword(), user.getPassword_hash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        if (user.getVerifyCode() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This account needs to be verified, check your email for more info");
        }

        return new TokenDTO(generateToken(user), "bearer");
    }

    private String generateToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("self")
                .issuedAt(now)
                .expiresAt(now.plus(24, ChronoUnit.HOURS))
                .subject(user.getUsername())
                .claim("pwd_ver", user.getPassword_version())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public TokenDTO changePassword(String username, ChangePasswordRequestDTO body) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) throw new ResourceNotFoundException("Authed user not found!!");
        if (!passwordEncoder.matches(body.getOld_password(), user.getPassword_hash())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid old credentials");
        user.setPassword_hash(passwordEncoder.encode(body.getNew_password()));
        user.setPassword_version(user.getPassword_version() + 1);
        userRepository.save(user);
        return new TokenDTO(generateToken(user), "bearer");
    }

    public ForgotPasswordResponseDTO forgotPassword(ForgotPasswordRequestDTO body) {
        User user = userRepository.findByEmail(body.getEmail()).orElse(null);
        if (user != null) {
            UUID codeU = UUID.randomUUID();
            String code = codeU.toString();
            user.setPassword_reset_token_hash(code);

            EmailMessage emailMessage = new EmailMessage(
                    UUID.randomUUID().toString(),
                    user.getEmail(),
                    "Password reset",
                    "Your password reset code is: " + code
            );

            userRepository.save(user);

            try {
                String json = objectMapper.writeValueAsString(emailMessage);
                sqsClient.sendMessage(SendMessageRequest.builder()
                        .queueUrl(sqsQueueUrl)
                        .messageBody(json)
                        .build());
            } catch (Exception e) {
                throw new IllegalStateException("Failed to send password reset email via SQS", e);
            }
        }
        return new ForgotPasswordResponseDTO("received");
    }

    public MessageDTO resetPassword(ResetPasswordRequestDTO body) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public UserDTO me(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid user");
        return new UserDTO(user.getId(), user.getUsername(), user.getFull_name(), user.getEmail(), user.getIs_admin());
    }

    public Map<String, Object> testMail(String email) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public Map<String, Object> verify(String code) {
        User user = userRepository.findByVerifyCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Verification Code"));
        user.setVerifyCode(null);
        userRepository.save(user);
        return Map.of("status", "success");
    }
}
