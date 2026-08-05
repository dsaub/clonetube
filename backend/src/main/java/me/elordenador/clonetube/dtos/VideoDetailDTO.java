package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoDetailDTO {
    private Integer id;
    private String key;
    private String title;
    private String description;
    private String visibility;
    private Integer author_id;
    private String author_username;
    private String author_name;
}
