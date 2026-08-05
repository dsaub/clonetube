package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteMultipartRequestDTO {
    private String filename;
    private String uploadId;
    private List<PartInfoDTO> parts;
}
