package me.elordenador.clonetube.services;

import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.enums.VisibilityEnum;
import me.elordenador.clonetube.models.MultipartUpload;
import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoServiceTest {

    @Mock VideoRepository videoRepository;
    @Mock MultipartUploadRepository multipartUploadRepository;
    @Mock UserFollowsUserRepository userFollowsUserRepository;
    @Mock UserLikesVideoRepository userLikesVideoRepository;
    @Mock UserCanViewVideoRepository userCanViewVideoRepository;
    @Mock UserRepository userRepository;
    @Mock S3Client s3Client;
    @Mock VideoTranscodePublisher videoTranscodePublisher;
    @Mock JwtDecoder jwtDecoder;

    VideoService service;

    private final User author = User.builder().id(1).username("alice").full_name("Alice").password_version(0).build();

    @BeforeEach
    void setUp() {
        service = new VideoService(videoRepository, multipartUploadRepository, userFollowsUserRepository,
                userLikesVideoRepository, userCanViewVideoRepository, userRepository,
                s3Client, videoTranscodePublisher, jwtDecoder);
        ReflectionTestUtils.setField(service, "bucket", "test-bucket");
        ReflectionTestUtils.setField(service, "publicEndpoint", "");
    }

    private Video video(VisibilityEnum visibility, User owner) {
        Video video = new Video();
        video.setId(1);
        video.setFilename("videos/abc.mp4");
        video.setAuthor(owner);
        video.setVideo_name("Mi video");
        video.setVideo_desc("");
        video.setCreated_at(new Date());
        video.setIs_published(visibility == VisibilityEnum.PUBLIC);
        video.setVisibility(visibility);
        return video;
    }

    private MultipartUpload pendingUpload(String key) {
        MultipartUpload upload = new MultipartUpload();
        upload.setUpload_id("u1");
        upload.setUpload_key(key);
        upload.setOriginal_filename("pelicula.mp4");
        upload.setOwner(author);
        upload.setStatus("pending");
        return upload;
    }

    // ---------- start-multipart ----------

    @Test
    void startMultipart_rejects_invalid_extension() {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.startMultipart("alice", "documento.txt"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void startMultipart_creates_upload_in_s3_and_db() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(s3Client.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
                .thenReturn(CreateMultipartUploadResponse.builder().uploadId("u1").key("videos/x.mp4").build());

        StartMultipartResponseDTO response = service.startMultipart("alice", "mi-video.mp4");

        assertEquals("u1", response.getUploadId());
        assertEquals("mi-video.mp4", response.getOriginal_filename());
        ArgumentCaptor<MultipartUpload> captor = ArgumentCaptor.forClass(MultipartUpload.class);
        verify(multipartUploadRepository).save(captor.capture());
        assertEquals("pending", captor.getValue().getStatus());
        assertEquals(author.getId(), captor.getValue().getOwner().getId());
    }

    // ---------- upload-chunk ----------

    @Test
    void uploadChunk_rejects_empty_body() {
        when(multipartUploadRepository.findByUploadIdAndUploadKey("u1", "videos/abc.mp4"))
                .thenReturn(Optional.of(pendingUpload("videos/abc.mp4")));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.uploadChunk("alice", "videos/abc.mp4", "u1", 1, new byte[0]));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void uploadChunk_rejects_oversize_body() {
        when(multipartUploadRepository.findByUploadIdAndUploadKey("u1", "videos/abc.mp4"))
                .thenReturn(Optional.of(pendingUpload("videos/abc.mp4")));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.uploadChunk("alice", "videos/abc.mp4", "u1", 1, new byte[32 * 1024 * 1024 + 1]));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, e.getStatusCode());
    }

    @Test
    void uploadChunk_forwards_bytes_and_returns_etag() {
        when(multipartUploadRepository.findByUploadIdAndUploadKey("u1", "videos/abc.mp4"))
                .thenReturn(Optional.of(pendingUpload("videos/abc.mp4")));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(s3Client.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
                .thenReturn(UploadPartResponse.builder().eTag("\"etag1\"").build());

        PartInfoDTO result = service.uploadChunk("alice", "videos/abc.mp4", "u1", 2, new byte[]{1, 2, 3});

        assertEquals(2, result.getPartNumber());
        assertEquals("\"etag1\"", result.getETag());
    }

    // ---------- complete-multipart ----------

    @Test
    void completeMultipart_sorts_parts_creates_video_and_queues_transcode() {
        when(multipartUploadRepository.findByUploadIdAndUploadKey("u1", "videos/abc.mp4"))
                .thenReturn(Optional.of(pendingUpload("videos/abc.mp4")));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder()
                        .location("http://s3/abc.mp4").eTag("source-etag").build());

        CompleteMultipartRequestDTO body = new CompleteMultipartRequestDTO(
                "videos/abc.mp4", "u1",
                List.of(new PartInfoDTO(2, "\"e2\""), new PartInfoDTO(1, "\"e1\"")));
        CompleteMultipartResponseDTO result = service.completeMultipart("alice", body);

        assertEquals("success", result.getStatus());
        verify(videoTranscodePublisher).enqueue(null, "videos/abc.mp4", "source-etag", "pelicula.mp4");
        ArgumentCaptor<CompleteMultipartUploadRequest> s3Captor = ArgumentCaptor.forClass(CompleteMultipartUploadRequest.class);
        verify(s3Client).completeMultipartUpload(s3Captor.capture());
        assertEquals(List.of(1, 2), s3Captor.getValue().multipartUpload().parts().stream()
                .map(CompletedPart::partNumber).toList());
        ArgumentCaptor<Video> videoCaptor = ArgumentCaptor.forClass(Video.class);
        verify(videoRepository).save(videoCaptor.capture());
        assertEquals(VisibilityEnum.PUBLIC, videoCaptor.getValue().getVisibility());
        assertEquals("pelicula.mp4", videoCaptor.getValue().getVideo_name());
    }

    @Test
    void completeMultipart_keeps_pending_job_when_sqs_is_unavailable() {
        MultipartUpload upload = pendingUpload("videos/abc.mp4");
        when(multipartUploadRepository.findByUploadIdAndUploadKey("u1", "videos/abc.mp4"))
                .thenReturn(Optional.of(upload));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(s3Client.completeMultipartUpload(any(CompleteMultipartUploadRequest.class)))
                .thenReturn(CompleteMultipartUploadResponse.builder().eTag("source-etag").build());
        doThrow(new IllegalStateException("SQS unavailable")).when(videoTranscodePublisher)
                .enqueue(any(), any(), any(), any());

        CompleteMultipartResponseDTO result = service.completeMultipart("alice", new CompleteMultipartRequestDTO(
                "videos/abc.mp4", "u1", List.of(new PartInfoDTO(1, "etag"))));

        assertEquals("success", result.getStatus());
        assertEquals("transcode_pending", upload.getStatus());
    }

    // ---------- acceso ----------

    @Test
    void detail_private_only_author_or_allowed() {
        Video privateVideo = video(VisibilityEnum.PRIVATE, author);
        when(videoRepository.findById(1)).thenReturn(Optional.of(privateVideo));

        assertThrows(ResponseStatusException.class, () -> service.detail(null, 1));

        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(
                User.builder().id(2).username("bob").password_version(0).build()));
        assertThrows(ResponseStatusException.class, () -> service.detail(anyAuth("bob"), 1));

        when(userCanViewVideoRepository.existsByUserIdAndVideoId(2, 1)).thenReturn(true);
        VideoDetailDTO detail = service.detail(anyAuth("bob"), 1);
        assertEquals("private", detail.getVisibility());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        assertEquals("private", service.detail(anyAuth("alice"), 1).getVisibility());
    }

    private org.springframework.security.core.Authentication anyAuth(String username) {
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null, List.of());
    }

    // ---------- stream-url ----------

    @Test
    void streamUrl_public_omits_token() {
        when(videoRepository.findById(1))
                .thenReturn(Optional.of(video(VisibilityEnum.PUBLIC, author)));

        StreamUrlResponseDTO result = service.streamUrl(null, "jwt-secreto", 1);

        assertFalse(result.getUrl().contains("token="));
        assertTrue(result.getUrl().startsWith("/api/v1/video/stream?"));
    }

    @Test
    void streamUrl_public_uses_configured_public_endpoint() {
        ReflectionTestUtils.setField(service, "publicEndpoint", " https://cdn.example.com/media/ ");
        when(videoRepository.findById(1))
                .thenReturn(Optional.of(video(VisibilityEnum.PUBLIC, author)));

        StreamUrlResponseDTO result = service.streamUrl(null, null, 1);

        assertEquals("https://cdn.example.com/media/videos/abc.mp4", result.getUrl());
    }

    @Test
    void streamUrl_private_includes_token() {
        ReflectionTestUtils.setField(service, "publicEndpoint", "https://cdn.example.com");
        when(videoRepository.findById(1))
                .thenReturn(Optional.of(video(VisibilityEnum.PRIVATE, author)));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));

        StreamUrlResponseDTO result = service.streamUrl(anyAuth("alice"), "jwt-secreto", 1);

        assertTrue(result.getUrl().contains("token=jwt-secreto"));
        assertTrue(result.getUrl().startsWith("/api/v1/video/stream?"));
    }

    // ---------- stream ----------

    @Test
    void stream_passes_range_and_returns_206() throws Exception {
        when(videoRepository.findByFilename("videos/abc.mp4"))
                .thenReturn(Optional.of(video(VisibilityEnum.PUBLIC, author)));
        GetObjectResponse objectResponse = GetObjectResponse.builder()
                .contentLength(100L).contentRange("bytes 0-99/100").contentType("video/mp4").build();
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(objectResponse, new ByteArrayInputStream(new byte[10])));

        ResponseEntity<StreamingResponseBody> result = service.stream(null, "videos/abc.mp4", null, "bytes=0-99");

        assertEquals(HttpStatus.PARTIAL_CONTENT, result.getStatusCode());
        assertEquals("bytes", result.getHeaders().getFirst("Accept-Ranges"));
        assertEquals("bytes 0-99/100", result.getHeaders().getFirst("Content-Range"));
        assertNotNull(result.getBody());
    }

    @Test
    void stream_returns_200_without_range() throws Exception {
        when(videoRepository.findByFilename("videos/abc.mp4"))
                .thenReturn(Optional.of(video(VisibilityEnum.PUBLIC, author)));
        GetObjectResponse objectResponse = GetObjectResponse.builder()
                .contentLength(100L).contentType("video/mp4").build();
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenReturn(new ResponseInputStream<>(objectResponse, new ByteArrayInputStream(new byte[10])));

        ResponseEntity<StreamingResponseBody> result = service.stream(null, "videos/abc.mp4", null, null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNull(result.getHeaders().getFirst("Content-Range"));
    }

    // ---------- update ----------

    @Test
    void updateVideo_rejects_invalid_visibility() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(videoRepository.findById(1)).thenReturn(Optional.of(video(VisibilityEnum.PUBLIC, author)));

        VideoUpdateDTO body = new VideoUpdateDTO("Título", "", "secreto", List.of());
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.updateVideo("alice", "1", body));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
    }

    @Test
    void updateVideo_rejects_unknown_allowed_users() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(author));
        when(videoRepository.findById(1)).thenReturn(Optional.of(video(VisibilityEnum.PUBLIC, author)));
        when(userRepository.findAllByUsernameIn(anyCollection())).thenReturn(List.of());

        VideoUpdateDTO body = new VideoUpdateDTO("Título", "", "private", List.of("nadie"));
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.updateVideo("alice", "1", body));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatusCode());
    }

    @Test
    void updateVideo_only_author_can_edit() {
        when(videoRepository.findById(1)).thenReturn(Optional.of(video(VisibilityEnum.PUBLIC, author)));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(
                User.builder().id(2).username("bob").password_version(0).build()));

        VideoUpdateDTO body = new VideoUpdateDTO("Título", "", "public", List.of());
        assertThrows(ResponseStatusException.class,
                () -> service.updateVideo("bob", "1", body));
    }
}
