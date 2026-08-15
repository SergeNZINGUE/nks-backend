package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.NomPhase;
import bf.laterrasse.nks.domain.enums.Enums.StatutPhase;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Étape de la compétition (présélection, éliminatoires, demi-finale, finale).
 * Pondérations décidées avec le client : Éliminatoires 25/25/50, Demi-finale 25/20/55,
 * Finale 25/15/60 avec jury obligatoire (jurObligatoire = true, H7 confirmée).
 */
@Entity
@Table(name = "phases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Phase {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NomPhase nom;

    @Column(name = "type_phase", nullable = false, length = 30)
    private String typePhase;

    @Column(nullable = false)
    private Short ordre;

    @Column(name = "date_debut")
    private Instant dateDebut;

    @Column(name = "date_fin")
    private Instant dateFin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatutPhase statut = StatutPhase.EN_ATTENTE;

    @Column(name = "poids_votes_en_ligne", nullable = false)
    private Short poidsVotesEnLigne;

    @Column(name = "poids_public_sur_place", nullable = false)
    private Short poidsPublicSurPlace;

    @Column(name = "poids_jury", nullable = false)
    private Short poidsJury;

    @Column(name = "points_max_votes_en_ligne", nullable = false)
    @Builder.Default
    private BigDecimal pointsMaxVotesEnLigne = BigDecimal.valueOf(100);

    @Column(name = "points_max_public", nullable = false)
    @Builder.Default
    private BigDecimal pointsMaxPublic = BigDecimal.valueOf(100);

    @Column(name = "points_max_jury", nullable = false)
    @Builder.Default
    private BigDecimal pointsMaxJury = BigDecimal.valueOf(100);

    @Column(name = "jury_obligatoire", nullable = false)
    @Builder.Default
    private boolean juryObligatoire = true;

    @Column(name = "vote_actif", nullable = false)
    @Builder.Default
    private boolean voteActif = false;

    @Column(name = "date_ouverture_vote")
    private Instant dateOuvertureVote;

    @Column(name = "date_fermeture_vote")
    private Instant dateFermetureVote;

    @PrePersist
    private void avantInsert() {
        if (statut == null) statut = StatutPhase.EN_ATTENTE;
        if (pointsMaxVotesEnLigne == null) pointsMaxVotesEnLigne = BigDecimal.valueOf(100);
        if (pointsMaxPublic == null) pointsMaxPublic = BigDecimal.valueOf(100);
        if (pointsMaxJury == null) pointsMaxJury = BigDecimal.valueOf(100);
    }
}
