package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.PointsHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointsHistoryRepository extends JpaRepository<PointsHistory, Integer> {
    List<PointsHistory> findByUserIdOrderByCreatedAtDesc(Integer userId);
}
