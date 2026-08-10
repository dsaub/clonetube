package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.elordenador.clonetube.models.MultipartUpload;
import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.MultipartUploadRepository;
import me.elordenador.clonetube.repository.VideoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscodeRetryService {

    private final MultipartUploadRepository multipartUploadRepository;
    private final VideoRepository videoRepository;
    private final VideoTranscodePublisher publisher;

    @Scheduled(initialDelayString = "${video.transcode.retry-initial-delay-ms:30000}",
            fixedDelayString = "${video.transcode.retry-delay-ms:30000}")
    @Transactional
    public void retryPendingJobs() {
        for (MultipartUpload upload : multipartUploadRepository.findByStatus(
                "transcode_pending", PageRequest.of(0, 20))) {
            Video video = videoRepository.findByFilename(upload.getUpload_key()).orElse(null);
            if (video == null) {
                log.warn("No se puede reencolar la carga {}: no existe su vídeo", upload.getId());
                continue;
            }
            try {
                publisher.enqueue(video.getId(), upload.getUpload_key(), null, upload.getOriginal_filename());
                upload.setStatus("completed");
                multipartUploadRepository.save(upload);
                log.info("Trabajo de vídeo reencolado para {}", upload.getUpload_key());
            } catch (IllegalStateException e) {
                log.warn("SQS sigue sin estar disponible para {}", upload.getUpload_key());
            }
        }
    }
}
