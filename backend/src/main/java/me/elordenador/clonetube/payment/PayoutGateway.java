package me.elordenador.clonetube.payment;

import java.math.BigDecimal;

/**
 * Capacidad de un proveedor de pago para enviar dinero a un destinatario
 * (retirada automática de puntos a euros).
 */
public interface PayoutGateway {

    String providerName();

    /**
     * Envía un pago de {@code amountEur} euros al {@code destination}.
     *
     * @param amountEur   importe en euros (sin IVA adicional)
     * @param destination destino del pago (email de PayPal o cuenta/destino de Stripe)
     * @return resultado con el id del payout en el proveedor
     * @throws IllegalStateException si el envío falla
     */
    PayoutResult sendPayout(BigDecimal amountEur, String destination);
}
