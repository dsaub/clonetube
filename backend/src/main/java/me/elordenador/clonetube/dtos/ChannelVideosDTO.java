package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelVideosDTO {
    private List<ChannelVideoItemDTO> videos;
    private Integer page;
    private Integer page_size;
    private Integer total;
    private Integer pages;
}
