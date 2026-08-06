package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.models.UserFollowsUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFollowsUserRepository extends JpaRepository<UserFollowsUser, Long> {

    Optional<UserFollowsUser> findByFollowerIdAndFollowedId(Integer followerId, Integer followedId);

    boolean existsByFollowerIdAndFollowedId(Integer followerId, Integer followedId);

    long countByFollowedId(Integer followedId);

    long countByFollowerId(Integer followerId);

    @Query("select uf.followed from UserFollowsUser uf where uf.follower.id = :followerId order by uf.followed.username")
    List<User> findFollowedByFollowerId(@Param("followerId") Integer followerId);
}
