package me.elordenador.clonetube.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserLikesVideoId implements Serializable {
    private Integer video;
    private Integer user;
}
