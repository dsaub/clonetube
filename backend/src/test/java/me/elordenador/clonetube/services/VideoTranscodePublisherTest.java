package me.elordenador.clonetube.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VideoTranscodePublisherTest {

    @Mock
    SqsClient sqsClient;

    @Test
    void enqueue_publishes_versioned_job_to_video_queue() throws Exception {
        VideoTranscodePublisher publisher = new VideoTranscodePublisher(sqsClient);
        ReflectionTestUtils.setField(publisher, "queueUrl", "https://sqs.example/video");

        publisher.enqueue(42, "videos/abc.mp4", "source-etag", "pelicula.mov");

        ArgumentCaptor<SendMessageRequest> captor = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(sqsClient).sendMessage(captor.capture());
        assertEquals("https://sqs.example/video", captor.getValue().queueUrl());
        Map<?, ?> body = new ObjectMapper().readValue(captor.getValue().messageBody(), Map.class);
        assertEquals("video.transcode", body.get("type"));
        assertEquals(1, body.get("version"));
        assertEquals(42, body.get("video_id"));
        assertEquals("videos/abc.mp4", body.get("source_key"));
        assertEquals("source-etag", body.get("source_etag"));
        assertEquals("pelicula.mov", body.get("original_filename"));
    }
}
