package me.elordenador.clonetube.repository;

import me.elordenador.clonetube.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByUsername(String username);
    Optional<User> findByVerifyCode(String verifyCode);
    Optional<User> findByEmail(String email);
    @Query("select u from User u where u.password_reset_token_hash = :token")
    Optional<User> findByPasswordResetTokenHash(@Param("token") String token);
    List<User> findAllByUsernameIn(Collection<String> usernames);
}
