package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoCatalogItemDTO {
    private Integer id;
    private String filename;
    private String title;
    private String description;
    private Integer author_id;
    private String author_username;
    private String author_name;
}
