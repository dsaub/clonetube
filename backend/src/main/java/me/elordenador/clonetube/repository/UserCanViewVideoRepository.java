package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.UserCanViewVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserCanViewVideoRepository extends JpaRepository<UserCanViewVideo, Long> {

    boolean existsByUserIdAndVideoId(Integer userId, Integer videoId);

    @Query("select v.user.username from UserCanViewVideo v where v.video.id = :videoId")
    List<String> findUsernamesByVideoId(@Param("videoId") Integer videoId);

    @Modifying
    @Query("delete from UserCanViewVideo v where v.video.id = :videoId")
    void deleteByVideoId(@Param("videoId") Integer videoId);
}
