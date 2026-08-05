package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "affectations_poules", uniqueConstraints = @UniqueConstraint(columnNames = {"candidat_id", "poule_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffectationPoule {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "poule_id", nullable = false)
    private Poule poule;

    @Column(name = "ordre_passage")
    private Short ordrePassage;

    @Column(name = "chanson_imposee", length = 255)
    private String chansonImposee;
}
