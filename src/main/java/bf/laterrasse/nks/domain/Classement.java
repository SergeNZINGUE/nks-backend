package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classements", uniqueConstraints = @UniqueConstraint(columnNames = {"candidat_id", "edition_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Classement {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(name = "total_points_cumules", nullable = false)
    @Builder.Default
    private BigDecimal totalPointsCumules = BigDecimal.ZERO;

    @Column(name = "rang_global")
    private Integer rangGlobal;

    @Column(name = "date_derniere_mise_a_jour", nullable = false)
    @Builder.Default
    private Instant dateDerniereMiseAJour = Instant.now();

    @Column(nullable = false)
    @Builder.Default
    private boolean officiel = false;
}
