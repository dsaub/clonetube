package me.elordenador.clonetube.services;

import me.elordenador.clonetube.models.Video;
import me.elordenador.clonetube.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoCatalogRequeueServiceTest {

    @Mock VideoRepository videoRepository;
    @Mock VideoTranscodePublisher publisher;
    @Mock ApplicationArguments args;

    @Test
    void run_enqueues_all_catalog_videos_when_enabled() {
        Video first = new Video();
        first.setId(1);
        first.setFilename("videos/abc.mp4");
        first.setVideo_name("Primer vídeo");
        Video second = new Video();
        second.setId(2);
        second.setFilename("videos/def.mp4");
        second.setVideo_name(null);
        when(videoRepository.findAll()).thenReturn(List.of(first, second));

        VideoCatalogRequeueService service = new VideoCatalogRequeueService(videoRepository, publisher);
        ReflectionTestUtils.setField(service, "requeueCatalog", true);
        service.run(args);

        verify(publisher).enqueue(1, "videos/abc.mp4", null, "Primer vídeo");
        verify(publisher).enqueue(2, "videos/def.mp4", null, "videos/def.mp4");
    }

    @Test
    void run_does_nothing_when_disabled() {
        VideoCatalogRequeueService service = new VideoCatalogRequeueService(videoRepository, publisher);
        ReflectionTestUtils.setField(service, "requeueCatalog", false);
        service.run(args);

        verifyNoInteractions(videoRepository, publisher);
    }
}
