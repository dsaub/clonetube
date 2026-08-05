package me.elordenador.clonetube.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompleteMultipartResponseDTO {
    private String status;
    private String location;
    private String key;
    private String original_filename;
}
