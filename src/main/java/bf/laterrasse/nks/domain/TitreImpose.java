package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Titre imposé par le comité d'organisation, publié par phase (valable pour toutes les soirées de cette phase). */
@Entity
@Table(name = "titres_imposes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TitreImpose {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "phase_id", nullable = false)
    private Phase phase;

    @Column(nullable = false, length = 255)
    private String titre;

    @Column(nullable = false)
    @Builder.Default
    private Short ordre = 0;
}
