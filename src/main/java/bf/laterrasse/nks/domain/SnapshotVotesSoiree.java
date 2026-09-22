package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "snapshots_votes_soiree",
        uniqueConstraints = @UniqueConstraint(columnNames = {"soiree_id", "candidat_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SnapshotVotesSoiree {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @Column(name = "voix_payantes", nullable = false)
    @Builder.Default
    private long voixPayantes = 0L;

    @Column(name = "voix_sociales_likes", nullable = false)
    @Builder.Default
    private long voixSocialesLikes = 0L;

    @Column(name = "voix_sociales_commentaires", nullable = false)
    @Builder.Default
    private long voixSocialesCommentaires = 0L;

    @Column(name = "voix_sur_place", nullable = false)
    @Builder.Default
    private long voixSurPlace = 0L;

    @Column(name = "total_voix_payantes_phase", nullable = false)
    @Builder.Default
    private long totalVoixPayantesPhase = 0L;

    @Column(name = "total_voix_sur_place_soiree", nullable = false)
    @Builder.Default
    private long totalVoixSurPlaceSoiree = 0L;

    @Column(name = "date_snapshot", nullable = false)
    @Builder.Default
    private Instant dateSnapshot = Instant.now();
}
