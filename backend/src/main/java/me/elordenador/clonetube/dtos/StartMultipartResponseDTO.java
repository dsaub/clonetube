package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StartMultipartResponseDTO {
    private String uploadId;
    private String key;
    private String original_filename;
}
