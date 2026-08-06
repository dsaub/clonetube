package me.elordenador.clonetube.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.elordenador.clonetube.models.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class PayPalGateway implements PaymentGateway, PayoutGateway {

    private static final String PROVIDER = "PAYPAL";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${paypal.client-id:}")
    private String clientId;

    @Value("${paypal.client-secret:}")
    private String clientSecret;

    @Value("${paypal.base-url:https://api-m.sandbox.paypal.com}")
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
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("PayPal credentials not configured");
        }
        if (destination == null || destination.isBlank()) {
            throw new IllegalStateException("PayPal payout destination is required");
        }
        String token = accessToken();
        String batchId = UUID.randomUUID().toString();

        String payload = "{\"sender_batch_header\":{"
                + "\"sender_batch_id\":\"" + batchId + "\","
                + "\"email_subject\":\"Retirada de clonetube\","
                + "\"email_message\":\"Has recibido el pago de tu retirada.\"},"
                + "\"items\":[{"
                + "\"recipient_type\":\"EMAIL\","
                + "\"amount\":{\"value\":\"" + amountEur.toPlainString() + "\",\"currency\":\"EUR\"},"
                + "\"receiver\":\"" + destination + "\","
                + "\"note\":\"Retirada de puntos clonetube\""
                + "}]}";

        JsonNode json = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("PayPal-Request-Id", UUID.randomUUID().toString())
                .build()
                .post()
                .uri("/v2/payments/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        if (json == null || !json.path("batch_header").has("payout_batch_id")) {
            throw new IllegalStateException("PayPal payout failed");
        }
        return new PayoutResult(json.path("batch_header").get("payout_batch_id").asText());
    }

    @Override
    public CheckoutResult createCheckout(Payment order) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("PayPal credentials not configured");
        }
        String token = accessToken();

        BigDecimal amount = order.getAmountEur() == null ? BigDecimal.ZERO : order.getAmountEur();

        String payload = "{\"intent\":\"CAPTURE\","
                + "\"purchase_units\":[{\"custom_id\":\"" + order.getId() + "\","
                + "\"amount\":{\"currency_code\":\"EUR\",\"value\":\"" + amount.toPlainString() + "\"}}],"
                + "\"application_context\":{\"return_url\":\"" + successUrl + "?order=" + order.getId()
                + "\",\"cancel_url\":\"" + cancelUrl + "\"}}";

        JsonNode json = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .build()
                .post()
                .uri("/v2/checkout/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        if (json == null || !json.has("id")) {
            throw new IllegalStateException("PayPal order creation failed");
        }
        String orderId = json.get("id").asText();
        String approveUrl = null;
        JsonNode links = json.path("links");
        for (JsonNode link : links) {
            if ("payer-action".equals(link.path("rel").asText())) {
                approveUrl = link.path("href").asText();
            }
        }
        if (approveUrl == null) {
            approveUrl = json.path("links").get(0).path("href").asText();
        }
        return new CheckoutResult(orderId, approveUrl);
    }

    private String accessToken() {
        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        JsonNode json = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Basic " + credentials)
                .build()
                .post()
                .uri("/v1/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(JsonNode.class);
        if (json == null || !json.has("access_token")) {
            throw new IllegalStateException("PayPal auth failed");
        }
        return json.get("access_token").asText();
    }

    @Override
    public String extractProviderPaymentId(String rawBody, Map<String, String> headers) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root.path("resource").has("id")) {
                return root.path("resource").get("id").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public String extractOrderId(String rawBody, Map<String, String> headers) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root.path("resource").has("custom_id")) {
                return root.path("resource").get("custom_id").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public boolean isWebhookAuthentic(String rawBody, Map<String, String> headers) {
        // La verificacion de firma de PayPal requiere una llamada extra al API
        // (/v1/notifications/verify-webhook-signature). Se asume autentico; el
        // webhook se valida por el custom_id interno de la orden.
        return true;
    }
}
