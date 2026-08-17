package me.elordenador.clonetube.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateServiceTest {

    private final EmailTemplateService service = new EmailTemplateService();

    @Test
    void verificationRendersLayoutAndReplacesPlaceholders() {
        String html = service.verification("alice", "https://example.com/verify/token");

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("alice"));
        assertTrue(html.contains("href=\"https://example.com/verify/token\""));
        assertTrue(html.contains("background-color:#5147cc;color:#ffffff"));
        assertFalse(html.contains("{{"));
    }

    @Test
    void passwordResetPreservesEmailCompatibleButtonMarkup() {
        String html = service.passwordReset("bob", "https://example.com/reset/token");

        assertTrue(html.contains("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""));
        assertTrue(html.contains("background-color:#5147cc;color:#ffffff"));
        assertTrue(html.contains("href=\"https://example.com/reset/token\""));
    }

    @Test
    void donationEscapesOptionalMessage() {
        String html = service.donation("recipient", "donor", 25, "Gracias <script>& fin");

        assertTrue(html.contains("recipient"));
        assertTrue(html.contains("25 puntos"));
        assertTrue(html.contains("Gracias &lt;script&gt;&amp; fin"));
        assertFalse(html.contains("Gracias <script>"));
    }

    @Test
    void testMailRendersInsideSharedLayout() {
        String html = service.testMail();

        assertTrue(html.contains("Correo de prueba"));
        assertTrue(html.contains("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\""));
        assertFalse(html.contains("{{content}}"));
    }
}
