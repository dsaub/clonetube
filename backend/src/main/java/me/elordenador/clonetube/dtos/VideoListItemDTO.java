package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoListItemDTO {
    private String key;
    private Long size;
    private String last_modified;
    private String original_filename;
}
