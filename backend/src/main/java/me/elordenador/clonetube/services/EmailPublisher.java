package me.elordenador.clonetube.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.models.EmailMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;

/**
 * Publica mensajes de correo en la cola SQS que consume el worker.
 */
@Component
@RequiredArgsConstructor
public class EmailPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.sqs.queue-url}")
    private String sqsQueueUrl;

    public void send(String to, String subject, String body, String bodyHtml) {
        EmailMessage message = new EmailMessage(
                UUID.randomUUID().toString(),
                to,
                subject,
                body,
                bodyHtml
        );
        try {
            String json = objectMapper.writeValueAsString(message);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .messageBody(json)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish email to SQS", e);
        }
    }
}
