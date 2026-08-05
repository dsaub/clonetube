package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.dtos.*;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class VideoService {

    public StartMultipartResponseDTO startMultipart(String original_filename) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public SignChunkResponseDTO signChunk(String filename, String upload_id, Integer chunk_number) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public PartInfoDTO uploadChunk(String filename, String upload_id, Integer chunk_number, byte[] body) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public CompleteMultipartResponseDTO completeMultipart(CompleteMultipartRequestDTO body) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public Map<String, String> cancelMultipart(String filename, String upload_id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public VideoListDTO listVideos() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public VideoCatalogDTO catalog() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public FeedDTO feed(Integer limit, Boolean only_following) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public VideoDetailDTO detail(String key) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public StudioVideoListDTO studio() {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public StudioVideoItemDTO updateVideo(String video_id, VideoUpdateDTO body) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public void deleteVideo(String video_id) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public StreamUrlResponseDTO streamUrl(String key) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    public ResponseEntity<Resource> stream(String key, String token, String range) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
