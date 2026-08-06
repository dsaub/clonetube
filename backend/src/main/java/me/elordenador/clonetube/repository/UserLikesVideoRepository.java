package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.UserLikesVideo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserLikesVideoRepository extends JpaRepository<UserLikesVideo, Long> {

    @Query("select ulv.video.id, count(ulv) from UserLikesVideo ulv group by ulv.video.id")
    List<Object[]> countAllGroupByVideo();

    @Query("select ulv.video.id, count(ulv) from UserLikesVideo ulv where ulv.video.id in :ids group by ulv.video.id")
    List<Object[]> countByVideoIds(@Param("ids") List<Integer> ids);

    @Modifying
    @Query("delete from UserLikesVideo ulv where ulv.video.id = :videoId")
    void deleteByVideoId(@Param("videoId") Integer videoId);
}
