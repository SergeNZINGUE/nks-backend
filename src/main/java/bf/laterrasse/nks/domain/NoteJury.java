package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notes_jury", uniqueConstraints = @UniqueConstraint(columnNames = {"jury_id", "candidat_id", "soiree_id", "critere_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NoteJury {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jury_id", nullable = false)
    private Jury jury;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat_id", nullable = false)
    private Candidat candidat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "critere_id", nullable = false)
    private CritereNotation critere;

    @Column(nullable = false)
    private BigDecimal valeur;

    @Column(nullable = false)
    @Builder.Default
    private boolean verrouille = false;

    @Column(name = "date_saisie", nullable = false)
    @Builder.Default
    private Instant dateSaisie = Instant.now();

    @Column(name = "date_modification")
    private Instant dateModification;
}
