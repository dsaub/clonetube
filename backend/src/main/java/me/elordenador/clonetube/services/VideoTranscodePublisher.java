package me.elordenador.clonetube.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.elordenador.clonetube.models.VideoTranscodeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.UUID;

@Component
public class VideoTranscodePublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${aws.sqs.video-queue-url}")
    private String queueUrl;

    public VideoTranscodePublisher(SqsClient sqsClient) {
        this.sqsClient = sqsClient;
    }

    public void enqueue(Integer videoId, String sourceKey, String sourceEtag, String originalFilename) {
        VideoTranscodeMessage message = new VideoTranscodeMessage(
                "video.transcode",
                1,
                UUID.randomUUID().toString(),
                videoId,
                sourceKey,
                sourceEtag,
                originalFilename
        );
        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(objectMapper.writeValueAsString(message))
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo publicar el trabajo de transcodificación", e);
        }
    }
}
