package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.StatutQualification;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resultats_phase", uniqueConstraints = @UniqueConstraint(columnNames = {"candidat_id", "phase_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResultatPhase {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private Phase phase;

    @Column(name = "points_votes_en_ligne", nullable = false)
    @Builder.Default
    private BigDecimal pointsVotesEnLigne = BigDecimal.ZERO;

    @Column(name = "points_public_sur_place", nullable = false)
    @Builder.Default
    private BigDecimal pointsPublicSurPlace = BigDecimal.ZERO;

    @Column(name = "points_jury", nullable = false)
    @Builder.Default
    private BigDecimal pointsJury = BigDecimal.ZERO;

    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private BigDecimal totalPoints = BigDecimal.ZERO;

    private Integer rang;

    @Enumerated(EnumType.STRING)
    @Column(name = "statut_qualification", nullable = false, length = 20)
    @Builder.Default
    private StatutQualification statutQualification = StatutQualification.EN_ATTENTE;

    @Column(name = "date_calcul", nullable = false)
    @Builder.Default
    private Instant dateCalcul = Instant.now();

    @Column(name = "motif_repechage", columnDefinition = "text")
    private String motifRepechage;
}
