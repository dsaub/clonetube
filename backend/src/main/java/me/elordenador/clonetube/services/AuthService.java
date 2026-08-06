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

/**
 * Servicio encargado de la lógica de negocio relacionada con la autenticación y
 * la gestión de usuarios del sistema.
 *
 * <p>Proporciona operaciones de registro, inicio de sesión, cambio y restablecimiento de
 * contraseña, verificación de cuenta, consulta de datos del usuario autenticado y un
 * endpoint de prueba para el envío de correos electrónicos.</p>
 *
 * <p>La emisión de tokens JWT se realiza mediante {@link JwtEncoder} y el envío de correos
 * se lleva a cabo publicando mensajes de tipo {@link EmailMessage} en una cola SQS.</p>
 *
 * @author me.elordenador
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SqsClient sqsClient;
    private final EmailTemplateService emailTemplateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${aws.sqs.queue-url}")
    private String sqsQueueUrl;
    @Value("${domain}")
    private String domain;

    /**
     * Registra un nuevo usuario en el sistema.
     *
     * <p>Valida que username, password y email sean obligatorios y no superen las longitudes
     * máximas permitidas. La contraseña se codifica con el {@link PasswordEncoder} y se genera
     * un código de verificación. Se publica un email de verificación en la cola SQS y, si todo
     * es correcto, el usuario se persiste en la base de datos.</p>
     *
     * @param body datos del usuario a registrar (username, password, email y full_name opcional)
     * @return mapa con el estado {@code pending_verification}
     * @throws ResponseStatusException si faltan campos obligatorios, se excede la longitud,
     *         falla el envío del email o el username/email ya existen
     */
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
        String verifyUrl = domain + "/verify/" + user.getVerifyCode();
        EmailMessage verMessage = new EmailMessage(
                UUID.randomUUID().toString(),
                user.getEmail(),
                "Verifica tu cuenta en clonetube",
                "Entra en el siguiente enlace para verificar tu cuenta: " + verifyUrl,
                emailTemplateService.verification(username, verifyUrl)
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

    /**
     * Inicia sesión con las credenciales proporcionadas.
     *
     * <p>Comprueba que username y password sean obligatorios, que el usuario exista, que la
     * contraseña coincida con la almacenada y que la cuenta esté verificada. Si todo es
     * correcto, devuelve un token JWT.</p>
     *
     * @param body credenciales de acceso (username y password)
     * @return token JWT de acceso con tipo {@code bearer}
     * @throws ResponseStatusException si las credenciales son inválidas o la cuenta no está verificada
     */
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

    /**
     * Genera un token JWT para un usuario con validez de 24 horas.
     *
     * <p>Incluye el username como subject y la versión de contraseña como claim
     * {@code pwd_ver} para invalidar tokens antiguos tras un cambio de contraseña.</p>
     *
     * @param user usuario para el que se emite el token
     * @return valor del token JWT codificado
     */
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

    /**
     * Cambia la contraseña de un usuario autenticado.
     *
     * <p>Verifica la contraseña antigua antes de actualizarla e incrementa la versión de
     * contraseña para invalidar los tokens previamente emitidos.</p>
     *
     * @param username nombre de usuario autenticado
     * @param body     contraseña antigua y nueva
     * @return nuevo token JWT emitido tras el cambio
     * @throws ResourceNotFoundException si el usuario no existe
     * @throws ResponseStatusException   si la contraseña antigua no coincide
     */
    public TokenDTO changePassword(String username, ChangePasswordRequestDTO body) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) throw new ResourceNotFoundException("Authed user not found!!");
        if (!passwordEncoder.matches(body.getOld_password(), user.getPassword_hash())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid old credentials");
        user.setPassword_hash(passwordEncoder.encode(body.getNew_password()));
        user.setPassword_version(user.getPassword_version() + 1);
        userRepository.save(user);
        return new TokenDTO(generateToken(user), "bearer");
    }

    /**
     * Solicita el restablecimiento de contraseña de un usuario.
     *
     * <p>Si existe un usuario con el email indicado, se genera un código de restablecimiento,
     * se almacena y se envía por email a través de la cola SQS.</p>
     *
     * @param body email del usuario
     * @return respuesta genérica {@code received} independientemente de si el email existe
     * @throws IllegalStateException si falla el envío del email por SQS
     */
    public ForgotPasswordResponseDTO forgotPassword(ForgotPasswordRequestDTO body) {
        User user = userRepository.findByEmail(body.getEmail()).orElse(null);
        if (user != null) {
            UUID codeU = UUID.randomUUID();
            String code = codeU.toString();
            user.setPassword_reset_token_hash(code);
            user.setPassword_reset_expires_at(Instant.now().plus(1, ChronoUnit.HOURS).toString());

            String resetUrl = domain + "/reset-password/" + code;
            EmailMessage emailMessage = new EmailMessage(
                    UUID.randomUUID().toString(),
                    user.getEmail(),
                    "Restablece tu contraseña en clonetube",
                    "Pulsa el siguiente enlace para restablecer tu contraseña: " + resetUrl,
                    emailTemplateService.passwordReset(user.getUsername(), resetUrl)
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

    /**
     * Restablece la contraseña de un usuario a partir de un código.
     *
     * <p>Actualmente no está implementado.</p>
     *
     * @param body código y nueva contraseña
     * @return mensaje de confirmación
     * @throws ResponseStatusException con estado {@code NOT_IMPLEMENTED}
     */
    public MessageDTO resetPassword(ResetPasswordRequestDTO body) {
        if (body.getToken() == null || body.getToken().isBlank()
                || body.getNew_password() == null || body.getNew_password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token and new password are required");
        }
        if (body.getNew_password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password too long");
        }

        User user = userRepository.findByPassword_reset_token_hash(body.getToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token"));

        if (user.getPassword_reset_expires_at() != null) {
            try {
                Instant expiresAt = Instant.parse(user.getPassword_reset_expires_at());
                if (expiresAt.isBefore(Instant.now())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset token expired");
                }
            } catch (java.time.format.DateTimeParseException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid reset token");
            }
        }

        user.setPassword_hash(passwordEncoder.encode(body.getNew_password()));
        user.setPassword_version(user.getPassword_version() + 1);
        user.setPassword_reset_token_hash(null);
        user.setPassword_reset_expires_at(null);
        userRepository.save(user);
        return new MessageDTO("success");
    }

    /**
     * Devuelve los datos del usuario autenticado.
     *
     * @param username nombre de usuario autenticado
     * @return DTO con los datos públicos del usuario
     * @throws ResponseStatusException si el usuario no existe
     */
    public UserDTO me(String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid user");
        return new UserDTO(user.getId(), user.getUsername(), user.getFull_name(), user.getEmail(), user.getIs_admin());
    }

    /**
     * Endpoint de prueba para el envío de un email.
     *
     * <p>Actualmente no está implementado.</p>
     *
     * @param email dirección de destino del email de prueba
     * @return mapa con el resultado de la operación
     * @throws ResponseStatusException con estado {@code NOT_IMPLEMENTED}
     */
    public Map<String, Object> testMail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        }
        EmailMessage testMessage = new EmailMessage(
                UUID.randomUUID().toString(),
                email,
                "Correo de prueba — clonetube",
                "This is a test email from clonetube",
                emailTemplateService.testMail()
        );
        try {
            String json = objectMapper.writeValueAsString(testMessage);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .messageBody(json)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send test email via SQS", e);
        }
        return Map.of("status", "sent");
    }

    /**
     * Verifica la cuenta de un usuario mediante un código de verificación.
     *
     * <p>Si el código es válido, se elimina el código de verificación y la cuenta queda
     * habilitada para iniciar sesión.</p>
     *
     * @param code código de verificación recibido por email
     * @return mapa con el estado {@code success}
     * @throws ResponseStatusException si el código de verificación es inválido
     */
    public Map<String, Object> verify(String code) {
        User user = userRepository.findByVerifyCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid Verification Code"));
        user.setVerifyCode(null);
        userRepository.save(user);
        return Map.of("status", "success");
    }
}
