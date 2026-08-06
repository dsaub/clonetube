package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.*;
import me.elordenador.clonetube.services.PointsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @GetMapping
    @RequireAuth
    public PointsViewDTO getPoints(Authentication auth) {
        return pointsService.getPoints(auth.getName());
    }

    @PostMapping("/purchase")
    @RequireAuth
    public PurchaseResponseDTO purchase(Authentication auth, @RequestBody PurchaseRequestDTO body) {
        return pointsService.purchase(auth.getName(), body);
    }

    @PostMapping("/purchase/{orderId}/confirm")
    @RequireAuth
    public PaymentDTO confirmPayment(Authentication auth, @PathVariable Long orderId) {
        return pointsService.confirmPayment(auth.getName(), orderId);
    }

    @GetMapping("/payments")
    @RequireAuth
    public List<PaymentDTO> listPayments(Authentication auth) {
        return pointsService.listPayments(auth.getName());
    }

    @PostMapping("/donate")
    @RequireAuth
    public DonationDTO donate(Authentication auth, @RequestBody DonateRequestDTO body) {
        return pointsService.donate(auth.getName(), body);
    }

    @GetMapping("/donations")
    @RequireAuth
    public List<DonationDTO> listDonations(Authentication auth,
                                           @RequestParam(defaultValue = "sent") String direction) {
        return pointsService.listDonations(auth.getName(), direction);
    }

    @PostMapping("/withdraw")
    @RequireAuth
    public WithdrawalDTO withdraw(Authentication auth, @RequestBody WithdrawRequestDTO body) {
        return pointsService.withdraw(auth.getName(), body);
    }

    @GetMapping("/withdrawals")
    @RequireAuth
    public List<WithdrawalDTO> listWithdrawals(Authentication auth) {
        return pointsService.listWithdrawals(auth.getName());
    }

    @GetMapping("/ledger")
    @RequireAuth
    public List<LedgerEntryDTO> ledger(Authentication auth) {
        return pointsService.listLedger(auth.getName());
    }

    @PostMapping("/webhooks/stripe")
    public ResponseEntity<Map<String, String>> stripeWebhook(@RequestBody String body,
                                                             @RequestHeader Map<String, String> headers) {
        pointsService.handleWebhook("STRIPE", body, headers);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/webhooks/paypal")
    public ResponseEntity<Map<String, String>> paypalWebhook(@RequestBody String body,
                                                             @RequestHeader Map<String, String> headers) {
        pointsService.handleWebhook("PAYPAL", body, headers);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
