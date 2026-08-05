package me.elordenador.clonetube.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "multipart_upload")
public class MultipartUpload {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String upload_id;
    private String upload_key;
    private String original_filename;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name="owner_id",
            nullable = false
    )
    private User owner;
    private String status;
    private Date created_at;
}
