package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.PointsHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PointsHistoryRepository extends JpaRepository<PointsHistory, Integer> {
    @Query("select p from PointsHistory p where p.user.id = :userId order by p.created_at desc")
    List<PointsHistory> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);
}
