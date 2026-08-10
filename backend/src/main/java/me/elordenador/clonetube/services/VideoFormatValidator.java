package me.elordenador.clonetube.services;

import java.util.List;

public final class VideoFormatValidator {

    private static final List<String> ALLOWED_EXTENSIONS = List.of(
            ".mp4", ".mov", ".avi", ".mkv", ".webm", ".flv", ".wmv", ".m4v", ".3gp", ".ogv");

    private VideoFormatValidator() {
    }

    public static boolean isValidVideoExtension(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        int dot = lower.lastIndexOf('.');
        return dot >= 0 && ALLOWED_EXTENSIONS.contains(lower.substring(dot));
    }
}
