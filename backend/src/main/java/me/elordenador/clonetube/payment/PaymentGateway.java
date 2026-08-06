package me.elordenador.clonetube.payment;

import me.elordenador.clonetube.models.Payment;

import java.util.Map;

/**
 * Abstraccion de un proveedor de pago. Permite crear un checkout y resolver
 * el pago recibido en un webhook de vuelta a nuestra orden interna.
 */
public interface PaymentGateway {

    String providerName();

    CheckoutResult createCheckout(Payment order);

    /**
     * Devuelve el id de pago del proveedor a partir de la carga del webhook.
     */
    String extractProviderPaymentId(String rawBody, Map<String, String> headers);

    /**
     * Devuelve el id de nuestra orden interna (metadata/custom_id) a partir
     * del webhook. {@code null} si no se puede determinar.
     */
    String extractOrderId(String rawBody, Map<String, String> headers);

    /**
     * Valida la firma del webhook. Devuelve {@code false} si no se puede
     * autenticar de forma fiable.
     */
    boolean isWebhookAuthentic(String rawBody, Map<String, String> headers);
}
