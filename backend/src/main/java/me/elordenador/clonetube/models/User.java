package me.elordenador.clonetube.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "user")
public class User {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Integer id;

    private String username;
    private String password_hash;
    private Integer password_version;
    private String full_name;
    private String email;
    private String password_reset_token_hash, password_reset_expires_at;
    @Column(name = "verify_code")
    private String verifyCode;
    private Integer points;
    private Boolean is_admin;
}
