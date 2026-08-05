package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoUpdateDTO {
    private String title;
    private String description;
    private String visibility;
    private List<String> allowed_users;
}
