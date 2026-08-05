package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelVideoItemDTO {
    private Integer id;
    private String key;
    private String title;
    private String description;
    private String visibility;
    private String created_at;
    private Integer likes;
}
