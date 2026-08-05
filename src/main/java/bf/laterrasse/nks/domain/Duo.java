package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "duos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Duo {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private Phase phase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id")
    private SoireeEvent soiree;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat1_id", nullable = false)
    private Candidat candidat1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidat2_id", nullable = false)
    private Candidat candidat2;

    @Column(name = "chanson_commune", length = 255)
    private String chansonCommune;

    @Column(name = "ordre_passage")
    private Short ordrePassage;
}
