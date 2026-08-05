package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelDTO {
    private Integer id;
    private String username;
    private String full_name;
    private Boolean following;
    private Integer followers;
    private Integer following_count;
    private Integer video_count;
}
