package me.elordenador.clonetube.models;

public record VideoTranscodeMessage(
        String type,
        int version,
        String id,
        Integer video_id,
        String source_key,
        String source_etag,
        String original_filename
) {
}
