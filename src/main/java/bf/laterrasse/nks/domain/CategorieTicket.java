package bf.laterrasse.nks.domain;

import bf.laterrasse.nks.domain.enums.Enums.NomCategorieTicket;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories_tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorieTicket {

    @Id
    @org.hibernate.annotations.UuidGenerator
    @Column(columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "soiree_id", nullable = false)
    private SoireeEvent soiree;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NomCategorieTicket nom;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal prix = BigDecimal.ZERO;

    @Column(name = "nb_places_disponibles", nullable = false)
    @Builder.Default
    private Integer nbPlacesDisponibles = 0;

    @Column(name = "nb_places_reservees", nullable = false)
    @Builder.Default
    private Integer nbPlacesReservees = 0;

    @Column(name = "date_ouverture")
    private Instant dateOuverture;

    @Column(name = "date_fermeture_reservations")
    private Instant dateFermetureReservations;

    @Column(nullable = false)
    @Builder.Default
    private boolean actif = true;
}
