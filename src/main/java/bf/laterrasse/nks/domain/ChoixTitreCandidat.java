package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Choix d'un candidat pour sa soirée : un titre parmi la liste imposée par le CO (via la
 * phase de cette soirée) + un titre personnel libre. Un seul choix par (candidat, soirée) —
 * contrainte UNIQUE en base — un candidat ne se produisant qu'une fois par phase (RM-41).
 */
@Entity
@Table(name = "choix_titres_candidats")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChoixTitreCandidat {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "titre_impose_id", nullable = false)
    private TitreImpose titreImpose;

    @Column(name = "titre_personnel", nullable = false, length = 255)
    private String titrePersonnel;

    @Column(name = "date_choix", nullable = false)
    @Builder.Default
    private Instant dateChoix = Instant.now();
}
