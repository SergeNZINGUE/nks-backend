package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.TypeVote;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Vote exprimé pour un candidat, toutes sources confondues.
 * sourceExterneId : identifiant du like/commentaire côté API Facebook/TikTok — permet la
 * déduplication puisque les votes sociaux sont désormais ingérés automatiquement via API
 * (décision client, remplace la saisie manuelle prévue initialement dans le rapport §H3).
 */
@Entity
@Table(name = "votes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "type_vote", nullable = false, length = 25)
    private TypeVote typeVote;

    @Column(name = "nombre_voix", nullable = false)
    @Builder.Default
    private Integer nombreVoix = 1;

    @Column(name = "points_calcules")
    private BigDecimal pointsCalcules;

    @Column(name = "source_telephone", length = 20)
    private String sourceTelephone;

    @Column(name = "date_vote", nullable = false)
    @Builder.Default
    private Instant dateVote = Instant.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id_saisie")
    private Utilisateur adminSaisie;

    @Column(name = "source_externe_id", length = 150)
    private String sourceExterneId;
}
