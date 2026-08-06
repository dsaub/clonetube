package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
    List<Payment> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
