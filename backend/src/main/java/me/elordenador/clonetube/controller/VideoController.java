package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.services.VideoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping("/start-multipart")
    @RequireAuth
    public StartMultipartResponseDTO startMultipart(Authentication auth,
                                                    @RequestParam String original_filename) {
        return videoService.startMultipart(auth.getName(), original_filename);
    }

    @GetMapping("/sign-chunk")
    @RequireAuth
    public SignChunkResponseDTO signChunk(Authentication auth,
                                          @RequestParam String filename,
                                          @RequestParam String upload_id,
                                          @RequestParam Integer chunk_number) {
        return videoService.signChunk(filename, upload_id, chunk_number);
    }

    @PutMapping("/upload-chunk")
    @RequireAuth
    public PartInfoDTO uploadChunk(Authentication auth,
                                   @RequestParam String filename,
                                   @RequestParam String upload_id,
                                   @RequestParam Integer chunk_number,
                                   @RequestBody byte[] body) {
        return videoService.uploadChunk(auth.getName(), filename, upload_id, chunk_number, body);
    }

    @PostMapping("/complete-multipart")
    @RequireAuth
    public CompleteMultipartResponseDTO completeMultipart(Authentication auth,
                                                          @RequestBody CompleteMultipartRequestDTO body) {
        return videoService.completeMultipart(auth.getName(), body);
    }

    @DeleteMapping("/cancel-multipart")
    @RequireAuth
    public Map<String, String> cancelMultipart(Authentication auth,
                                               @RequestParam String filename,
                                               @RequestParam String upload_id) {
        return videoService.cancelMultipart(auth.getName(), filename, upload_id);
    }

    @GetMapping("/list")
    public VideoListDTO listVideos() {
        return videoService.listVideos();
    }

    @GetMapping("/catalog")
    public VideoCatalogDTO catalog() {
        return videoService.catalog();
    }

    @GetMapping("/feed")
    public FeedDTO feed(Authentication auth,
                        @RequestParam(defaultValue = "50") Integer limit,
                        @RequestParam(defaultValue = "false") Boolean only_following) {
        return videoService.feed(auth, limit, only_following);
    }

    @GetMapping("/detail")
    public VideoDetailDTO detail(Authentication auth, @RequestParam Integer id) {
        return videoService.detail(auth, id);
    }

    @GetMapping("/studio")
    @RequireAuth
    public StudioVideoListDTO studio(Authentication auth) {
        return videoService.studio(auth.getName());
    }

    @PatchMapping("/{video_id}")
    @RequireAuth
    public StudioVideoItemDTO updateVideo(Authentication auth,
                                          @PathVariable String video_id,
                                          @RequestBody VideoUpdateDTO body) {
        return videoService.updateVideo(auth.getName(), video_id, body);
    }

    @DeleteMapping("/{video_id}")
    @RequireAuth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVideo(Authentication auth, @PathVariable String video_id) {
        videoService.deleteVideo(auth.getName(), video_id);
    }

    @GetMapping("/stream-url")
    public StreamUrlResponseDTO streamUrl(Authentication auth,
                                           @RequestHeader(value = "Authorization", required = false) String authorization,
                                           @RequestParam Integer id) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        return videoService.streamUrl(auth, token, id);
    }

    @GetMapping("/playback")
    public PlaybackInfoDTO playback(Authentication auth,
                                    @RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam Integer id) {
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        return videoService.playback(auth, token, id);
    }

    @GetMapping("/stream")
    public ResponseEntity<StreamingResponseBody> stream(
            Authentication auth,
            @RequestParam String key,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "Range", required = false) String range) {
        return videoService.stream(auth, key, token, range);
    }

    @GetMapping("/stream/{*key}")
    public ResponseEntity<StreamingResponseBody> streamPath(
            Authentication auth,
            @PathVariable("key") String key,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "Range", required = false) String range) {
        return videoService.stream(auth, key, token, range);
    }
}
