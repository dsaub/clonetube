package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.models.*;
import me.elordenador.clonetube.payment.CheckoutResult;
import me.elordenador.clonetube.payment.PaymentGateway;
import me.elordenador.clonetube.payment.PayoutGateway;
import me.elordenador.clonetube.payment.PayoutResult;
import me.elordenador.clonetube.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sistema de puntuación de clonetube.
 *
 * <p>Los puntos se compran con dinero real (1&nbsp;€ = {@value #PURCHASE_POINTS_PER_EURO}
 * puntos) a través de Stripe o PayPal. Los puntos se pueden donar a otros canales y también
 * se pueden reclamar de vuelta a euros aplicando una comisión: para sacar 1&nbsp;€ se
 * necesitan {@value #WITHDRAWAL_POINTS_PER_EURO} puntos en lugar de
 * {@value #PURCHASE_POINTS_PER_EURO}.</p>
 */
@Service
@RequiredArgsConstructor
public class PointsService {

    public static final int PURCHASE_POINTS_PER_EURO = 100;
    public static final int WITHDRAWAL_POINTS_PER_EURO = 120;

    private static final String TYPE_PURCHASE = "purchase";
    private static final String TYPE_DONATION_SENT = "donation_sent";
    private static final String TYPE_DONATION_RECEIVED = "donation_received";
    private static final String TYPE_WITHDRAWAL = "withdrawal";
    private static final String TYPE_WITHDRAWAL_REFUND = "withdrawal_refund";

    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final DonationRepository donationRepository;
    private final WithdrawalRequestRepository withdrawalRepository;
    private final PointsHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final EmailPublisher emailPublisher;
    private final EmailTemplateService emailTemplateService;
    private final List<PaymentGateway> gateways;
    private final List<PayoutGateway> payoutGateways;

    private Map<String, PaymentGateway> gatewaysByName() {
        return gateways.stream().collect(Collectors.toMap(PaymentGateway::providerName, Function.identity()));
    }

    private Map<String, PayoutGateway> payoutGatewaysByName() {
        return payoutGateways.stream().collect(Collectors.toMap(PayoutGateway::providerName, Function.identity()));
    }

    public PointsViewDTO getPoints(String username) {
        User user = requireUser(username);
        return new PointsViewDTO(user.getPoints() == null ? 0 : user.getPoints());
    }

    @Transactional
    public PurchaseResponseDTO purchase(String username, PurchaseRequestDTO body) {
        User user = requireUser(username);
        if (body.getProvider() == null || body.getProvider().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is required");
        }
        PaymentGateway gateway = gatewaysByName().get(body.getProvider().toUpperCase());
        if (gateway == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payment provider");
        }
        BigDecimal amount = body.getAmountEur();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount must be positive");
        }
        int points = amount.multiply(BigDecimal.valueOf(PURCHASE_POINTS_PER_EURO))
                .setScale(0, RoundingMode.DOWN).intValue();
        if (points < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount too small");
        }

        Payment payment = Payment.builder()
                .user(user)
                .provider(gateway.providerName())
                .amountEur(amount.setScale(2, RoundingMode.HALF_UP))
                .points(points)
                .status("PENDING")
                .createdAt(Instant.now())
                .build();
        payment = paymentRepository.save(payment);

        CheckoutResult checkout;
        try {
            checkout = gateway.createCheckout(payment);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to start checkout: " + e.getMessage());
        }
        payment.setProviderPaymentId(checkout.providerPaymentId());
        paymentRepository.save(payment);
        return new PurchaseResponseDTO(payment.getId(), checkout.checkoutUrl(), points);
    }

    @Transactional
    public PaymentDTO confirmPayment(String username, Long orderId) {
        Payment payment = requireOwnedPayment(username, orderId);
        if ("PENDING".equals(payment.getStatus())) {
            credit(payment.getUser(), payment.getPoints(), TYPE_PURCHASE,
                    "Compra de " + payment.getPoints() + " puntos (" + payment.getProvider() + ")");
            payment.setStatus("PAID");
            paymentRepository.save(payment);
        }
        return toPaymentDto(payment);
    }

    @Transactional
    public void handleWebhook(String provider, String rawBody, Map<String, String> headers) {
        PaymentGateway gateway = gatewaysByName().get(provider);
        if (gateway == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown provider");
        }
        if (!gateway.isWebhookAuthentic(rawBody, headers)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        String orderId = gateway.extractOrderId(rawBody, headers);
        if (orderId == null) {
            return;
        }
        Payment payment = paymentRepository.findById(Long.valueOf(orderId)).orElse(null);
        if (payment == null) {
            return;
        }
        payment.setProviderPaymentId(gateway.extractProviderPaymentId(rawBody, headers));
        if (!"PAID".equals(payment.getStatus())) {
            credit(payment.getUser(), payment.getPoints(), TYPE_PURCHASE,
                    "Compra de " + payment.getPoints() + " puntos (" + payment.getProvider() + ")");
            payment.setStatus("PAID");
            paymentRepository.save(payment);
        }
    }

    @Transactional
    public DonationDTO donate(String username, DonateRequestDTO body) {
        User donor = requireUser(username);
        if (body.getRecipientUsername() == null || body.getRecipientUsername().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Recipient is required");
        }
        if (body.getPoints() == null || body.getPoints() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be positive");
        }
        User recipient = userRepository.findByUsername(body.getRecipientUsername())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));
        if (donor.getId().equals(recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes donarte a ti mismo");
        }
        int donorPoints = donor.getPoints() == null ? 0 : donor.getPoints();
        if (donorPoints < body.getPoints()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Puntos insuficientes");
        }

        donor.setPoints(donorPoints - body.getPoints());
        recipient.setPoints((recipient.getPoints() == null ? 0 : recipient.getPoints()) + body.getPoints());
        userRepository.save(donor);
        userRepository.save(recipient);

        Donation donation = Donation.builder()
                .donor(donor)
                .recipient(recipient)
                .points(body.getPoints())
                .message(body.getMessage())
                .createdAt(Instant.now())
                .build();
        donation = donationRepository.save(donation);

        history(donor, -body.getPoints(), TYPE_DONATION_SENT,
                "Donación a @" + recipient.getUsername() + " (" + body.getPoints() + " puntos)");
        history(recipient, body.getPoints(), TYPE_DONATION_RECEIVED,
                "Donación de @" + donor.getUsername() + " (" + body.getPoints() + " puntos)");

        String msg = body.getMessage() != null ? body.getMessage() : "";
        String title = "@" + donor.getUsername() + " te ha donado " + body.getPoints() + " puntos";
        String text = body.getMessage() == null || body.getMessage().isBlank()
                ? title
                : title + ": \"" + body.getMessage() + "\"";
        notificationService.notify(recipient, "donation", title, text);
        emailPublisher.send(recipient.getEmail(), title,
                title + (msg.isBlank() ? "" : " - " + msg),
                emailTemplateService.donation(recipient.getUsername(), donor.getUsername(), body.getPoints(), msg));

        return toDonationDto(donation);
    }

    @Transactional
    public WithdrawalDTO withdraw(String username, WithdrawRequestDTO body) {
        User user = requireUser(username);
        if (body.getPoints() == null || body.getPoints() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Points must be positive");
        }
        if (body.getProvider() == null || body.getProvider().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is required");
        }
        if (body.getDestination() == null || body.getDestination().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Destination is required");
        }
        PayoutGateway gateway = payoutGatewaysByName().get(body.getProvider().toUpperCase());
        if (gateway == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown payout provider");
        }
        int balance = user.getPoints() == null ? 0 : user.getPoints();
        if (balance < body.getPoints()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Puntos insuficientes");
        }
        BigDecimal eurValue = BigDecimal.valueOf(body.getPoints())
                .divide(BigDecimal.valueOf(WITHDRAWAL_POINTS_PER_EURO), 2, RoundingMode.DOWN);
        if (eurValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se necesitan al menos "
                    + WITHDRAWAL_POINTS_PER_EURO + " puntos para reclamar 1 euro");
        }

        // Retirada automática: se descuenta y se ejecuta el payout en el momento.
        user.setPoints(balance - body.getPoints());
        userRepository.save(user);

        WithdrawalRequest withdrawal = WithdrawalRequest.builder()
                .user(user)
                .points(body.getPoints())
                .eurValue(eurValue)
                .status("PROCESSING")
                .provider(gateway.providerName())
                .destination(body.getDestination())
                .createdAt(Instant.now())
                .build();
        withdrawal = withdrawalRepository.save(withdrawal);

        history(user, -body.getPoints(), TYPE_WITHDRAWAL,
                "Retirada de " + eurValue + " € (" + body.getPoints() + " puntos)");

        try {
            PayoutResult payout = gateway.sendPayout(eurValue, body.getDestination());
            withdrawal.setStatus("PAID");
            withdrawal.setProviderPayoutId(payout.providerPayoutId());
        } catch (Exception e) {
            // El payout falló: se devuelven los puntos y se marca como fallida.
            withdrawal.setStatus("FAILED");
            user.setPoints((user.getPoints() == null ? 0 : user.getPoints()) + body.getPoints());
            userRepository.save(user);
            history(user, body.getPoints(), TYPE_WITHDRAWAL_REFUND,
                    "Reembolso de la retirada fallida (" + body.getPoints() + " puntos)");
        }
        withdrawalRepository.save(withdrawal);
        return toWithdrawalDto(withdrawal);
    }

    @Transactional(readOnly = true)
    public List<PaymentDTO> listPayments(String username) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(requireUser(username).getId()).stream()
                .map(this::toPaymentDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DonationDTO> listDonations(String username, String direction) {
        Integer userId = requireUser(username).getId();
        List<Donation> donations = "received".equalsIgnoreCase(direction)
                ? donationRepository.findByRecipientIdOrderByCreatedAtDesc(userId)
                : donationRepository.findByDonorIdOrderByCreatedAtDesc(userId);
        return donations.stream().map(this::toDonationDto).toList();
    }

    @Transactional(readOnly = true)
    public List<WithdrawalDTO> listWithdrawals(String username) {
        return withdrawalRepository.findByUserIdOrderByCreatedAtDesc(requireUser(username).getId()).stream()
                .map(this::toWithdrawalDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryDTO> listLedger(String username) {
        return historyRepository.findByUserIdOrderByCreatedAtDesc(requireUser(username).getId()).stream()
                .map(h -> new LedgerEntryDTO(h.getTransaction_id(), h.getModifier(), h.getDescription(),
                        h.getType(), h.getCreated_at()))
                .toList();
    }

    private void credit(User user, int points, String type, String description) {
        user.setPoints((user.getPoints() == null ? 0 : user.getPoints()) + points);
        userRepository.save(user);
        history(user, points, type, description);
    }

    private void history(User user, int modifier, String type, String description) {
        PointsHistory entry = new PointsHistory();
        entry.setUser(user);
        entry.setModifier(modifier);
        entry.setDescription(description);
        entry.setType(type);
        entry.setCreated_at(Instant.now());
        historyRepository.save(entry);
    }

    private Payment requireOwnedPayment(String username, Long orderId) {
        Payment payment = paymentRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (!payment.getUser().getUsername().equals(username)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your payment");
        }
        return payment;
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Authed user not found"));
    }

    private PaymentDTO toPaymentDto(Payment p) {
        return new PaymentDTO(p.getId(), p.getProvider(), p.getProviderPaymentId(), p.getAmountEur(), p.getPoints(), p.getStatus());
    }

    private DonationDTO toDonationDto(Donation d) {
        return new DonationDTO(d.getId(), d.getDonor().getUsername(), d.getRecipient().getUsername(),
                d.getPoints(), d.getMessage(), d.getCreatedAt());
    }

    private WithdrawalDTO toWithdrawalDto(WithdrawalRequest w) {
        return new WithdrawalDTO(w.getId(), w.getPoints(), w.getEurValue(), w.getStatus(),
                w.getProvider(), w.getDestination(), w.getProviderPayoutId(), w.getCreatedAt());
    }
}
