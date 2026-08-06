package me.elordenador.clonetube.models;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailMessageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesToExpectedJson() throws Exception {
        EmailMessage emailMessage = new EmailMessage(
                "message-id",
                "user@example.com",
                "Password reset",
                "Your password reset code is: reset-code"
        );

        String json = objectMapper.writeValueAsString(emailMessage);
        assertEquals(
                "{\"id\":\"message-id\",\"to\":\"user@example.com\",\"subject\":\"Password reset\",\"body\":\"Your password reset code is: reset-code\",\"body_html\":null}",
                json
        );
        assertTrue(emailMessage.body().contains("reset-code"));
    }
}
