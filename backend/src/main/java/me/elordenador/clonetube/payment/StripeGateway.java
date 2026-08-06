package me.elordenador.clonetube.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.elordenador.clonetube.models.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

@Service
public class StripeGateway implements PaymentGateway, PayoutGateway {

    private static final String PROVIDER = "STRIPE";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.base-url:https://api.stripe.com}")
    private String baseUrl;

    @Value("${points.success-url:}")
    private String successUrl;

    @Value("${points.cancel-url:}")
    private String cancelUrl;

    @Override
    public String providerName() {
        return PROVIDER;
    }

    @Override
    public PayoutResult sendPayout(BigDecimal amountEur, String destination) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key not configured");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("Stripe payout destination is required");
        }
        long cents = amountEur.multiply(BigDecimal.valueOf(100)).longValue();
        String form = "amount=" + cents
                + "&currency=eur"
                + "&destination=" + enc(destination);

        JsonNode json = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .build()
                .post()
                .uri("/v1/payouts")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        if (json == null || !json.has("id")) {
            throw new IllegalStateException("Stripe payout failed");
        }
        return new PayoutResult(json.get("id").asText());
    }

    @Override
    public CheckoutResult createCheckout(Payment order) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Stripe secret key not configured");
        }
        BigDecimal amount = order.getAmountEur() == null ? BigDecimal.ZERO : order.getAmountEur();
        long cents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        String form = "mode=payment"
                + "&success_url=" + enc(successUrl + "?order=" + order.getId())
                + "&cancel_url=" + enc(cancelUrl)
                + "&metadata[order_id]=" + order.getId()
                + "&line_items[0][quantity]=1"
                + "&line_items[0][price_data][currency]=eur"
                + "&line_items[0][price_data][unit_amount]=" + cents
                + "&line_items[0][price_data][product_data][name]="
                + enc("Puntos clonetube - " + order.getPoints());

        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + secretKey)
                .build();

        JsonNode json = client.post()
                .uri("/v1/checkout/sessions")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);

        if (json == null || !json.has("id") || !json.has("url")) {
            throw new IllegalStateException("Stripe checkout creation failed");
        }
        return new CheckoutResult(json.get("id").asText(), json.get("url").asText());
    }

    @Override
    public String extractProviderPaymentId(String rawBody, Map<String, String> headers) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode session = root.path("data").path("object");
            if (session.has("id")) {
                return session.get("id").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public String extractOrderId(String rawBody, Map<String, String> headers) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode metadata = root.path("data").path("object").path("metadata");
            if (metadata.has("order_id")) {
                return metadata.get("order_id").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public boolean isWebhookAuthentic(String rawBody, Map<String, String> headers) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        String signatureHeader = headers.get("stripe-signature");
        if (signatureHeader == null) {
            return false;
        }
        String[] parts = signatureHeader.split(",");
        String signature = null;
        String timestamp = null;
        for (String part : parts) {
            if (part.startsWith("t=")) {
                timestamp = part.substring(2);
            } else if (part.startsWith("v1=")) {
                signature = part.substring(3);
            }
        }
        if (signature == null || timestamp == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(bytes);
            return constantTimeEquals(expected, signature);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    private String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
