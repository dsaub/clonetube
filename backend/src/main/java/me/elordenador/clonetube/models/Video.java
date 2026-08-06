package me.elordenador.clonetube.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.enums.VisibilityEnum;

import java.util.Date;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "video")
public class Video {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Integer id;
    private String filename;
    @ManyToOne(fetch= FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "author",
            nullable = false
    )
    private User author;
    private String video_name;
    private String video_desc;
    private Date created_at;
    private Boolean is_published;
    @Enumerated(EnumType.STRING)
    private VisibilityEnum visibility;
}
