package me.elordenador.clonetube.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Transcodificación fire-and-forget (port de backend.old/video_processor.py):
 * descarga el video de S3, lo convierte a MP4/H.264/AAC (+faststart) con
 * ffmpeg y lo reemplaza en la misma key. Los fallos solo se loguean: el
 * video original sigue siendo reproducible.
 */
@Slf4j
@Component
public class VideoProcessor {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv", ".m4v", ".3gp", ".ogv");

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    public VideoProcessor(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public static boolean isValidVideoExtension(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && ALLOWED_EXTENSIONS.contains(lower.substring(dot));
    }

    @Async
    public void transcodeAndReplace(String key, String originalFilename) {
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("clonetube-in", ".mp4");
            output = Files.createTempFile("clonetube-out", ".mp4");

            log.info("Descargando {} de S3 para transcodificar", key);
            s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toFile(input));

            String[] encoder = detectEncoder();
            runFfmpeg(List.of(
                    "ffmpeg", "-i", input.toString(),
                    "-c:v", encoder[0], "-profile:v", encoder[1],
                    "-level", "3.0", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k",
                    "-movflags", "+faststart", "-y", output.toString()));

            log.info("Subiendo video transcodificado a S3: {}", key);
            PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(key);
            if (originalFilename != null) {
                request.metadata(Map.of("original-filename", originalFilename));
            }
            s3Client.putObject(request.build(), output);
            log.info("Transcodificación completada para {}", key);
        } catch (Exception e) {
            log.error("Transcodificación fallida para {}", key, e);
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    /** Elige libx264 o libopenh264 según lo que ofrezca el ffmpeg instalado. */
    private String[] detectEncoder() throws IOException, InterruptedException {
        String output = runFfmpeg(List.of("ffmpeg", "-hide_banner", "-encoders"));
        if (output.contains("libx264")) return new String[]{"libx264", "baseline"};
        if (output.contains("libopenh264")) return new String[]{"libopenh264", "constrained_baseline"};
        throw new IllegalStateException("FFmpeg sin encoder H.264 (libx264/libopenh264)");
    }

    private String runFfmpeg(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("FFmpeg falló (" + command.get(0) + "): " + output);
        }
        return output;
    }

    private void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }
}
