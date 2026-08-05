package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutSoiree;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "soirees_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoireeEvent {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private Phase phase;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(name = "date_heure", nullable = false)
    private Instant dateHeure;

    @Column(length = 150)
    private String lieu;

    @Column(length = 255)
    private String adresse;

    @Column(name = "capacite_max", nullable = false)
    @Builder.Default
    private Integer capaciteMax = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutSoiree statut = StatutSoiree.PLANIFIEE;

    @Column(name = "vote_sur_place_actif", nullable = false)
    @Builder.Default
    private boolean voteSurPlaceActif = false;
}
