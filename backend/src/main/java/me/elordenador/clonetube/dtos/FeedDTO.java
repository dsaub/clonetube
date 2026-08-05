package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedDTO {
    private List<FeedVideoItemDTO> videos;
    private Integer following_count;
    private Boolean personalized;
}
