package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudioVideoItemDTO {
    private String key;
    private Integer size;
    private String last_modified;
    private String original_filename;
    private Integer id;
    private String title;
    private String description;
    private String visibility;
    private List<String> allowed_users;
}
