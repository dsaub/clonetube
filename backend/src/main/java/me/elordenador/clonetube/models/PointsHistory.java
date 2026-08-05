package me.elordenador.clonetube.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
@Entity
@Table(name = "points_history")
public class PointsHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer transaction_id;
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;
    private Integer modifier;
    private String description;
}
