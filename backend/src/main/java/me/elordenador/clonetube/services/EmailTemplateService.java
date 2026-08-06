package me.elordenador.clonetube.services;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;

/**
 * Servicio encargado de cargar y rellenar las plantillas HTML de los correos electrónicos.
 *
 * <p>Las plantillas se encuentran en {@code src/main/resources/templates/email/} y se cargan
 * desde el classpath (incluidas dentro del jar empaquetado). Soportan los marcadores
 * {@code {{username}}}, {@code {{url}}}, {@code {{content}}} y {@code {{year}}}.</p>
 */
@Service
public class EmailTemplateService {

    private static final String BASE_PATH = "templates/email/";

    /**
     * Lee el contenido de una plantilla del classpath.
     */
    private String load(String name) {
        try (InputStream in = new ClassPathResource(BASE_PATH + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo cargar la plantilla de correo: " + name, e);
        }
    }

    /**
     * Envuelve un fragmento en el esqueleto común del email.
     */
    private String render(String content) {
        return load("layout.html")
                .replace("{{content}}", content)
                .replace("{{year}}", String.valueOf(Year.now().getValue()));
    }

    /**
     * Plantilla para la verificación de cuenta.
     */
    public String verification(String username, String url) {
        String content = load("verification.html")
                .replace("{{username}}", username)
                .replace("{{url}}", url);
        return render(content);
    }

    /**
     * Plantilla para el restablecimiento de contraseña.
     */
    public String passwordReset(String username, String url) {
        String content = load("password_reset.html")
                .replace("{{username}}", username)
                .replace("{{url}}", url);
        return render(content);
    }

    /**
     * Plantilla para el correo de prueba.
     */
    public String testMail() {
        return render(load("test_mail.html"));
    }

    /**
     * Plantilla para notificar una donación recibida.
     */
    public String donation(String recipient, String donor, int points, String message) {
        String messageBlock = (message != null && !message.isBlank())
                ? "<p style=\"margin:0 0 8px 0;color:#9a9ab8;font-size:14px;line-height:1.6;\">&laquo;"
                + message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "&raquo;</p>"
                : "";
        String content = load("donation.html")
                .replace("{{recipient}}", recipient)
                .replace("{{donor}}", donor)
                .replace("{{points}}", String.valueOf(points))
                .replace("{{messageBlock}}", messageBlock);
        return render(content);
    }
}
