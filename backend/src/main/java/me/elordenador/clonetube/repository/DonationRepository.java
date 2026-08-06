package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.Donation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    List<Donation> findByDonorIdOrderByCreatedAtDesc(Integer donorId);
    List<Donation> findByRecipientIdOrderByCreatedAtDesc(Integer recipientId);
}
