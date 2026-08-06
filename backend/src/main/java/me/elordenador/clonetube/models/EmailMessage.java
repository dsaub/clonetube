package me.elordenador.clonetube.models;

public record EmailMessage(String id, String to, String subject, String body, String body_html) {
    public EmailMessage(String id, String to, String subject, String body) {
        this(id, to, subject, body, null);
    }
}
