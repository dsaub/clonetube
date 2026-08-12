package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.VideoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class VideoCatalogRequeueService implements ApplicationRunner {

    private final VideoRepository videoRepository;
    private final VideoTranscodePublisher publisher;

    @Value("${video.transcode.requeue-catalog:false}")
    private boolean requeueCatalog;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!requeueCatalog) {
            log.info("Reencolado del catálogo desactivado (video.transcode.requeue-catalog=false)");
            return;
        }
        List<Video> videos = videoRepository.findAll();
        int enqueued = 0;
        for (Video video : videos) {
            if (video.getFilename() == null) {
                continue;
            }
            String originalFilename = video.getVideo_name() != null ? video.getVideo_name() : video.getFilename();
            try {
                publisher.enqueue(video.getId(), video.getFilename(), null, originalFilename);
                enqueued++;
            } catch (IllegalStateException e) {
                log.warn("SQS no disponible; se detiene el reencolado en {}", video.getFilename());
                break;
            }
        }
        log.info("Catálogo reencolado: {}/{} vídeos enviados a transcodificación HLS", enqueued, videos.size());
    }
}
