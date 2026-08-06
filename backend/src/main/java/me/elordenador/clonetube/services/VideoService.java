package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.enums.VisibilityEnum;
import me.elordenador.clonetube.feed.FeedRanker;
import me.elordenador.clonetube.models.MultipartUpload;
import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.models.UserCanViewVideo;
import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class VideoService {

    private static final int MAX_CHUNK_BYTES = 32 * 1024 * 1024;
    private static final int STREAM_CHUNK_BYTES = 1024 * 1024;
    private static final int MAX_FEED_LIMIT = 200;

    private final VideoRepository videoRepository;
    private final MultipartUploadRepository multipartUploadRepository;
    private final UserFollowsUserRepository userFollowsUserRepository;
    private final UserLikesVideoRepository userLikesVideoRepository;
    private final UserCanViewVideoRepository userCanViewVideoRepository;
    private final UserRepository userRepository;
    private final S3Client s3Client;
    private final VideoProcessor videoProcessor;
    private final JwtDecoder jwtDecoder;

    @Value("${aws.s3.bucket}")
    private String bucket;

    // ---------- Subida multipart ----------

    @Transactional
    public StartMultipartResponseDTO startMultipart(String username, String original_filename) {
        if (!VideoProcessor.isValidVideoExtension(original_filename)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Formato de archivo no permitido: " + original_filename);
        }
        String videoKey = "videos/" + UUID.randomUUID().toString().replace("-", "") + ".mp4";
        try {
            CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                    CreateMultipartUploadRequest.builder().bucket(bucket).key(videoKey).build());
            MultipartUpload upload = new MultipartUpload();
            upload.setUpload_id(response.uploadId());
            upload.setUpload_key(videoKey);
            upload.setOriginal_filename(original_filename);
            upload.setOwner(requireUser(username));
            upload.setStatus("pending");
            upload.setCreated_at(Date.from(Instant.now()));
            multipartUploadRepository.save(upload);
            return new StartMultipartResponseDTO(response.uploadId(), videoKey, original_filename);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    public SignChunkResponseDTO signChunk(String filename, String upload_id, Integer chunk_number) {
        // ponytail: el frontend solo usa /upload-chunk (proxy); presigned se añade cuando la
        // subida por la API sea cuello de botella
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Transactional
    public PartInfoDTO uploadChunk(String username, String filename, String upload_id,
                                   Integer chunk_number, byte[] body) {
        findPendingUpload(username, filename, upload_id);
        if (body == null || body.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El fragmento está vacío");
        }
        if (body.length > MAX_CHUNK_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El fragmento supera el máximo de " + MAX_CHUNK_BYTES + " bytes");
        }
        try {
            UploadPartResponse response = s3Client.uploadPart(
                    UploadPartRequest.builder().bucket(bucket).key(filename)
                            .uploadId(upload_id).partNumber(chunk_number).build(),
                    RequestBody.fromBytes(body));
            return new PartInfoDTO(chunk_number, response.eTag());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Transactional
    public CompleteMultipartResponseDTO completeMultipart(String username, CompleteMultipartRequestDTO body) {
        MultipartUpload upload = findPendingUpload(username, body.getFilename(), body.getUploadId());
        try {
            List<CompletedPart> parts = body.getParts().stream()
                    .sorted(Comparator.comparing(PartInfoDTO::getPartNumber))
                    .map(p -> CompletedPart.builder().partNumber(p.getPartNumber()).eTag(p.getETag()).build())
                    .toList();
            CompleteMultipartUploadResponse response = s3Client.completeMultipartUpload(
                    CompleteMultipartUploadRequest.builder().bucket(bucket).key(body.getFilename())
                            .uploadId(body.getUploadId())
                            .multipartUpload(CompletedMultipartUpload.builder().parts(parts).build())
                            .build());

            upload.setStatus("completed");
            multipartUploadRepository.save(upload);

            User author = requireUser(username);
            Video video = new Video();
            video.setFilename(body.getFilename());
            video.setAuthor(author);
            video.setVideo_name(upload.getOriginal_filename());
            video.setVideo_desc("");
            video.setCreated_at(Date.from(Instant.now()));
            video.setIs_published(true);
            video.setVisibility(VisibilityEnum.PUBLIC);
            videoRepository.save(video);

            videoProcessor.transcodeAndReplace(body.getFilename(), upload.getOriginal_filename());

            return new CompleteMultipartResponseDTO("success",
                    response.location() == null ? "" : response.location(),
                    body.getFilename(), upload.getOriginal_filename());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @Transactional
    public Map<String, String> cancelMultipart(String username, String filename, String upload_id) {
        MultipartUpload upload = findPendingUpload(username, filename, upload_id);
        s3Client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder().bucket(bucket).key(filename).uploadId(upload_id).build());
        upload.setStatus("cancelled");
        multipartUploadRepository.save(upload);
        return Map.of("status", "cancelled");
    }

    private MultipartUpload findPendingUpload(String username, String filename, String uploadId) {
        MultipartUpload upload = multipartUploadRepository
                .findByUploadIdAndUploadKey(uploadId, filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Carga no encontrada"));
        if (!upload.getOwner().getId().equals(requireUser(username).getId()) || !"pending".equals(upload.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Carga no encontrada");
        }
        return upload;
    }

    // ---------- Listados ----------

    public VideoListDTO listVideos() {
        try {
            Set<String> publicKeys = videoRepository.findAllByVisibility(VisibilityEnum.PUBLIC)
                    .stream().map(Video::getFilename).collect(Collectors.toSet());
            List<VideoListItemDTO> videos = new ArrayList<>();
            s3Client.listObjectsV2Paginator(b -> b.bucket(bucket).prefix("videos/")).stream()
                    .flatMap(page -> page.contents().stream())
                    .filter(obj -> !obj.key().endsWith("/") && publicKeys.contains(obj.key()))
                    .forEach(obj -> {
                        String original = objectOriginalFilename(obj.key());
                        videos.add(new VideoListItemDTO(obj.key(), obj.size(), obj.lastModified().toString(), original));
                    });
            VideoListDTO dto = new VideoListDTO();
            dto.setVideos(videos);
            return dto;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    private String objectOriginalFilename(String key) {
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            String metadata = head.metadata().get("original-filename");
            if (metadata != null) return metadata;
        } catch (Exception e) {
            log.warn("No se pudieron leer los metadatos S3 de {}", key);
        }
        return key.substring(key.lastIndexOf('/') + 1);
    }

    public VideoCatalogDTO catalog() {
        List<Video> videos = videoRepository.findAllByVisibilityWithAuthor(VisibilityEnum.PUBLIC);
        List<VideoCatalogItemDTO> items = new ArrayList<>();
        for (Video video : videos) {
            User author = video.getAuthor();
            items.add(new VideoCatalogItemDTO(video.getId(), video.getFilename(), video.getVideo_name(),
                    video.getVideo_desc(), author.getId(), author.getUsername(), author.getFull_name()));
        }
        VideoCatalogDTO dto = new VideoCatalogDTO();
        dto.setVideos(items);
        return dto;
    }

    public FeedDTO feed(Authentication auth, Integer limit, Boolean onlyFollowing) {
        int max = limit == null ? 50 : Math.max(1, Math.min(MAX_FEED_LIMIT, limit));
        boolean onlyFollowed = Boolean.TRUE.equals(onlyFollowing);

        User viewer = resolveOptionalUser(auth);
        Set<Integer> followedIds = viewer == null ? Set.of()
                : userFollowsUserRepository.findFollowedByFollowerId(viewer.getId())
                        .stream().map(User::getId).collect(Collectors.toSet());

        List<Video> rows = videoRepository.findAllByVisibilityWithAuthor(VisibilityEnum.PUBLIC);
        Map<Integer, Long> likes = countLikesMap(userLikesVideoRepository.countAllGroupByVideo());

        List<FeedRanker.FeedCandidate> candidates = new ArrayList<>();
        Map<Integer, Video> byId = new HashMap<>();
        for (Video video : rows) {
            if (onlyFollowed && !followedIds.contains(video.getAuthor().getId())) continue;
            byId.put(video.getId(), video);
            candidates.add(new FeedRanker.FeedCandidate(video.getId(), video.getAuthor().getId(),
                    video.getCreated_at().toInstant(), likes.getOrDefault(video.getId(), 0L)));
        }

        List<FeedRanker.ScoredVideo> ranked = FeedRanker.rankCandidates(candidates, followedIds, Instant.now(), max);
        List<FeedVideoItemDTO> items = new ArrayList<>();
        for (FeedRanker.ScoredVideo scored : ranked) {
            Video video = byId.get(scored.candidate().videoId());
            User author = video.getAuthor();
            items.add(new FeedVideoItemDTO(video.getId(), video.getFilename(), video.getVideo_name(),
                    video.getVideo_desc(), author.getId(), author.getUsername(), author.getFull_name(),
                    video.getCreated_at().toInstant().toString(), (int) scored.candidate().likes(),
                    scored.score(), scored.fromFollowedAuthor()));
        }
        return new FeedDTO(items, followedIds.size(), !followedIds.isEmpty());
    }

    public StudioVideoListDTO studio(String username) {
        User owner = requireUser(username);
        List<StudioVideoItemDTO> items = videoRepository.findAllByAuthorId(owner.getId()).stream()
                .map(this::studioItem).toList();
        StudioVideoListDTO dto = new StudioVideoListDTO();
        dto.setVideos(items);
        return dto;
    }

    // ---------- Detalle, edición, borrado ----------

    public VideoDetailDTO detail(Authentication auth, String key) {
        Video video = findAccessibleVideo(key, resolveOptionalUser(auth));
        User author = video.getAuthor();
        return new VideoDetailDTO(video.getId(), video.getFilename(), video.getVideo_name(),
                video.getVideo_desc(), video.getVisibility().toApi(), author.getId(),
                author.getUsername(), author.getFull_name());
    }

    @Transactional
    public StudioVideoItemDTO updateVideo(String username, String video_id, VideoUpdateDTO body) {
        User owner = requireUser(username);
        Video video = videoRepository.findById(parseId(video_id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado"));
        if (!video.getAuthor().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado");
        }

        VisibilityEnum visibility = VisibilityEnum.fromApi(body.getVisibility());
        if (visibility == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Visibilidad inválida");
        }
        String title = body.getTitle() == null ? "" : body.getTitle().strip();
        if (title.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "El título no puede estar vacío");
        }
        Set<String> allowedUsernames = body.getAllowed_users() == null ? Set.of()
                : body.getAllowed_users().stream()
                        .filter(u -> u != null && !u.strip().isEmpty())
                        .map(String::strip).collect(Collectors.toSet());

        if (visibility == VisibilityEnum.PRIVATE && !allowedUsernames.isEmpty()) {
            List<String> existing = userRepository.findAllByUsernameIn(allowedUsernames)
                    .stream().map(User::getUsername).toList();
            Set<String> missing = new TreeSet<>(allowedUsernames);
            missing.removeAll(existing);
            if (!missing.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Usuarios inexistentes: " + String.join(", ", missing));
            }
        }

        video.setVideo_name(title);
        video.setVideo_desc(body.getDescription() == null ? "" : body.getDescription());
        video.setVisibility(visibility);
        video.setIs_published(visibility == VisibilityEnum.PUBLIC);
        videoRepository.save(video);

        userCanViewVideoRepository.deleteByVideoId(video.getId());
        if (visibility == VisibilityEnum.PRIVATE) {
            for (String usernameAllowed : allowedUsernames) {
                User allowed = userRepository.findByUsername(usernameAllowed).orElseThrow();
                UserCanViewVideo link = new UserCanViewVideo();
                link.setUser(allowed);
                link.setVideo(video);
                userCanViewVideoRepository.save(link);
            }
        }
        return studioItem(video);
    }

    @Transactional
    public void deleteVideo(String username, String video_id) {
        User owner = requireUser(username);
        Video video = videoRepository.findById(parseId(video_id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado"));
        if (!video.getAuthor().getId().equals(owner.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado");
        }
        userLikesVideoRepository.deleteByVideoId(video.getId());
        userCanViewVideoRepository.deleteByVideoId(video.getId());
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(video.getFilename()).build());
        videoRepository.delete(video);
    }

    // ---------- Streaming ----------

    public StreamUrlResponseDTO streamUrl(Authentication auth, String token, String key) {
        Video video = findAccessibleVideo(key, resolveOptionalUser(auth));
        String params = "key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        // Un vídeo público no necesita credenciales: mantener el token fuera de la
        // URL evita que acabe en historiales, logs de proxy o enlaces compartidos.
        if (video.getVisibility() != VisibilityEnum.PUBLIC && token != null && !token.isBlank()) {
            params += "&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        }
        return new StreamUrlResponseDTO("/api/v1/video/stream?" + params, key);
    }

    public ResponseEntity<StreamingResponseBody> stream(Authentication auth, String key, String token, String range) {
        User viewer = resolveOptionalUser(auth);
        if (viewer == null) {
            viewer = userFromQueryToken(token);
        }
        Video video = findAccessibleVideo(key, viewer);

        GetObjectRequest.Builder request = GetObjectRequest.builder().bucket(bucket).key(key);
        if (range != null && !range.isBlank()) {
            request.range(range);
        }
        final GetObjectResponse response;
        final ResponseInputStream<GetObjectResponse> bodyStream;
        try {
            var obj = s3Client.getObject(request.build());
            response = obj.response();
            bodyStream = obj;
        } catch (Exception e) {
            log.warn("No se pudo leer el vídeo {} del almacenamiento: {}", video.getId(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (response.contentLength() != null) {
            headers.setContentLength(response.contentLength());
        }
        HttpStatus status = HttpStatus.OK;
        if (response.contentRange() != null && !response.contentRange().isBlank()) {
            headers.set(HttpHeaders.CONTENT_RANGE, response.contentRange());
            status = HttpStatus.PARTIAL_CONTENT;
        }
        headers.setContentType(MediaType.parseMediaType(
                response.contentType() == null ? "video/mp4" : response.contentType()));

        StreamingResponseBody body = out -> {
            try (var in = bodyStream) {
                byte[] buffer = new byte[STREAM_CHUNK_BYTES];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        };
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    // ---------- Acceso y utilidades ----------

    private Video findAccessibleVideo(String key, User viewer) {
        Video video = videoRepository.findByFilename(key)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado"));
        if (!canView(video, viewer)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado");
        }
        return video;
    }

    private boolean canView(Video video, User viewer) {
        if (video.getVisibility() == VisibilityEnum.PUBLIC || video.getVisibility() == VisibilityEnum.HIDDEN) {
            return true;
        }
        if (viewer == null) return false;
        if (video.getAuthor().getId().equals(viewer.getId())) return true;
        return userCanViewVideoRepository.existsByUserIdAndVideoId(viewer.getId(), video.getId());
    }

    private StudioVideoItemDTO studioItem(Video video) {
        long size = 0;
        String lastModified = Instant.now().toString();
        String originalFilename = video.getVideo_name();
        try {
            HeadObjectResponse head = s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucket).key(video.getFilename()).build());
            size = head.contentLength();
            if (head.lastModified() != null) lastModified = head.lastModified().toString();
            String metadata = head.metadata().get("original-filename");
            if (metadata != null) originalFilename = metadata;
        } catch (Exception e) {
            log.warn("No se pudieron leer los metadatos S3 de {}", video.getFilename());
        }
        List<String> allowed = userCanViewVideoRepository.findUsernamesByVideoId(video.getId()).stream().sorted().toList();
        return new StudioVideoItemDTO(video.getFilename(), (int) size, lastModified, originalFilename,
                video.getId(), video.getVideo_name(), video.getVideo_desc(),
                video.getVisibility().toApi(), allowed);
    }

    private Map<Integer, Long> countLikesMap(List<Object[]> rows) {
        Map<Integer, Long> likes = new HashMap<>();
        for (Object[] row : rows) {
            likes.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        return likes;
    }

    private Integer parseId(String videoId) {
        try {
            return Integer.valueOf(videoId);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vídeo no encontrado");
        }
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authed user not found"));
    }

    private User resolveOptionalUser(Authentication auth) {
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return userRepository.findByUsername(auth.getName()).orElse(null);
        }
        return null;
    }

    /** El elemento <video> no envía cabeceras: para privados el JWT viaja en la query. */
    private User userFromQueryToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Jwt jwt = jwtDecoder.decode(token);
            User user = userRepository.findByUsername(jwt.getSubject()).orElse(null);
            if (user == null) return null;
            Object pwdVer = jwt.getClaim("pwd_ver");
            if (pwdVer instanceof Number n && user.getPassword_version().equals(n.intValue())) {
                return user;
            }
            return null;
        } catch (JwtException e) {
            return null;
        }
    }
}
