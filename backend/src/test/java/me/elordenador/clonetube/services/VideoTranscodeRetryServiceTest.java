package me.elordenador.clonetube.services;

import me.elordenador.clonetube.models.MultipartUpload;
import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.MultipartUploadRepository;
import me.elordenador.clonetube.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoTranscodeRetryServiceTest {

    @Mock MultipartUploadRepository multipartUploadRepository;
    @Mock VideoRepository videoRepository;
    @Mock VideoTranscodePublisher publisher;

    @Test
    void retryPendingJobs_enqueues_and_marks_upload_completed() {
        MultipartUpload upload = new MultipartUpload();
        upload.setId(7);
        upload.setUpload_key("videos/abc.mp4");
        upload.setOriginal_filename("pelicula.mov");
        upload.setStatus("transcode_pending");
        Video video = new Video();
        video.setId(42);

        when(multipartUploadRepository.findByStatus(any(), any(Pageable.class))).thenReturn(List.of(upload));
        when(videoRepository.findByFilename("videos/abc.mp4")).thenReturn(Optional.of(video));

        new VideoTranscodeRetryService(multipartUploadRepository, videoRepository, publisher).retryPendingJobs();

        verify(publisher).enqueue(42, "videos/abc.mp4", null, "pelicula.mov");
        verify(multipartUploadRepository).save(upload);
        assertEquals("completed", upload.getStatus());
    }
}
