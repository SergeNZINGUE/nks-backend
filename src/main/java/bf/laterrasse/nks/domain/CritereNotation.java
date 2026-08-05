package bf.laterrasse.nks.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "criteres_notation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CritereNotation {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "edition_id", nullable = false)
    private Edition edition;

    @Column(nullable = false, length = 150)
    private String nom;

    @Column(name = "note_min", nullable = false)
    @Builder.Default
    private BigDecimal noteMin = BigDecimal.ZERO;

    @Column(name = "note_max", nullable = false)
    private BigDecimal noteMax;

    @Column(nullable = false)
    private Short ordre;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;
}
