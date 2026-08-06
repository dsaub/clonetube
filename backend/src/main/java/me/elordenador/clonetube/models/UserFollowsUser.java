package me.elordenador.clonetube.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@IdClass(UserFollowsUserId.class)
@Table(name = "user_follows_user")
public class UserFollowsUser {
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name="follower_id",
            nullable = false
    )
    private User follower;
    @Id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name="followed_id",
            nullable = false
    )
    private User followed;

    private Date created_at;
}
