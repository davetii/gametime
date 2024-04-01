package software.daveturner.gametime.entity;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "gm")
@Data
public class GMEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @JsonBackReference
    @OneToOne(cascade = CascadeType.ALL, mappedBy = "gm")
    private TeamEntity team;

}
