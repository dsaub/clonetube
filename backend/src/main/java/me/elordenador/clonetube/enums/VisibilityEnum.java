package me.elordenador.clonetube.enums;

/**
 * Visibilidad de un video. La API habla en minúsculas (public/unlisted/private);
 * el enum (y la BD) usan PUBLIC/HIDDEN/PRIVATE. Toda la traducción vive aquí.
 */
public enum VisibilityEnum {
    PUBLIC,
    HIDDEN,
    PRIVATE;

    public static VisibilityEnum fromApi(String value) {
        if (value == null) return null;
        return switch (value.toLowerCase()) {
            case "public" -> PUBLIC;
            case "unlisted" -> HIDDEN;
            case "private" -> PRIVATE;
            default -> null;
        };
    }

    public String toApi() {
        return switch (this) {
            case PUBLIC -> "public";
            case HIDDEN -> "unlisted";
            case PRIVATE -> "private";
        };
    }
}
