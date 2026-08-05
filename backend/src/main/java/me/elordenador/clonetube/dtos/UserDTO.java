package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    protected Integer id;
    protected String username;
    protected String full_name;
    protected String email;
    protected Boolean admin;
}
