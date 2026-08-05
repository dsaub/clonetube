package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.services.VideoService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    @PostMapping("/start-multipart")
    @RequireAuth
    public StartMultipartResponseDTO startMultipart(@RequestParam String original_filename) {
        return videoService.startMultipart(original_filename);
    }

    @GetMapping("/sign-chunk")
    @RequireAuth
    public SignChunkResponseDTO signChunk(
            @RequestParam String filename,
            @RequestParam String upload_id,
            @RequestParam Integer chunk_number) {
        return videoService.signChunk(filename, upload_id, chunk_number);
    }

    @PutMapping("/upload-chunk")
    @RequireAuth
    public PartInfoDTO uploadChunk(
            @RequestParam String filename,
            @RequestParam String upload_id,
            @RequestParam Integer chunk_number,
            @RequestBody byte[] body) {
        return videoService.uploadChunk(filename, upload_id, chunk_number, body);
    }

    @PostMapping("/complete-multipart")
    @RequireAuth
    public CompleteMultipartResponseDTO completeMultipart(@RequestBody CompleteMultipartRequestDTO body) {
        return videoService.completeMultipart(body);
    }

    @DeleteMapping("/cancel-multipart")
    @RequireAuth
    public Map<String, String> cancelMultipart(
            @RequestParam String filename,
            @RequestParam String upload_id) {
        return videoService.cancelMultipart(filename, upload_id);
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
    public FeedDTO feed(
            @RequestParam(defaultValue = "50") Integer limit,
            @RequestParam(defaultValue = "false") Boolean only_following) {
        return videoService.feed(limit, only_following);
    }

    @GetMapping("/detail")
    public VideoDetailDTO detail(@RequestParam String key) {
        return videoService.detail(key);
    }

    @GetMapping("/studio")
    @RequireAuth
    public StudioVideoListDTO studio() {
        return videoService.studio();
    }

    @PatchMapping("/{video_id}")
    @RequireAuth
    public StudioVideoItemDTO updateVideo(
            @PathVariable String video_id,
            @RequestBody VideoUpdateDTO body) {
        return videoService.updateVideo(video_id, body);
    }

    @DeleteMapping("/{video_id}")
    @RequireAuth
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVideo(@PathVariable String video_id) {
        videoService.deleteVideo(video_id);
    }

    @GetMapping("/stream-url")
    public StreamUrlResponseDTO streamUrl(@RequestParam String key) {
        return videoService.streamUrl(key);
    }

    @GetMapping("/stream")
    public ResponseEntity<Resource> stream(
            @RequestParam String key,
            @RequestParam(required = false) String token,
            @RequestHeader(value = "Range", required = false) String range) {
        return videoService.stream(key, token, range);
    }
}
