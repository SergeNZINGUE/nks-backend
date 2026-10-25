package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutSoiree;
import bf.laterrasse.nks.domain.enums.Enums.TypeSoiree;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = true)
    private Phase phase;

    @Enumerated(EnumType.STRING)
    @Column(name = "type_soiree", nullable = false, length = 10)
    @Builder.Default
    private TypeSoiree typeSoiree = TypeSoiree.NKS;

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

    @Column(name = "resultats_publies", nullable = false)
    @Builder.Default
    private boolean resultatsPublies = false;

    /** Seuil de consommations supplémentaires pour débloquer un vote bonus. Null ou <= 0 =
     * fonctionnalité désactivée pour cette soirée — cf. VoteSurPlaceService#ajouterConsommationBonus. */
    @Column(name = "nb_consommations_pour_vote_bonus")
    private Short nbConsommationsPourVoteBonus;

    /** Plafond de votes bonus par billet pour cette soirée. Null = pas de plafond. */
    @Column(name = "plafond_votes_bonus")
    private Short plafondVotesBonus;

    @Column(name = "votes_arretes_le")
    private Instant votesArretesLe;

    @Column(name = "deliberation_verrouillee", nullable = false)
    @Builder.Default
    private boolean deliberationVerrouilee = false;

    @Column(name = "deliberation_verrouillee_le")
    private Instant deliberationVerrouilleeDate;

    /** URL Cloudinary de l'image de couverture affichée dans le carousel homepage. */
    @Column(name = "image_url", length = 500)
    private String imageUrl;
}
