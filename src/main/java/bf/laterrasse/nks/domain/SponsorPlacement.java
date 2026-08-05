package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sponsors_placements", uniqueConstraints = @UniqueConstraint(columnNames = {"partenaire_id", "edition_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SponsorPlacement {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partenaire_id", nullable = false)
    private Partenaire partenaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(name = "niveau_affichage", length = 30)
    private String niveauAffichage;

    @Column(name = "ordre_affichage", nullable = false)
    @Builder.Default
    private Short ordreAffichage = 0;

    @Column(name = "contrat_reference", length = 100)
    private String contratReference;

    @Column(name = "date_debut")
    private LocalDate dateDebut;

    @Column(name = "date_fin")
    private LocalDate dateFin;
}
