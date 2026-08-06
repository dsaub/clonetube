package me.elordenador.clonetube.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserFollowsUserId implements Serializable {
    private Integer follower;
    private Integer followed;
}
